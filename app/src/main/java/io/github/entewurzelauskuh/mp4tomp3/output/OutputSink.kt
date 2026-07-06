package io.github.entewurzelauskuh.mp4tomp3.output

import java.io.OutputStream

/**
 * A destination for a finished `.mp3`: either the public Music folder via `MediaStore`
 * ([MediaStoreSink]) or a user-chosen SAF tree ([SafTreeSink]). Phase 3 implements both.
 *
 * **Frozen contract** (spec §6.2). Lifecycle per job: [open] → write to the returned
 * stream → [finalize] on success, or [abort] on failure/cancel (which deletes the partial
 * file). Implementations own collision handling inside [open].
 */
interface OutputSink {
    /** Reserve the target for [desiredBaseName] (resolving name collisions) and open it for writing. */
    fun open(desiredBaseName: String): OpenOutput

    /** Commit a fully-written output (e.g. clear `IS_PENDING`) and release the handle. */
    fun finalize(handle: OutputHandle)

    /** Discard a partial output — delete the reserved file/row and release the handle. */
    fun abort(handle: OutputHandle)
}

/**
 * The result of [OutputSink.open]: a writable [stream], a human-readable [humanPath] for
 * the UI (e.g. `"Music/Holiday.mp3"`), and an opaque [handle] to pass to
 * [OutputSink.finalize]/[OutputSink.abort].
 */
class OpenOutput(
    val stream: OutputStream,
    val humanPath: String,
    val handle: OutputHandle,
)

/**
 * Opaque handle to a reserved output, implementation-specific (a `MediaStore` row Uri or a
 * `DocumentFile`). Callers pass it back to the sink and never inspect it.
 */
interface OutputHandle
