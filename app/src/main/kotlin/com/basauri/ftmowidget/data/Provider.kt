package com.basauri.ftmowidget.data

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.io.IOException

/**
 * What the widget needs to reach one account at one prop firm.
 *
 * The credential is a single opaque [token] whose format is the provider's
 * business: Blue Guardian shares a lone MongoDB ObjectId, FTMO needs a login
 * *and* a sharing code and packs both into one string. Keeping it opaque means
 * [AccountStore] never has to grow a column per firm.
 */
@Serializable
data class Identity(val provider: ProviderId, val token: String)

interface Provider {
    val id: ProviderId

    /** Name shown in the config screen when reporting which firm was detected. */
    val displayName: String

    /**
     * Extracts an identity from a pasted share link, or null when the input
     * doesn't look like this provider's. Implementations must be strict enough
     * not to claim another provider's link.
     */
    fun parse(input: String): Identity?

    /** Canonical share URL for [identity], to repopulate the config field. */
    fun shareUrl(identity: Identity): String

    /** Fetches and normalizes. Throws on network or decode failure. */
    suspend fun fetch(identity: Identity): AccountSnapshot
}

/** Delay *after* each attempt; the trailing 0 means the last one doesn't wait. */
internal val RETRY_DELAYS_MS = longArrayOf(700, 1_800, 0)

/**
 * Runs [block], retrying transport failures with a short backoff.
 *
 * Refreshes very often fire while the radio is still settling, and DNS then
 * fails even though the device is reachable a moment later. OkHttp's
 * `retryOnConnectionFailure` does not cover DNS, so this is where that gap is
 * closed. Only [IOException] is caught, which also means a coroutine
 * cancellation propagates instead of being swallowed as a failed attempt.
 */
internal suspend fun <T> retryOnIo(block: suspend () -> T): T {
    var last: IOException? = null
    for (index in RETRY_DELAYS_MS.indices) {
        try {
            return block()
        } catch (e: IOException) {
            last = e
            RETRY_DELAYS_MS[index].takeIf { it > 0 }?.let { delay(it) }
        }
    }
    throw last ?: IOException("Network failure")
}

object Providers {
    val all: List<Provider> = listOf(FtmoProvider, BlueGuardianProvider)

    fun of(id: ProviderId): Provider = all.first { it.id == id }

    /**
     * Detects the provider from a pasted link. FTMO is tried first: its pattern
     * (numeric login + hyphenated UUID) is the more specific of the two, and
     * Blue Guardian's bare 24-hex token would otherwise be the greedier match.
     */
    fun parse(input: String): Identity? = all.firstNotNullOfOrNull { it.parse(input) }
}
