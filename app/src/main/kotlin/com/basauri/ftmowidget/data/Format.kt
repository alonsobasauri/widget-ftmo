package com.basauri.ftmowidget.data

import java.text.NumberFormat
import java.util.Locale

object Format {

    private val twoDecimals = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    private val wholeNumber = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    fun money(value: Double?, currency: String?, withSign: Boolean = false): String {
        if (value == null) return "—"
        return formatAmount(value, currencySymbol(currency), withSign)
    }

    /** Money without cents, for secondary figures where the decimals are noise. */
    fun moneyWhole(value: Double?, currency: String?): String {
        if (value == null) return "—"
        val abs = wholeNumber.format(kotlin.math.abs(value))
        val sign = if (value < 0) "-" else ""
        return "$sign${currencySymbol(currency)}$abs"
    }

    /** Values arrive already scaled to 0..100; each provider's mapper owns that. */
    fun percent(pct: Double?, fractionDigits: Int = 1): String {
        if (pct == null) return "—"
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        return "${nf.format(pct)}%"
    }

    fun ratio(value: Double?, fractionDigits: Int = 2): String {
        if (value == null) return "—"
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        return nf.format(value)
    }

    /**
     * Friendly stale banner for when a refresh failed but cached data is shown.
     * Classifies network/DNS failures as "Sin conexión" and includes the age of
     * the cached snapshot instead of dumping the raw exception message.
     */
    fun staleNote(
        errorMessage: String?,
        fetchedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val ageMin = ((nowMillis - fetchedAtMillis) / 60000L).coerceAtLeast(0L)
        val age = when {
            ageMin < 1 -> "hace <1 min"
            ageMin < 60 -> "hace $ageMin min"
            else -> "hace ${ageMin / 60} h"
        }
        val prefix = if (isOffline(errorMessage)) "Sin conexión" else "No actualizado"
        return "⚠ $prefix · $age"
    }

    /**
     * Same classification as [staleNote], for the case where there is no cached
     * snapshot to annotate. A raw `UnknownHostException` truncated mid-sentence
     * tells the user nothing they can act on; "Sin conexión" does.
     */
    fun errorNote(errorMessage: String?): String = when {
        isOffline(errorMessage) -> "Sin conexión"
        errorMessage.isNullOrBlank() -> "Error desconocido"
        else -> errorMessage.take(80)
    }

    /**
     * Recognises the transport failures that mean "the device could not reach
     * the network", as opposed to the server answering with something bad.
     * DNS failures are the common one on Android: the widget refreshes on a
     * schedule, so it regularly fires while the radio is still coming back up.
     */
    private fun isOffline(errorMessage: String?): Boolean {
        val m = errorMessage?.lowercase() ?: return false
        return listOf(
            "resolve host", "unable to", "timeout", "timed out",
            "failed to connect", "no address", "unreachable", "network",
        ).any { m.contains(it) }
    }

    fun shortDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        // Providers normalize to ISO yyyy-MM-dd; show "22 May" style without
        // pulling in a calendar API.
        val parts = isoDate.take(10).split('-')
        if (parts.size < 3) return isoDate
        val month = MONTH_ABBR.getOrNull(parts[1].toIntOrNull()?.minus(1) ?: -1) ?: parts[1]
        val day = parts[2].trimStart('0').ifEmpty { "0" }
        return "$day $month"
    }

    private fun formatAmount(amount: Double, symbol: String, withSign: Boolean): String {
        val abs = twoDecimals.format(kotlin.math.abs(amount))
        val sign = when {
            amount < 0 -> "-"
            withSign && amount > 0 -> "+"
            else -> ""
        }
        return "$sign$symbol$abs"
    }

    private fun currencySymbol(code: String?): String = when (code?.uppercase()) {
        null, "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$code "
    }

    private val MONTH_ABBR = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
