package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory, single-source-of-truth store of [ConversionJob]s for the current process
 * lifetime (spec §6.2, §6.6 — no persistence across process death in v1).
 *
 * Jobs are kept in **FIFO insertion order** (oldest first). [nextQueued] returns the
 * oldest [JobState.Queued] job, so a single consumer ([ConversionService]) drains the
 * queue sequentially. The UI presents the same list newest-first (a display concern).
 *
 * Only the `jobs` flow plus [enqueue]/[cancel]/[clear] are used by the UI; the
 * `mark*`/[updateProgress]/[nextQueued]/[isCancellationRequested] methods are the
 * driving surface for the service. All mutations are atomic (`StateFlow.update`), so
 * calls from the UI thread and the service coroutine are safe.
 *
 * Deliberately free of Android dependencies except [Uri], which it only ever holds and
 * passes on — it never calls Uri's methods.
 *
 * @param resolveDisplayName maps a source [Uri] to its display name (e.g. `"Holiday.mp4"`).
 *   Injected because name resolution needs a `ContentResolver`, which must not leak into
 *   this package. Fakes supply a pure function in tests.
 * @param now supplies the current wall-clock millis (injectable for deterministic tests).
 * @param newId supplies a fresh unique id (injectable for deterministic tests).
 */
class JobRepository(
    private val resolveDisplayName: (Uri) -> String,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _jobs = MutableStateFlow<List<ConversionJob>>(emptyList())
    val jobs: StateFlow<List<ConversionJob>> = _jobs.asStateFlow()

    /** Ids whose running job the user asked to cancel; the service observes and finalises. */
    private val cancelRequested: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Adds one [JobState.Queued] job per URI, in the given order, at the end of the queue.
     * @return the new job ids in the same order.
     */
    fun enqueue(uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val created = uris.map { uri ->
            ConversionJob(
                id = newId(),
                sourceUri = uri,
                displayName = resolveDisplayName(uri),
                state = JobState.Queued,
                createdAt = now(),
            )
        }
        _jobs.update { it + created }
        return created.map { it.id }
    }

    /**
     * Cancels a job. A [JobState.Queued] job is marked [JobState.Cancelled] immediately;
     * for a [JobState.Running] job the request is recorded — the running converter observes
     * [isCancellationRequested] and stops, after which the service calls [markCancelled]
     * (which deletes any partial output via the sink). Terminal jobs are unaffected.
     */
    fun cancel(id: String) {
        _jobs.update { list ->
            val job = list.find { it.id == id } ?: return@update list
            when (job.state) {
                is JobState.Queued -> list.withState(id, JobState.Cancelled)

                is JobState.Running -> {
                    cancelRequested.add(id)
                    list
                }

                else -> list
            }
        }
    }

    /** Removes a **terminal** (Done/Failed/Cancelled) job from the list (F11 "Clear"). No-op otherwise. */
    fun clear(id: String) {
        _jobs.update { list ->
            val job = list.find { it.id == id } ?: return@update list
            if (job.state.isTerminal()) {
                cancelRequested.remove(id)
                list.filterNot { it.id == id }
            } else {
                list
            }
        }
    }

    /** The oldest queued job (FIFO), or `null` if none are waiting. */
    fun nextQueued(): ConversionJob? = _jobs.value.firstOrNull { it.state is JobState.Queued }

    /**
     * Transitions a [JobState.Queued] job to [JobState.Running] with 0% progress.
     * @return `true` if it started; `false` if the job was cancelled/gone in the meantime.
     */
    fun markRunning(id: String): Boolean {
        var started = false
        _jobs.update { list ->
            val job = list.find { it.id == id } ?: return@update list
            if (job.state is JobState.Queued) {
                started = true
                list.withState(id, JobState.Running(0))
            } else {
                list
            }
        }
        return started
    }

    /** Updates progress (clamped `0..100`) only while the job is [JobState.Running]. */
    fun updateProgress(id: String, percent: Int) {
        _jobs.update { list ->
            val job = list.find { it.id == id } ?: return@update list
            if (job.state is JobState.Running) {
                list.withState(id, JobState.Running(percent.coerceIn(0, 100)))
            } else {
                list
            }
        }
    }

    /** Marks a job [JobState.Done] with a human-readable output description. */
    fun markDone(id: String, outputDescription: String) {
        cancelRequested.remove(id)
        _jobs.update { it.withState(id, JobState.Done(outputDescription)) }
    }

    /** Marks a job [JobState.Failed] with a categorised [reason]. */
    fun markFailed(id: String, reason: FailureReason) {
        cancelRequested.remove(id)
        _jobs.update { it.withState(id, JobState.Failed(reason)) }
    }

    /** Marks a job [JobState.Cancelled] (called by the service after aborting partial output). */
    fun markCancelled(id: String) {
        cancelRequested.remove(id)
        _jobs.update { it.withState(id, JobState.Cancelled) }
    }

    /** Whether the user requested cancellation of the (running) job [id]. Polled by the converter. */
    fun isCancellationRequested(id: String): Boolean = id in cancelRequested

    private fun JobState.isTerminal(): Boolean = this is JobState.Done || this is JobState.Failed || this is JobState.Cancelled

    /** Returns a copy of the list with job [id]'s state replaced, preserving order. */
    private fun List<ConversionJob>.withState(id: String, state: JobState): List<ConversionJob> = map { if (it.id == id) it.copy(state = state) else it }
}
