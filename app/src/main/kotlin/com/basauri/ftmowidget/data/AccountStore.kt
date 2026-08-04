package com.basauri.ftmowidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.accountDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore("ftmo_widget")

/**
 * Persists the configured accounts, each account's last snapshot, and which
 * account every placed widget is bound to.
 *
 * Snapshots and errors live under per-account keys rather than in one blob so a
 * refresh can update a single account without rewriting the others — one firm
 * being down must not disturb the rest.
 */
class AccountStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private object Keys {
        val ACCOUNTS = stringPreferencesKey("accounts_json")
        // Single-account keys, superseded by ACCOUNTS. Still read once so an
        // upgraded install keeps its configured account instead of resetting.
        val LEGACY_PROVIDER = stringPreferencesKey("provider")
        val LEGACY_TOKEN = stringPreferencesKey("account_token")
        val LEGACY_LOGIN = longPreferencesKey("login")
        val LEGACY_SHARING_CODE = stringPreferencesKey("sharing_code")
        val LEGACY_SNAPSHOT = stringPreferencesKey("last_snapshot_json")

        val REFRESHING = booleanPreferencesKey("refreshing")
        val BACKGROUND_ALPHA = floatPreferencesKey("background_alpha")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_min")

        fun snapshot(accountId: String) = stringPreferencesKey("snapshot_$accountId")
        fun error(accountId: String) = stringPreferencesKey("error_$accountId")
        fun binding(appWidgetId: Int) = stringPreferencesKey("bind_$appWidgetId")
    }

    // ---- accounts -------------------------------------------------------

    /**
     * Configured accounts in display order. Performs the one-time migration off
     * the single-account keys on first read, carrying the cached snapshot over
     * so an upgrade doesn't blank the widget until the next refresh.
     */
    suspend fun accounts(): List<AccountRef> {
        val prefs = context.accountDataStore.data.first()
        prefs[Keys.ACCOUNTS]?.let { raw ->
            return runCatching { json.decodeFromString(ListSerializer(AccountRef.serializer()), raw) }
                .getOrDefault(emptyList())
        }

        val legacy = legacyIdentity(prefs) ?: return emptyList()
        val ref = AccountRef(legacy)
        val legacySnapshot = prefs[Keys.LEGACY_SNAPSHOT]
        context.accountDataStore.edit { edit ->
            edit[Keys.ACCOUNTS] = json.encodeToString(listOf(ref))
            if (legacySnapshot != null) edit[Keys.snapshot(ref.id)] = legacySnapshot
            edit.remove(Keys.LEGACY_PROVIDER)
            edit.remove(Keys.LEGACY_TOKEN)
            edit.remove(Keys.LEGACY_LOGIN)
            edit.remove(Keys.LEGACY_SHARING_CODE)
            edit.remove(Keys.LEGACY_SNAPSHOT)
        }
        return listOf(ref)
    }

    private fun legacyIdentity(prefs: Preferences): Identity? {
        prefs[Keys.LEGACY_TOKEN]?.let { token ->
            val provider = prefs[Keys.LEGACY_PROVIDER]
                ?.let { name -> ProviderId.entries.firstOrNull { it.name == name } }
                ?: ProviderId.FTMO
            return Identity(provider, token)
        }
        // Pre-multi-provider install: rebuild the FTMO identity from the old keys.
        val login = prefs[Keys.LEGACY_LOGIN] ?: return null
        val code = prefs[Keys.LEGACY_SHARING_CODE] ?: return null
        return Identity(ProviderId.FTMO, "$login:$code")
    }

    /** Adds, or updates the label of an account that is already configured. */
    suspend fun addAccount(identity: Identity, label: String? = null): AccountRef {
        val ref = AccountRef(identity, label)
        val current = accounts()
        val next = if (current.any { it.id == ref.id }) {
            current.map { if (it.id == ref.id) it.copy(label = label ?: it.label) else it }
        } else {
            current + ref
        }
        context.accountDataStore.edit { prefs ->
            prefs[Keys.ACCOUNTS] = json.encodeToString(next)
            prefs.remove(Keys.error(ref.id))
        }
        return ref
    }

    suspend fun removeAccount(accountId: String) {
        val next = accounts().filterNot { it.id == accountId }
        context.accountDataStore.edit { prefs ->
            prefs[Keys.ACCOUNTS] = json.encodeToString(next)
            prefs.remove(Keys.snapshot(accountId))
            prefs.remove(Keys.error(accountId))
            // Widgets pointed at the removed account fall back to the default.
            // Collect first: removing while iterating the live map would throw.
            prefs.asMap().keys
                .map { it.name }
                .filter { it.startsWith("bind_") }
                .map { stringPreferencesKey(it) }
                .filter { prefs[it] == accountId }
                .toList()
                .forEach { prefs.remove(it) }
        }
    }

    suspend fun clear() {
        context.accountDataStore.edit { it.clear() }
    }

    // ---- per-account cache ----------------------------------------------

    suspend fun saveSnapshot(accountId: String, snapshot: AccountSnapshot) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.snapshot(accountId)] = json.encodeToString(snapshot)
            prefs.remove(Keys.error(accountId))
        }
    }

    suspend fun saveError(accountId: String, message: String) {
        context.accountDataStore.edit { prefs -> prefs[Keys.error(accountId)] = message }
    }

    /**
     * Snapshots cached by an older build use a different shape and simply fail
     * to decode, which reads as "no cache yet" and triggers a fresh fetch.
     */
    suspend fun snapshot(accountId: String): AccountSnapshot? {
        val raw = context.accountDataStore.data.first()[Keys.snapshot(accountId)] ?: return null
        return runCatching { json.decodeFromString<AccountSnapshot>(raw) }.getOrNull()
    }

    suspend fun error(accountId: String): String? =
        context.accountDataStore.data.first()[Keys.error(accountId)]

    /** Snapshots and errors for every account, in one read. */
    suspend fun views(refs: List<AccountRef>): List<AccountView> {
        val prefs = context.accountDataStore.data.first()
        return refs.map { ref ->
            val raw = prefs[Keys.snapshot(ref.id)]
            AccountView(
                ref = ref,
                snapshot = raw?.let { runCatching { json.decodeFromString<AccountSnapshot>(it) }.getOrNull() },
                error = prefs[Keys.error(ref.id)],
            )
        }
    }

    // ---- per-widget binding ---------------------------------------------

    /** Account id this widget shows, [WidgetBinding.ALL], or null when unset. */
    suspend fun binding(appWidgetId: Int): String? =
        context.accountDataStore.data.first()[Keys.binding(appWidgetId)]

    suspend fun setBinding(appWidgetId: Int, value: String) {
        context.accountDataStore.edit { it[Keys.binding(appWidgetId)] = value }
    }

    suspend fun clearBinding(appWidgetId: Int) {
        context.accountDataStore.edit { it.remove(Keys.binding(appWidgetId)) }
    }

    // ---- global settings -------------------------------------------------

    suspend fun setRefreshing(value: Boolean) {
        context.accountDataStore.edit { prefs ->
            if (value) prefs[Keys.REFRESHING] = true else prefs.remove(Keys.REFRESHING)
        }
    }

    suspend fun currentRefreshing(): Boolean =
        context.accountDataStore.data.first()[Keys.REFRESHING] == true

    suspend fun setBackgroundAlpha(value: Float) {
        context.accountDataStore.edit { it[Keys.BACKGROUND_ALPHA] = value.coerceIn(0f, 1f) }
    }

    suspend fun currentBackgroundAlpha(): Float =
        context.accountDataStore.data.first()[Keys.BACKGROUND_ALPHA] ?: 1f

    suspend fun setRefreshIntervalMinutes(value: Int) {
        context.accountDataStore.edit { it[Keys.REFRESH_INTERVAL] = value }
    }

    suspend fun currentRefreshIntervalMinutes(): Int =
        context.accountDataStore.data.first()[Keys.REFRESH_INTERVAL] ?: 5
}
