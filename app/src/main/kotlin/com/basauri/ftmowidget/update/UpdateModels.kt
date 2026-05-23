package com.basauri.ftmowidget.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
)

/** Result of a successful check. */
data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val latestTag: String,
    val isNewer: Boolean,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val releaseNotes: String,
    val htmlUrl: String,
)
