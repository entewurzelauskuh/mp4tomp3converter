package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.net.Uri

/**
 * A single unit of work: one `.mp4` source to be converted to one `.mp3`.
 *
 * Immutable — the [JobRepository] replaces the whole object on every state change so
 * that the backing `StateFlow` emits a fresh value. Jobs are compared/looked up by
 * [id], never by [sourceUri] (the repository never touches Uri's methods — see the
 * "no Android deps in jobs/ except Uri" rule in the spec).
 *
 * Originally a frozen §6.2 contract; [options] was added deliberately (issue #5) as the carrier
 * for per-conversion encoding settings. It is defaulted so existing construction stays valid — do
 * not otherwise reorder or remove fields.
 *
 * @property id stable UUID assigned at enqueue time
 * @property sourceUri content URI of the picked `.mp4` (held opaquely)
 * @property displayName source file's display name, e.g. `"Holiday.mp4"`
 * @property state current [JobState]
 * @property createdAt wall-clock millis when the job was enqueued (for display ordering)
 * @property options per-conversion encoding options chosen for this job (issue #5)
 */
data class ConversionJob(
    val id: String,
    val sourceUri: Uri,
    val displayName: String,
    val state: JobState,
    val createdAt: Long,
    val options: ConversionOptions = ConversionOptions.Default,
)

/**
 * The lifecycle state of a [ConversionJob].
 *
 * Frozen contract (spec §6.2). Terminal states are [Done], [Failed], [Cancelled];
 * [Queued] and [Running] are active.
 */
sealed interface JobState {
    /** Waiting in the FIFO queue; not yet started. */
    data object Queued : JobState

    /** Actively converting. [progressPercent] is clamped to `0..100`. */
    data class Running(val progressPercent: Int) : JobState

    /** Finished successfully. [outputDescription] is a human path, e.g. `"Music/Holiday.mp3"`. */
    data class Done(val outputDescription: String) : JobState

    /** Failed with a categorised [reason] (mapped to a user string in the UI layer). */
    data class Failed(val reason: FailureReason) : JobState

    /** Cancelled by the user (any partial output has been deleted). */
    data object Cancelled : JobState
}
