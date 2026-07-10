package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.content.Context
import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.engine.ConverterResult
import io.github.entewurzelauskuh.mp4tomp3.output.OpenOutput
import io.github.entewurzelauskuh.mp4tomp3.output.OutputFolderUnavailableException
import io.github.entewurzelauskuh.mp4tomp3.output.OutputSink
import io.github.entewurzelauskuh.mp4tomp3.output.StorageErrors
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import io.github.entewurzelauskuh.mp4tomp3.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * The sequential queue-draining loop (spec §6.4), extracted from any Android `Service`
 * lifecycle so it can be unit-tested with fakes.
 *
 * [drainQueue] processes one [JobState.Queued] job at a time, FIFO, until none remain,
 * driving each through open → convert → finalize/abort against the injected collaborators.
 * The service that hosts it (see [ConversionService]) only supplies real collaborators and
 * an [onProgress] callback that refreshes the notification — it owns no draining logic.
 *
 * @param repository the single source of truth for job state (Phase 1 contract).
 * @param converter the engine (Phase 2); called blocking, hence [drainQueue] is `suspend`
 *   and the service runs it on [kotlinx.coroutines.Dispatchers.Default].
 * @param sinkProvider resolves the [OutputSink] for the current [OutputTarget]; read fresh
 *   per job so a settings change mid-queue takes effect on the next job (spec F6/§6.6).
 * @param settings source of the current [OutputTarget].
 * @param context passed to [AudioConverter.convert] (the engine needs a `ContentResolver`).
 * @param onProgress invoked with the whole [ConversionJob] whenever its progress advances,
 *   so the service can update the ongoing notification. Defaults to a no-op for tests.
 */
class QueueDrainer(
    private val repository: JobRepository,
    private val converter: AudioConverter,
    private val sinkProvider: (OutputTarget) -> OutputSink,
    private val settings: SettingsRepository,
    private val context: Context,
    private val onProgress: (job: ConversionJob) -> Unit = {},
) {
    /**
     * Drains queued jobs sequentially until [JobRepository.nextQueued] returns `null`.
     *
     * Per job (all state transitions go through [repository], the single source of truth):
     * `markRunning` (skipped if the job was cancelled while queued) → resolve the sink for
     * the current [OutputTarget] and `open` it (an [OutputFolderUnavailableException] here
     * fails the job with [FailureReason.OutputFolderUnavailable] and moves on) → run the
     * blocking [converter] → then finalize/abort based on the result and any late cancel.
     */
    suspend fun drainQueue() {
        while (true) {
            val job = repository.nextQueued() ?: return
            val id = job.id

            // Another drainer/cancel raced us to this job; re-select the next one.
            if (!repository.markRunning(id)) continue

            val target = settings.outputTarget.first()
            val sink = sinkProvider(target)

            val open: OpenOutput = try {
                sink.open(job.displayName)
            } catch (_: OutputFolderUnavailableException) {
                // The chosen SAF folder is gone / permission lost — never silently write
                // elsewhere (spec §6.5); fail this job and keep draining the rest.
                repository.markFailed(id, FailureReason.OutputFolderUnavailable)
                continue
            } catch (t: Throwable) {
                // Any other failure while reserving the target (e.g. IOException/ENOSPC) fails
                // just this job — it must never wedge the queue with a stuck Running job.
                repository.markFailed(id, reasonFor(t))
                continue
            }

            processOpenedJob(id, sink, open)
        }
    }

    /**
     * Runs the converter for an already-opened job and finalises it. Split out so
     * [drainQueue] reads as the FIFO loop and this holds the per-job success/failure/cancel
     * branching (spec §6.4).
     */
    private fun processOpenedJob(id: String, sink: OutputSink, open: OpenOutput) {
        val job = repository.jobs.value.firstOrNull { it.id == id }
        if (job == null) {
            // The job vanished (shouldn't happen for a non-terminal job) — clean up and bail.
            runCatching { open.stream.close() }
            sink.abort(open.handle)
            return
        }

        val result = try {
            converter.convert(
                context = context,
                sourceUri = job.sourceUri,
                output = open.stream,
                options = job.options,
                onProgress = { percent ->
                    repository.updateProgress(id, percent)
                    // Emit the freshly-updated job so the service can refresh the notification.
                    repository.jobs.value.firstOrNull { it.id == id }?.let(onProgress)
                },
                isCancelled = { repository.isCancellationRequested(id) },
            )
        } catch (t: Throwable) {
            // The converter threw unexpectedly (RuntimeException / OOM / UnsatisfiedLinkError /
            // …). Never leave the job Running: close the stream, delete the partial output, fail.
            runCatching { open.stream.close() }
            sink.abort(open.handle)
            repository.markFailed(id, reasonFor(t))
            return
        }

        // A cancel that arrived during conversion wins regardless of the converter result.
        if (repository.isCancellationRequested(id)) {
            runCatching { open.stream.close() }
            sink.abort(open.handle)
            repository.markCancelled(id)
            return
        }

        when (result) {
            is ConverterResult.Success -> finalizeSuccess(id, sink, open)

            is ConverterResult.Failure -> {
                runCatching { open.stream.close() }
                sink.abort(open.handle)
                repository.markFailed(id, result.reason)
            }
        }
    }

    private fun reasonFor(t: Throwable): FailureReason = if (StorageErrors.isOutOfSpace(t)) FailureReason.StorageFull else FailureReason.Unknown

    /**
     * Closes the stream and commits the output. A failure while closing or finalising means
     * the bytes never landed safely, so the partial output is aborted and the job fails —
     * ENOSPC maps to [FailureReason.StorageFull], anything else to [FailureReason.Unknown]
     * (spec §6.5).
     */
    private fun finalizeSuccess(id: String, sink: OutputSink, open: OpenOutput) {
        try {
            open.stream.close()
            sink.finalize(open.handle)
        } catch (t: Throwable) {
            sink.abort(open.handle)
            val reason = if (StorageErrors.isOutOfSpace(t)) FailureReason.StorageFull else FailureReason.Unknown
            repository.markFailed(id, reason)
            return
        }
        repository.markDone(id, open.humanPath)
    }
}
