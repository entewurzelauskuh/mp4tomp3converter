package io.github.entewurzelauskuh.mp4tomp3.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionJob
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import io.github.entewurzelauskuh.mp4tomp3.jobs.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Files picked but not yet enqueued. [single] carries the probed [SourceInfo] for a **one-file**
 * selection (resolved asynchronously); it stays null for a multi-file selection, whose per-file
 * controls (trim/ID3/size) don't apply.
 */
data class PendingSelection(val uris: List<Uri>, val single: SourceInfo? = null)

/**
 * ViewModel for the job-list screen (F1–F3, F8, F11). Talks only to [JobRepository]; starting the
 * background service is delegated to [startConversions] so the UI never references it directly.
 *
 * Picking files stages a [pendingSelection] (shown as the pre-conversion options screen);
 * [startConversion] commits it. For a single file, [probeSource] resolves duration + suggested tags
 * off the main thread so the options screen can offer trim/ID3/size.
 */
class MainViewModel(
    private val repository: JobRepository,
    private val startConversions: () -> Unit,
    private val probeSource: (Uri) -> SourceInfo = { SourceInfo(0L, Id3Tags.None) },
) : ViewModel() {
    /** Jobs in repository (FIFO) order; the screen renders them newest-first. */
    val jobs: StateFlow<List<ConversionJob>> = repository.jobs

    private val _pendingSelection = MutableStateFlow<PendingSelection?>(null)

    /** While non-null, the host shows the options screen. */
    val pendingSelection: StateFlow<PendingSelection?> = _pendingSelection.asStateFlow()

    /** Picking files opens the options screen rather than enqueuing immediately. */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _pendingSelection.value = PendingSelection(uris)
        // Per-file controls apply to a single file only; probe it off the main thread.
        if (uris.size == 1) {
            val uri = uris[0]
            viewModelScope.launch(Dispatchers.IO) {
                val info = runCatching { probeSource(uri) }.getOrNull() ?: return@launch
                _pendingSelection.update { cur -> if (cur?.uris == uris) cur.copy(single = info) else cur }
            }
        }
    }

    /**
     * Confirm the options screen: enqueue the pending files with the chosen [options], nudge the
     * service (F3), and dismiss the screen. No-op if nothing is pending.
     */
    fun startConversion(options: ConversionOptions) {
        val pending = _pendingSelection.value ?: return
        repository.enqueue(pending.uris, options)
        _pendingSelection.value = null
        startConversions()
    }

    /** Dismiss the options screen without converting anything. */
    fun cancelSelection() {
        _pendingSelection.value = null
    }

    fun cancel(id: String) = repository.cancel(id)

    fun clear(id: String) = repository.clear(id)

    /** Remove all finished (Done/Failed/Cancelled) jobs at once. */
    fun clearAll() = repository.clearTerminal()
}
