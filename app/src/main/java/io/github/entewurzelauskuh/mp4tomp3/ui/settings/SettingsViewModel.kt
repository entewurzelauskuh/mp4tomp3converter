package io.github.entewurzelauskuh.mp4tomp3.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import io.github.entewurzelauskuh.mp4tomp3.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen (F6). The single v1 setting is the output target. The
 * persistable-permission grab for a chosen SAF tree happens in the screen (it needs a
 * `ContentResolver`); this only persists the resulting tree-URI string.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {
    val outputTarget: StateFlow<OutputTarget> =
        settings.outputTarget.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            OutputTarget.Default,
        )

    fun onFolderChosen(treeUri: String) = viewModelScope.launch {
        settings.setOutputTarget(OutputTarget.SafTree(treeUri))
    }

    fun resetToDefault() = viewModelScope.launch {
        settings.setOutputTarget(OutputTarget.Default)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
