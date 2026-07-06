package io.github.entewurzelauskuh.mp4tomp3.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionJob
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the job-list screen (F1–F3, F8, F11). Talks only to [JobRepository]; starting
 * the background service is delegated to [startConversions] (wired in Phase 6) so the UI never
 * references the service directly.
 */
class MainViewModel(
    private val repository: JobRepository,
    private val startConversions: () -> Unit,
) : ViewModel() {
    /** Jobs in repository (FIFO) order; the screen renders them newest-first. */
    val jobs: StateFlow<List<ConversionJob>> = repository.jobs

    /** Each picked file becomes a queued job immediately, then the service is nudged (F3). */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        repository.enqueue(uris)
        startConversions()
    }

    fun cancel(id: String) = repository.cancel(id)

    fun clear(id: String) = repository.clear(id)
}
