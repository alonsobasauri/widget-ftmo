package com.basauri.ftmowidget.data

import android.content.Context
import kotlinx.coroutines.delay
import java.io.IOException

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
                val snapshot = fetchWithRetry(ref)
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

    /**
     * Fetches with a short backoff on transport failures.
     *
     * A tap on the refresh control very often lands while the radio is still
     * settling — coming out of Doze, or handing over between Wi-Fi and mobile —
     * and DNS then fails with `UnknownHostException` even though the device is
     * online moments later. OkHttp's own `retryOnConnectionFailure` does not
     * cover DNS, so without this a single unlucky lookup marked the account
     * stale and the user saw "Sin conexión" on a working connection.
     *
     * Only [IOException] is retried: a decode failure or a bad share token will
     * fail identically every time, and retrying it just delays the error.
     */
    private suspend fun fetchWithRetry(ref: AccountRef): AccountSnapshot {
        val provider = Providers.of(ref.identity.provider)
        var last: IOException? = null
        for (attempt in RETRY_DELAYS_MS.indices) {
            try {
                return provider.fetch(ref.identity)
            } catch (e: IOException) {
                last = e
                RETRY_DELAYS_MS[attempt].takeIf { it > 0 }?.let { delay(it) }
            }
        }
        throw last ?: IOException("Network failure")
    }

    private companion object {
        /** Delay *after* each attempt; the trailing 0 means the last one doesn't wait. */
        val RETRY_DELAYS_MS = longArrayOf(700, 1_800, 0)
    }
}
