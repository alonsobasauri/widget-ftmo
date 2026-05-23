package com.basauri.ftmowidget.data

/**
 * Parses URLs like:
 *   https://trader.ftmo.com/live-metrix/531303305/share/019e5049-800b-7191-b41c-3e27d7304655?lang=en
 *   trader.ftmo.com/live-metrix/531303305/share/019e5049-800b-7191-b41c-3e27d7304655
 *   531303305/019e5049-800b-7191-b41c-3e27d7304655   (loose paste)
 */
data class ShareIdentity(val login: Long, val sharingCode: String)

object ShareUrlParser {
    private val regex = Regex(
        "(?<login>\\d{4,})\\D+(?<code>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    )

    fun parse(input: String): ShareIdentity? {
        val match = regex.find(input.trim()) ?: return null
        val login = match.groups["login"]?.value?.toLongOrNull() ?: return null
        val code = match.groups["code"]?.value ?: return null
        return ShareIdentity(login = login, sharingCode = code.lowercase())
    }
}
