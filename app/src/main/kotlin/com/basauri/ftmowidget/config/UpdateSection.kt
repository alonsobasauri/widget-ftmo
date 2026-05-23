package com.basauri.ftmowidget.config

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    data class InProgress(val percent: Int) : DownloadUiState
    data class Ready(val uri: Uri) : DownloadUiState
    data class Failed(val reasonCode: Int) : DownloadUiState
}

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<DownloadUiState>(DownloadUiState.Idle) }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var receiver by remember { mutableStateOf<BroadcastReceiver?>(null) }

    val checkLabel = stringResource(R.string.update_check_button)
    val checkingLabel = stringResource(R.string.update_checking)
    val currentLabel = stringResource(R.string.update_current_label, BuildConfig.VERSION_NAME)

    DisposableEffect(Unit) {
        onDispose {
            pollJob?.cancel()
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
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
        Text(text = currentLabel, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !checking,
                onClick = {
                    error = null
                    info = null
                    checking = true
                    scope.launch {
                        runCatching { UpdateChecker().checkLatest() }
                            .onSuccess {
                                info = it
                                dialogVisible = true
                            }
                            .onFailure { t ->
                                error = context.getString(
                                    R.string.update_check_failed,
                                    t.message ?: t::class.java.simpleName,
                                )
                            }
                        checking = false
                    }
                },
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(checkingLabel)
                } else {
                    Text(checkLabel)
                }
            }
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val currentInfo = info
    if (dialogVisible && currentInfo != null) {
        UpdateDialog(
            info = currentInfo,
            downloadState = downloadState,
            onDownload = {
                downloadState = DownloadUiState.InProgress(percent = 0)
                val downloader = UpdateDownloader(context)
                val id = downloader.enqueue(
                    url = currentInfo.apkUrl,
                    apkName = currentInfo.apkName,
                    title = context.getString(R.string.app_name) + " " + currentInfo.latestTag,
                )
                receiver = downloader.registerCompletionReceiver(id) { status ->
                    downloadState = when (status) {
                        is DownloadStatus.Successful -> status.uri?.let { DownloadUiState.Ready(it) }
                            ?: DownloadUiState.Failed(reasonCode = -1)
                        is DownloadStatus.Failed -> DownloadUiState.Failed(status.reason)
                        else -> downloadState
                    }
                }
                pollJob = scope.launch {
                    while (true) {
                        val s = downloader.query(id)
                        if (s is DownloadStatus.Running && s.bytesTotal > 0) {
                            val pct = ((s.bytesDone * 100) / s.bytesTotal).toInt()
                            downloadState = DownloadUiState.InProgress(percent = pct)
                        }
                        if (s is DownloadStatus.Successful || s is DownloadStatus.Failed) break
                        delay(500)
                    }
                }
            },
            onInstall = { uri ->
                UpdateDownloader(context).launchInstaller(uri)
                (context as? Activity)?.finish()
            },
            onOpenInBrowser = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentInfo.htmlUrl))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            },
            onDismiss = {
                dialogVisible = false
                downloadState = DownloadUiState.Idle
                pollJob?.cancel()
                receiver?.let { runCatching { context.unregisterReceiver(it) } }
                receiver = null
                pollJob = null
            },
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloadState: DownloadUiState,
    onDownload: () -> Unit,
    onInstall: (Uri) -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (info.isNewer) stringResource(R.string.update_dialog_title_new)
                else stringResource(R.string.update_dialog_title_uptodate)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_current_label, info.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Latest: ${info.latestTag}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.sizeBytes > 0) {
                    Text(
                        text = stringResource(R.string.update_size_label, humanBytes(info.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (info.releaseNotes.isNotBlank()) {
                    HorizontalDivider()
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!info.isNewer) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.update_dialog_uptodate_body, info.currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                when (val s = downloadState) {
                    DownloadUiState.Idle -> Unit
                    is DownloadUiState.InProgress -> {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.update_downloading, s.percent),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        LinearProgressIndicator(
                            progress = { (s.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is DownloadUiState.Failed -> {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.update_download_failed, s.reasonCode),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is DownloadUiState.Ready -> Unit
                }
            }
        },
        confirmButton = {
            when (val s = downloadState) {
                is DownloadUiState.Ready -> Button(onClick = { onInstall(s.uri) }) {
                    Text(stringResource(R.string.update_install_button))
                }
                DownloadUiState.Idle, is DownloadUiState.Failed -> if (info.isNewer) {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.update_download_button))
                    }
                }
                is DownloadUiState.InProgress -> Unit
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenInBrowser) {
                    Text(stringResource(R.string.update_open_release))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dismiss))
                }
            }
        },
    )
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
