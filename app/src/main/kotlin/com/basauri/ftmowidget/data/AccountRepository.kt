package com.basauri.ftmowidget.data

import android.content.Context

class AccountRepository(context: Context) {
    private val store = AccountStore(context.applicationContext)

    suspend fun currentIdentity(): Identity? = store.currentIdentity()
    suspend fun setIdentity(id: Identity) = store.saveIdentity(id)
    suspend fun clear() = store.clear()

    suspend fun cachedSnapshot(): AccountSnapshot? = store.currentSnapshot()
    suspend fun cachedError(): String? = store.currentError()
    suspend fun cachedRefreshing(): Boolean = store.currentRefreshing()
    suspend fun setRefreshing(value: Boolean) = store.setRefreshing(value)
    suspend fun backgroundAlpha(): Float = store.currentBackgroundAlpha()
    suspend fun setBackgroundAlpha(value: Float) = store.setBackgroundAlpha(value)
    suspend fun refreshIntervalMinutes(): Int = store.currentRefreshIntervalMinutes()
    suspend fun setRefreshIntervalMinutes(value: Int) = store.setRefreshIntervalMinutes(value)

    /** Fetches and persists. Throws on failure (after persisting the error message). */
    suspend fun refresh(): AccountSnapshot {
        val id = store.currentIdentity()
            ?: throw IllegalStateException("Widget not configured")
        return try {
            val snapshot = Providers.of(id.provider).fetch(id)
            store.saveSnapshot(snapshot)
            snapshot
        } catch (t: Throwable) {
            store.saveError(t.message ?: t::class.java.simpleName)
            throw t
        }
    }
}
