package com.basauri.ftmowidget.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.AccountRepository
import com.basauri.ftmowidget.data.Providers
import com.basauri.ftmowidget.widget.FtmoWidget
import com.basauri.ftmowidget.work.RefreshScheduler
import com.basauri.ftmowidget.work.RefreshWorker
import kotlinx.coroutines.launch

class ConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED)

        val repository = AccountRepository(applicationContext)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        repository = repository,
                        onSaved = { finishOk() },
                    )
                }
            }
        }
    }

    private fun finishOk() {
        val resultIntent = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        lifecycleScope.launch {
            val interval = AccountRepository(applicationContext).refreshIntervalMinutes()
            RefreshScheduler.schedule(applicationContext, interval)
            RefreshWorker.requestImmediate(applicationContext)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    repository: AccountRepository,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var alpha by remember { mutableStateOf(1f) }
    var refreshInterval by remember { mutableStateOf(5) }

    val titleText = context.getString(R.string.config_title)
    val instructionsText = context.getString(R.string.config_instructions)
    val hintText = context.getString(R.string.config_url_hint)
    val testText = context.getString(R.string.config_test)
    val saveText = context.getString(R.string.config_save)
    val invalidUrlText = context.getString(R.string.config_invalid_url)

    LaunchedEffect(Unit) {
        repository.currentIdentity()?.let { id ->
            url = TextFieldValue(Providers.of(id.provider).shareUrl(id))
        }
        alpha = repository.backgroundAlpha()
        refreshInterval = repository.refreshIntervalMinutes()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = titleText, style = MaterialTheme.typography.headlineSmall)
        Text(text = instructionsText, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; isError = false },
            label = { Text(hintText) },
            singleLine = false,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val id = Providers.parse(url.text)
                if (id == null) {
                    isError = true
                    status = invalidUrlText
                    return@OutlinedButton
                }
                val provider = Providers.of(id.provider)
                scope.launch {
                    status = "Testing…"
                    runCatching { provider.fetch(id) }
                        .onSuccess { snapshot ->
                            status = context.getString(
                                R.string.config_test_ok,
                                "${provider.displayName} · ${snapshot.accountLabel}",
                            )
                            isError = false
                        }
                        .onFailure { t ->
                            status = context.getString(
                                R.string.config_test_fail,
                                t.message ?: t::class.java.simpleName,
                            )
                            isError = true
                        }
                }
            }) { Text(testText) }

            Button(onClick = {
                val id = Providers.parse(url.text)
                if (id == null) {
                    isError = true
                    status = invalidUrlText
                    return@Button
                }
                scope.launch {
                    repository.setIdentity(id)
                    repository.setBackgroundAlpha(alpha)
                    repository.setRefreshIntervalMinutes(refreshInterval)
                    // Re-render directly so the new opacity applies immediately,
                    // without waiting on the network-constrained refresh worker.
                    FtmoWidget().updateAll(context)
                    onSaved()
                }
            }) { Text(saveText) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${context.getString(R.string.config_background_opacity)}: ${(alpha * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = alpha,
            onValueChange = { alpha = it },
            valueRange = 0f..1f,
            steps = 19,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${context.getString(R.string.config_refresh_interval)}: ${refreshInterval} min",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf(1, 5, 15, 30, 60).forEach { minutes ->
                if (minutes == refreshInterval) {
                    Button(onClick = { refreshInterval = minutes }) { Text("$minutes") }
                } else {
                    OutlinedButton(onClick = { refreshInterval = minutes }) { Text("$minutes") }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        status?.let {
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        UpdateSection()
    }
}
