package io.github.entewurzelauskuh.mp4tomp3.ui.about

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.entewurzelauskuh.mp4tomp3.R

private const val REPO_URL = "https://github.com/entewurzelauskuh/mp4tomp3converter"
private const val RELEASES_URL = "$REPO_URL/releases"

/**
 * About screen: build info, a link to the source, and honest "how to update" text.
 *
 * **No network.** The app never fetches anything — the buttons hand off to the user's browser via
 * an `ACTION_VIEW` intent, and the update section says plainly the app can't check for updates
 * itself (it has no internet access).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            val (name, code) = versionInfo(context)
            Text(
                text = stringResource(R.string.about_version, name, code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            Text(
                text = stringResource(R.string.about_contribute_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = stringResource(R.string.about_contribute_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = { openUrl(context, REPO_URL) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_view_on_github))
            }

            Text(
                text = stringResource(R.string.about_update_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = stringResource(R.string.about_update_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = { openUrl(context, RELEASES_URL) }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.about_view_releases))
            }
        }
    }
}

/** App version name + code from the package manager (no BuildConfig needed). */
private fun versionInfo(context: Context): Pair<String, Long> = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    (info.versionName ?: "?") to info.longVersionCode
}.getOrDefault("?" to 0L)

/** Open [url] in the user's browser. Best-effort — the app itself never touches the network. */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
