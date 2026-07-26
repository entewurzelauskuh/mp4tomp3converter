package io.github.entewurzelauskuh.mp4tomp3.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.entewurzelauskuh.mp4tomp3.App
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionService
import io.github.entewurzelauskuh.mp4tomp3.ui.about.AboutScreen
import io.github.entewurzelauskuh.mp4tomp3.ui.main.MainScreen
import io.github.entewurzelauskuh.mp4tomp3.ui.main.MainViewModel
import io.github.entewurzelauskuh.mp4tomp3.ui.options.ConversionOptionsScreen
import io.github.entewurzelauskuh.mp4tomp3.ui.settings.SettingsScreen
import io.github.entewurzelauskuh.mp4tomp3.ui.settings.SettingsViewModel
import io.github.entewurzelauskuh.mp4tomp3.ui.theme.Mp4ToMp3Theme

/**
 * Single-activity host (spec §6.1). Wires the ViewModels to the [App]'s `AppContainer`, navigates
 * between the job list, settings and about with simple saved state, requests `POST_NOTIFICATIONS`
 * (API 33+) when the user first enqueues work, and routes videos shared from other apps
 * into the options flow.
 */
class MainActivity : ComponentActivity() {
    private enum class Screen { Main, Settings, About }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container

        setContent {
            Mp4ToMp3Theme {
                var screen by rememberSaveable { mutableStateOf(Screen.Main) }

                val mainViewModel: MainViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MainViewModel(
                                repository = container.jobRepository,
                                startConversions = {
                                    ensureNotificationPermission()
                                    ConversionService.start(this@MainActivity)
                                },
                                probeSource = container.probeSource,
                            )
                        }
                    },
                )

                // Route a video shared from another app into the options flow, once.
                var shareHandled by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!shareHandled) {
                        val shared = sharedVideoUris(intent)
                        if (shared.isNotEmpty()) mainViewModel.onFilesPicked(shared)
                        shareHandled = true
                    }
                }

                val pendingSelection by mainViewModel.pendingSelection.collectAsStateWithLifecycle()

                when {
                    // A pending selection takes over the UI with the options screen.
                    pendingSelection != null -> ConversionOptionsScreen(
                        fileCount = pendingSelection!!.uris.size,
                        sourceInfo = pendingSelection!!.single,
                        onStart = mainViewModel::startConversion,
                        onCancel = mainViewModel::cancelSelection,
                    )

                    screen == Screen.Settings -> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer { SettingsViewModel(container.settingsRepository) }
                            },
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { screen = Screen.Main },
                        )
                    }

                    screen == Screen.About -> AboutScreen(onBack = { screen = Screen.Main })

                    else -> MainScreen(
                        viewModel = mainViewModel,
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenAbout = { screen = Screen.About },
                    )
                }
            }
        }
    }

    /** The video URIs from an incoming SEND / SEND_MULTIPLE share intent. */
    private fun sharedVideoUris(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))

            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()

            else -> emptyList()
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
