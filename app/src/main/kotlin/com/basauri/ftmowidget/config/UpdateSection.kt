package com.basauri.ftmowidget.config

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.basauri.ftmowidget.BuildConfig
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.update.DownloadStatus
import com.basauri.ftmowidget.update.UpdateChecker
import com.basauri.ftmowidget.update.UpdateDownloader
import com.basauri.ftmowidget.update.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data object Checking : DownloadUiState
    data class InProgress(val percent: Int) : DownloadUiState
    data class Ready(val uri: Uri) : DownloadUiState
    data class Failed(val reasonCode: Int) : DownloadUiState
}

/**
 * Self-driving update flow: checks on open, downloads a newer release without
 * being asked, and hands the APK to the system installer as soon as it lands.
 *
 * Android will always show its own install confirmation — a normal app cannot
 * install a package silently — so the last tap belongs to the OS, not to us.
 * What this removes is the three taps before it.
 */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<DownloadUiState>(DownloadUiState.Idle) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var receiver by remember { mutableStateOf<BroadcastReceiver?>(null) }
    var installsAllowed by remember { mutableStateOf(canInstallPackages(context)) }
    // Guards the automatic pass so returning to this screen doesn't re-download.
    var autoRan by remember { mutableStateOf(false) }

    fun cleanup() {
        pollJob?.cancel()
        pollJob = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    DisposableEffect(Unit) { onDispose { cleanup() } }

    fun startDownload(target: UpdateInfo) {
        cleanup()
        state = DownloadUiState.InProgress(percent = 0)
        val downloader = UpdateDownloader(context)
        val id = downloader.enqueue(
            url = target.apkUrl,
            apkName = target.apkName,
            title = context.getString(R.string.app_name) + " " + target.latestTag,
        )
        receiver = downloader.registerCompletionReceiver(id) { status ->
            when (status) {
                is DownloadStatus.Successful -> {
                    val uri = status.uri
                    if (uri == null) {
                        state = DownloadUiState.Failed(reasonCode = -1)
                    } else {
                        state = DownloadUiState.Ready(uri)
                        // Straight to the installer; the OS takes it from here.
                        if (canInstallPackages(context)) {
                            downloader.launchInstaller(uri)
                            (context as? Activity)?.finish()
                        } else {
                            installsAllowed = false
                        }
                    }
                }
                is DownloadStatus.Failed -> state = DownloadUiState.Failed(status.reason)
                else -> Unit
            }
        }
        pollJob = scope.launch {
            while (true) {
                val s = downloader.query(id)
                if (s is DownloadStatus.Running && s.bytesTotal > 0) {
                    state = DownloadUiState.InProgress(((s.bytesDone * 100) / s.bytesTotal).toInt())
                }
                if (s is DownloadStatus.Successful || s is DownloadStatus.Failed) break
                delay(500)
            }
        }
    }

    fun check(autoDownload: Boolean) {
        error = null
        state = DownloadUiState.Checking
        scope.launch {
            runCatching { UpdateChecker().checkLatest() }
                .onSuccess { latest ->
                    info = latest
                    state = DownloadUiState.Idle
                    if (latest.isNewer && autoDownload) startDownload(latest)
                }
                .onFailure { t ->
                    state = DownloadUiState.Idle
                    error = context.getString(
                        R.string.update_check_failed,
                        t.message ?: t::class.java.simpleName,
                    )
                }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoRan) {
            autoRan = true
            check(autoDownload = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.update_section_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.update_current_label, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
        )

        val current = info
        when (val s = state) {
            DownloadUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is DownloadUiState.InProgress -> {
                Text(
                    text = stringResource(R.string.update_downloading, s.percent),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { (s.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is DownloadUiState.Ready -> if (installsAllowed) {
                Text(
                    text = stringResource(R.string.update_opening_installer),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                // Without this permission the installer intent goes nowhere, so
                // say what is missing instead of appearing to hang.
                Text(
                    text = stringResource(R.string.update_needs_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openInstallPermissionSettings(context) }) {
                        Text(stringResource(R.string.update_allow_installs))
                    }
                    OutlinedButton(onClick = {
                        installsAllowed = canInstallPackages(context)
                        if (installsAllowed) {
                            UpdateDownloader(context).launchInstaller(s.uri)
                            (context as? Activity)?.finish()
                        }
                    }) { Text(stringResource(R.string.update_install_button)) }
                }
            }

            is DownloadUiState.Failed -> Text(
                text = stringResource(R.string.update_download_failed, s.reasonCode),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )

            DownloadUiState.Idle -> if (current != null && !current.isNewer) {
                Text(
                    text = stringResource(R.string.update_dialog_uptodate_body, current.currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (current?.isNewer == true && state !is DownloadUiState.InProgress) {
            Text(
                text = "${stringResource(R.string.update_dialog_title_new)}: ${current.latestTag}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current.releaseNotes.isNotBlank()) {
                Text(
                    text = current.releaseNotes.take(400),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = state !is DownloadUiState.InProgress && state != DownloadUiState.Checking,
                onClick = { check(autoDownload = true) },
            ) { Text(stringResource(R.string.update_check_button)) }

            current?.let { latest ->
                if (latest.htmlUrl.isNotBlank()) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(latest.htmlUrl))
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }) { Text(stringResource(R.string.update_open_release)) }
                }
            }
        }
    }
}

/**
 * Since API 26 the install prompt is per-app, not a global toggle, so this has
 * to be re-checked rather than assumed from the manifest permission.
 */
private fun canInstallPackages(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true
    }

private fun openInstallPermissionSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        .setData(Uri.parse("package:${context.packageName}"))
        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    runCatching { context.startActivity(intent) }
}
