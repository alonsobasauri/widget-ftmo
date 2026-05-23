package com.basauri.ftmowidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        val LOGIN = longPreferencesKey("login")
        val SHARING_CODE = stringPreferencesKey("sharing_code")
        val LAST_SNAPSHOT = stringPreferencesKey("last_snapshot_json")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val LAST_FETCH_AT = longPreferencesKey("last_fetch_at")
    }

    val identity: Flow<ShareIdentity?> = context.accountDataStore.data.map { prefs ->
        val login = prefs[Keys.LOGIN] ?: return@map null
        val code = prefs[Keys.SHARING_CODE] ?: return@map null
        ShareIdentity(login, code)
    }

    suspend fun currentIdentity(): ShareIdentity? = identity.first()

    suspend fun saveIdentity(identity: ShareIdentity) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.LOGIN] = identity.login
            prefs[Keys.SHARING_CODE] = identity.sharingCode
            prefs.remove(Keys.LAST_ERROR)
        }
    }

    suspend fun clear() {
        context.accountDataStore.edit { it.clear() }
    }

    suspend fun saveSnapshot(snapshot: WidgetSnapshot) {
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

    suspend fun currentSnapshot(): WidgetSnapshot? {
        val prefs = context.accountDataStore.data.first()
        val raw = prefs[Keys.LAST_SNAPSHOT] ?: return null
        return runCatching { json.decodeFromString<WidgetSnapshot>(raw) }.getOrNull()
    }

    suspend fun currentError(): String? =
        context.accountDataStore.data.first()[Keys.LAST_ERROR]
}
