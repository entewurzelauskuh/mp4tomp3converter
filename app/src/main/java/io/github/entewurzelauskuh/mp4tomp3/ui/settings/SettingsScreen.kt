package io.github.entewurzelauskuh.mp4tomp3.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget

/**
 * Settings screen (F6). One setting in v1: the output folder. Launches
 * `ACTION_OPEN_DOCUMENT_TREE`, persists the grant, and shows the current target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target by viewModel.outputTarget.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Persist read+write so we can still write to the folder in a later session.
            // takePersistableUriPermission can throw SecurityException (e.g. a provider that
            // returns a tree without a persistable grant) — don't crash; just don't switch to
            // a folder we can't keep access to.
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.isSuccess
            if (persisted) {
                viewModel.onFolderChosen(uri.toString())
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_output_folder_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = outputTargetLabel(target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = { folderPicker.launch(null) }) {
                    Text(stringResource(R.string.settings_choose_folder))
                }
                if (target is OutputTarget.SafTree) {
                    TextButton(onClick = { viewModel.resetToDefault() }) {
                        Text(stringResource(R.string.settings_reset_default))
                    }
                }
            }
        }
    }
}

/** Human-readable description of the current output target (F6). */
@Composable
private fun outputTargetLabel(target: OutputTarget): String = when (target) {
    is OutputTarget.Default -> stringResource(R.string.settings_output_music_default)
    is OutputTarget.SafTree -> rememberSafTreeName(target.treeUri)
}

/**
 * The chosen folder's human-readable name (F6) — the `DocumentFile` display name (e.g. "Music"),
 * not the raw tree document id (e.g. "primary:Music"). Resolved once per URI. Mirrors
 * `SafTreeSink.humanTreePath`.
 */
@Composable
private fun rememberSafTreeName(treeUri: String): String {
    val context = LocalContext.current
    return remember(treeUri) {
        val uri = Uri.parse(treeUri)
        runCatching { DocumentFile.fromTreeUri(context, uri)?.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: treeUri
    }
}
