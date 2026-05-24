package com.basauri.ftmowidget.update

import com.basauri.ftmowidget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val owner: String = "alonsobasauri",
    private val repo: String = "widget-ftmo",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun checkLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "FtmoWidget/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(200)}")
            }
            val release = json.decodeFromString<GitHubRelease>(body)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("Release ${release.tagName} has no APK attached")

            val currentRaw = BuildConfig.VERSION_NAME
            UpdateInfo(
                currentVersion = currentRaw,
                latestVersion = stripV(release.tagName),
                latestTag = release.tagName,
                isNewer = compareVersions(stripV(currentRaw.removeSuffix("-debug")), stripV(release.tagName)) < 0,
                apkUrl = apk.downloadUrl,
                apkName = apk.name,
                sizeBytes = apk.size,
                releaseNotes = release.body.orEmpty(),
                htmlUrl = release.htmlUrl,
            )
        }
    }

    private fun stripV(tag: String) = tag.removePrefix("v").removePrefix("V")

    /**
     * Returns negative if [a] < [b], positive if [a] > [b], zero if equal.
     * Compares numeric segments split by '.' and '-'. Non-numeric segments are treated as 0,
     * which is good enough for vX.Y.Z and vX.Y.Z-rcN style tags.
     */
    internal fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i) ?: 0
            val y = pb.getOrNull(i) ?: 0
            if (x != y) return x - y
        }
        return 0
    }
}
