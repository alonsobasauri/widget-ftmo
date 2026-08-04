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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.accountDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore("ftmo_widget")

class AccountStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val TOKEN = stringPreferencesKey("account_token")
        // Superseded by PROVIDER/TOKEN; still read so an upgraded install keeps
        // its configured FTMO account instead of falling back to Unconfigured.
        val LEGACY_LOGIN = longPreferencesKey("login")
        val LEGACY_SHARING_CODE = stringPreferencesKey("sharing_code")
        val LAST_SNAPSHOT = stringPreferencesKey("last_snapshot_json")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_FETCH_AT = longPreferencesKey("last_fetch_at")
        val REFRESHING = booleanPreferencesKey("refreshing")
        val BACKGROUND_ALPHA = floatPreferencesKey("background_alpha")
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_min")
    }

    val identity: Flow<Identity?> = context.accountDataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]
        if (token != null) {
            val provider = prefs[Keys.PROVIDER]
                ?.let { name -> ProviderId.entries.firstOrNull { it.name == name } }
                ?: ProviderId.FTMO
            return@map Identity(provider, token)
        }
        // Pre-multi-provider install: rebuild the FTMO identity from the old keys.
        val login = prefs[Keys.LEGACY_LOGIN] ?: return@map null
        val code = prefs[Keys.LEGACY_SHARING_CODE] ?: return@map null
        Identity(ProviderId.FTMO, "$login:$code")
    }

    suspend fun currentIdentity(): Identity? = identity.first()

    suspend fun saveIdentity(identity: Identity) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.PROVIDER] = identity.provider.name
            prefs[Keys.TOKEN] = identity.token
            prefs.remove(Keys.LEGACY_LOGIN)
            prefs.remove(Keys.LEGACY_SHARING_CODE)
            prefs.remove(Keys.LAST_ERROR)
            // The cached snapshot belongs to the previous account; drop it so a
            // stale figure from another firm can't flash before the first fetch.
            prefs.remove(Keys.LAST_SNAPSHOT)
        }
    }

    suspend fun clear() {
        context.accountDataStore.edit { it.clear() }
    }

    suspend fun saveSnapshot(snapshot: AccountSnapshot) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.LAST_SNAPSHOT] = json.encodeToString(snapshot)
            prefs[Keys.LAST_FETCH_AT] = snapshot.fetchedAtMillis
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun saveError(message: String) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.LAST_ERROR] = message
        }
    }

    /**
     * Snapshots cached by an older build use a different shape and simply fail
     * to decode, which reads as "no cache yet" and triggers a fresh fetch.
     */
    suspend fun currentSnapshot(): AccountSnapshot? {
        val prefs = context.accountDataStore.data.first()
        val raw = prefs[Keys.LAST_SNAPSHOT] ?: return null
        return runCatching { json.decodeFromString<AccountSnapshot>(raw) }.getOrNull()
    }

    suspend fun currentError(): String? =
        context.accountDataStore.data.first()[Keys.LAST_ERROR]

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
