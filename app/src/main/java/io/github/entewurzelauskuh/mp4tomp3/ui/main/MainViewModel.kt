package io.github.entewurzelauskuh.mp4tomp3.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionJob
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the job-list screen (F1–F3, F8, F11). Talks only to [JobRepository]; starting
 * the background service is delegated to [startConversions] (wired in Phase 6) so the UI never
 * references the service directly.
 *
 * Picking files no longer enqueues immediately — it stages a [pendingSelection], which the host
 * shows as the pre-conversion options screen (issue #5); [startConversion] commits it.
 */
class MainViewModel(
    private val repository: JobRepository,
    private val startConversions: () -> Unit,
) : ViewModel() {
    /** Jobs in repository (FIFO) order; the screen renders them newest-first. */
    val jobs: StateFlow<List<ConversionJob>> = repository.jobs

    private val _pendingSelection = MutableStateFlow<List<Uri>?>(null)

    /**
     * Files picked but not yet enqueued. While non-null, the host shows the pre-conversion options
     * screen (issue #5); [startConversion] or [cancelSelection] clears it.
     */
    val pendingSelection: StateFlow<List<Uri>?> = _pendingSelection.asStateFlow()

    /** Picking files opens the options screen (issue #5) rather than enqueuing immediately. */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _pendingSelection.value = uris
    }

    /**
     * Confirm the options screen: enqueue the pending files with the chosen [options], nudge the
     * service (F3), and dismiss the screen. No-op if nothing is pending.
     */
    fun startConversion(options: ConversionOptions) {
        val uris = _pendingSelection.value ?: return
        repository.enqueue(uris, options)
        _pendingSelection.value = null
        startConversions()
    }

    /** Dismiss the options screen without converting anything. */
    fun cancelSelection() {
        _pendingSelection.value = null
    }

    fun cancel(id: String) = repository.cancel(id)

    fun clear(id: String) = repository.clear(id)
}
