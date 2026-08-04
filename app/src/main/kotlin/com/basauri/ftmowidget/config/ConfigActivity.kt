package com.basauri.ftmowidget.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.basauri.ftmowidget.R
import com.basauri.ftmowidget.data.AccountRef
import com.basauri.ftmowidget.data.AccountRepository
import com.basauri.ftmowidget.data.Providers
import com.basauri.ftmowidget.data.WidgetBinding
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
                        appWidgetId = appWidgetId,
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
    appWidgetId: Int,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts = remember { mutableListOf<AccountRef>().toMutableStateList() }
    var url by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var alpha by remember { mutableStateOf(1f) }
    var refreshInterval by remember { mutableStateOf(5) }
    // Which account this particular widget shows; WidgetBinding.ALL stacks them.
    var binding by remember { mutableStateOf<String?>(null) }

    val invalidUrlText = context.getString(R.string.config_invalid_url)
    val hasWidget = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

    LaunchedEffect(Unit) {
        accounts.addAll(repository.accounts())
        alpha = repository.backgroundAlpha()
        refreshInterval = repository.refreshIntervalMinutes()
        binding = if (hasWidget) repository.binding(appWidgetId) else null
        if (binding == null) binding = accounts.firstOrNull()?.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = context.getString(R.string.config_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = context.getString(R.string.config_instructions), style = MaterialTheme.typography.bodyMedium)

        // ---- configured accounts ----
        if (accounts.isNotEmpty()) {
            Text(
                text = context.getString(R.string.config_accounts_title),
                style = MaterialTheme.typography.titleSmall,
            )
            accounts.forEach { ref ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = ref.displayName(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = Providers.of(ref.identity.provider).displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                repository.removeAccount(ref.id)
                                accounts.remove(ref)
                                if (binding == ref.id) binding = accounts.firstOrNull()?.id
                                FtmoWidget().updateAll(context)
                            }
                        },
                    ) { Text(context.getString(R.string.config_remove)) }
                }
            }
            HorizontalDivider()
        }

        // ---- add an account ----
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; isError = false },
            label = { Text(context.getString(R.string.config_url_hint)) },
            singleLine = false,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy,
            onClick = {
                val id = Providers.parse(url.text)
                if (id == null) {
                    isError = true
                    status = invalidUrlText
                    return@Button
                }
                val provider = Providers.of(id.provider)
                scope.launch {
                    busy = true
                    status = context.getString(R.string.config_testing)
                    // Fetch before storing: this validates the link and gives the
                    // account a real label in the list before the first refresh.
                    runCatching { provider.fetch(id) }
                        .onSuccess { snapshot ->
                            val ref = repository.addAccount(id, snapshot.accountLabel)
                            accounts.removeAll { it.id == ref.id }
                            accounts.add(ref)
                            if (binding == null) binding = ref.id
                            url = TextFieldValue("")
                            isError = false
                            status = context.getString(
                                R.string.config_added,
                                "${provider.displayName} · ${snapshot.accountLabel}",
                            )
                            FtmoWidget().updateAll(context)
                        }
                        .onFailure { t ->
                            isError = true
                            status = context.getString(
                                R.string.config_test_fail,
                                t.message ?: t::class.java.simpleName,
                            )
                        }
                    busy = false
                }
            },
        ) { Text(context.getString(R.string.config_add_account)) }

        // ---- what this widget shows ----
        if (hasWidget && accounts.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = context.getString(R.string.config_this_widget_shows),
                style = MaterialTheme.typography.titleSmall,
            )
            accounts.forEach { ref ->
                BindingOption(
                    label = ref.displayName(),
                    selected = binding == ref.id,
                    onClick = { binding = ref.id },
                )
            }
            if (accounts.size > 1) {
                BindingOption(
                    label = context.getString(R.string.config_all_accounts),
                    selected = binding == WidgetBinding.ALL,
                    onClick = { binding = WidgetBinding.ALL },
                )
            }
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
            text = "${context.getString(R.string.config_refresh_interval)}: $refreshInterval min",
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

        status?.let {
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            enabled = accounts.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    repository.setBackgroundAlpha(alpha)
                    repository.setRefreshIntervalMinutes(refreshInterval)
                    if (hasWidget) binding?.let { repository.setBinding(appWidgetId, it) }
                    // Re-render directly so the new opacity and binding apply
                    // immediately, without waiting on the network-constrained
                    // refresh worker.
                    FtmoWidget().updateAll(context)
                    onSaved()
                }
            },
        ) { Text(context.getString(R.string.config_save)) }

        Spacer(modifier = Modifier.height(16.dp))
        UpdateSection()
    }
}

@Composable
private fun BindingOption(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}
