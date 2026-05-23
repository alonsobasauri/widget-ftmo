package com.basauri.ftmowidget.data

import java.text.NumberFormat
import java.util.Locale

object Format {

    private val twoDecimals = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun money(money: Money?, withSign: Boolean = false): String {
        if (money == null) return "—"
        val amount = money.amount
        val symbol = currencySymbol(money.currency)
        return formatAmount(amount, symbol, withSign)
    }

    fun money(value: Double?, currency: String?, withSign: Boolean = false): String {
        if (value == null) return "—"
        return formatAmount(value, currencySymbol(currency), withSign)
    }

    fun percent(score: Score?, fractionDigits: Int = 2): String {
        if (score == null) return "—"
        val pct = if (score.type == "fraction") score.value * 100.0 else score.value
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        return "${nf.format(pct)}%"
    }

    fun ratio(value: Double, fractionDigits: Int = 2): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        return nf.format(value)
    }

    fun shortDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        // FTMO returns ISO yyyy-MM-dd; show "22 May" style without pulling a calendar API.
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
