package com.basauri.ftmowidget.data

import android.content.Context

class FtmoRepository(context: Context) {
    private val store = AccountStore(context.applicationContext)
    private val client = FtmoClient()

    suspend fun currentIdentity(): ShareIdentity? = store.currentIdentity()
    suspend fun setIdentity(id: ShareIdentity) = store.saveIdentity(id)
    suspend fun clear() = store.clear()

    suspend fun cachedSnapshot(): WidgetSnapshot? = store.currentSnapshot()
    suspend fun cachedError(): String? = store.currentError()
    suspend fun cachedRefreshing(): Boolean = store.currentRefreshing()
    suspend fun setRefreshing(value: Boolean) = store.setRefreshing(value)

    /** Fetches and persists. Throws on failure (after persisting the error message). */
    suspend fun refresh(): WidgetSnapshot {
        val id = store.currentIdentity()
            ?: throw IllegalStateException("Widget not configured")
        return try {
            val metrix = client.fetchMetrix(id.login, id.sharingCode)
            val snapshot = WidgetSnapshot(
                login = id.login,
                sharingCode = id.sharingCode,
                fetchedAtMillis = System.currentTimeMillis(),
                metrix = metrix,
            )
            store.saveSnapshot(snapshot)
            snapshot
        } catch (t: Throwable) {
            store.saveError(t.message ?: t::class.java.simpleName)
            throw t
        }
    }
}
