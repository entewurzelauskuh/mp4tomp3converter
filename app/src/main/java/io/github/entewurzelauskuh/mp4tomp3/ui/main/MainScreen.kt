package io.github.entewurzelauskuh.mp4tomp3.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionJob
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobState
import io.github.entewurzelauskuh.mp4tomp3.ui.messageRes

/**
 * The main screen: the conversion job list (newest first), the overflow menu (F2), and the
 * multi-select `video/mp4` picker (F3). Stateless below the ViewModel — it renders
 * [MainViewModel.jobs] and forwards user actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onFilesPicked(uris) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    OverflowMenu(
                        onConvertFile = { picker.launch(arrayOf("video/mp4")) },
                        onSettings = onOpenSettings,
                    )
                },
            )
        },
    ) { innerPadding ->
        if (jobs.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                // Repository order is FIFO (oldest first); the list shows newest first (F1).
                items(items = jobs.asReversed(), key = { it.id }) { job ->
                    JobRow(
                        job = job,
                        onCancel = { viewModel.cancel(job.id) },
                        onClear = { viewModel.clear(job.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(onConvertFile: () -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_convert_file)) },
            onClick = {
                expanded = false
                onConvertFile()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_settings)) },
            onClick = {
                expanded = false
                onSettings()
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JobRow(job: ConversionJob, onCancel: () -> Unit, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = job.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = job.state.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val state = job.state
        if (state is JobState.Running) {
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            when (state) {
                is JobState.Queued, is JobState.Running ->
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }

                else ->
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
            }
        }
    }
}

/** Human-readable one-line state label for a job (F1). */
@Composable
private fun JobState.label(): String = when (this) {
    is JobState.Queued -> stringResource(R.string.state_queued)
    is JobState.Running -> "${stringResource(R.string.state_converting)} $progressPercent%"
    is JobState.Done -> stringResource(R.string.state_done, outputDescription)
    is JobState.Failed -> stringResource(reason.messageRes())
    is JobState.Cancelled -> stringResource(R.string.state_cancelled)
}
