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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.lifecycleScope
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.FtmoClient
import com.basauri.ftmowidget.data.FtmoRepository
import com.basauri.ftmowidget.data.ShareUrlParser
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

        val repository = FtmoRepository(applicationContext)
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
            RefreshWorker.scheduleRecurring(this@ConfigActivity)
            RefreshWorker.requestImmediate(this@ConfigActivity)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    repository: FtmoRepository,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val titleText = context.getString(R.string.config_title)
    val instructionsText = context.getString(R.string.config_instructions)
    val hintText = context.getString(R.string.config_url_hint)
    val testText = context.getString(R.string.config_test)
    val saveText = context.getString(R.string.config_save)
    val invalidUrlText = context.getString(R.string.config_invalid_url)

    LaunchedEffect(Unit) {
        repository.currentIdentity()?.let { id ->
            url = TextFieldValue(
                "https://trader.ftmo.com/live-metrix/${id.login}/share/${id.sharingCode}"
            )
        }
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
                val id = ShareUrlParser.parse(url.text)
                if (id == null) {
                    isError = true
                    status = invalidUrlText
                    return@OutlinedButton
                }
                scope.launch {
                    status = "Testing…"
                    runCatching { FtmoClient().fetchMetrix(id.login, id.sharingCode) }
                        .onSuccess { resp ->
                            val acc = resp.info.accountProductType ?: "FTMO"
                            status = context.getString(R.string.config_test_ok, acc)
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
                val id = ShareUrlParser.parse(url.text)
                if (id == null) {
                    isError = true
                    status = invalidUrlText
                    return@Button
                }
                scope.launch {
                    repository.setIdentity(id)
                    onSaved()
                }
            }) { Text(saveText) }
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
