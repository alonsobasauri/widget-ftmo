package com.basauri.ftmowidget.data

import kotlinx.serialization.Serializable

/**
 * One configured account. [id] is derived from the identity rather than
 * generated, so adding the same share link twice is a no-op and the per-account
 * cache keys stay stable across restarts.
 */
@Serializable
data class AccountRef(
    val identity: Identity,
    /** Last known account label, so the config list reads well before the first fetch. */
    val label: String? = null,
) {
    val id: String get() = "${identity.provider.name}#${identity.token}"

    /** What to call this account in a list. */
    fun displayName(): String =
        label ?: Providers.of(identity.provider).displayName
}

/**
 * Which account a placed widget shows. Bindings are per `appWidgetId`, so two
 * widgets on the same home screen can track different accounts — or one can be
 * set to [ALL] and stack them.
 */
object WidgetBinding {
    /** Sentinel binding: show every configured account, stacked. */
    const val ALL = "*"
}

/** A configured account paired with whatever the last refresh produced for it. */
data class AccountView(
    val ref: AccountRef,
    val snapshot: AccountSnapshot? = null,
    val error: String? = null,
)
