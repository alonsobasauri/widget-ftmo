package com.basauri.ftmowidget.data

import android.content.Context

class AccountRepository(context: Context) {
    private val store = AccountStore(context.applicationContext)

    suspend fun accounts(): List<AccountRef> = store.accounts()

    /**
     * Registers an account, seeding its cache with [snapshot] when the caller
     * already has one. The config screen fetches to validate the share link, so
     * throwing that result away would leave a brand-new account with nothing to
     * render until the next refresh — and a bare error card if that one fails.
     */
    suspend fun addAccount(
        identity: Identity,
        label: String? = null,
        snapshot: AccountSnapshot? = null,
    ): AccountRef {
        val ref = store.addAccount(identity, label)
        if (snapshot != null) store.saveSnapshot(ref.id, snapshot)
        return ref
    }
    suspend fun removeAccount(accountId: String) = store.removeAccount(accountId)
    suspend fun clear() = store.clear()

    suspend fun views(): List<AccountView> = store.views(store.accounts())

    suspend fun binding(appWidgetId: Int): String? = store.binding(appWidgetId)
    suspend fun setBinding(appWidgetId: Int, value: String) = store.setBinding(appWidgetId, value)
    suspend fun clearBinding(appWidgetId: Int) = store.clearBinding(appWidgetId)

    suspend fun cachedRefreshing(): Boolean = store.currentRefreshing()
    suspend fun setRefreshing(value: Boolean) = store.setRefreshing(value)
    suspend fun backgroundAlpha(): Float = store.currentBackgroundAlpha()
    suspend fun setBackgroundAlpha(value: Float) = store.setBackgroundAlpha(value)
    suspend fun refreshIntervalMinutes(): Int = store.currentRefreshIntervalMinutes()
    suspend fun setRefreshIntervalMinutes(value: Int) = store.setRefreshIntervalMinutes(value)

    /**
     * Refreshes every configured account, persisting each result as it lands.
     *
     * Failures are recorded per account and never abort the loop: one firm being
     * unreachable must not stop the others from updating, and a widget bound to
     * a healthy account should not go stale because a sibling failed.
     *
     * Returns the number of accounts that refreshed successfully.
     */
    suspend fun refreshAll(): Int {
        val refs = store.accounts()
        if (refs.isEmpty()) throw IllegalStateException("Widget not configured")
        var ok = 0
        for (ref in refs) {
            try {
                val snapshot = Providers.of(ref.identity.provider).fetch(ref.identity)
                store.saveSnapshot(ref.id, snapshot)
                // Keep the stored label in step with what the firm reports, so
                // the account list doesn't drift after a rename or an upgrade.
                if (ref.label != snapshot.accountLabel) {
                    store.addAccount(ref.identity, snapshot.accountLabel)
                }
                ok++
            } catch (t: Throwable) {
                store.saveError(ref.id, t.message ?: t::class.java.simpleName)
            }
        }
        if (ok == 0) throw IllegalStateException("All ${refs.size} account(s) failed to refresh")
        return ok
    }
}
