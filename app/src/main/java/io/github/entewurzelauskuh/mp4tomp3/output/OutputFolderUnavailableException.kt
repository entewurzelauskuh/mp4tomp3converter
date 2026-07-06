package io.github.entewurzelauskuh.mp4tomp3.output

import java.io.IOException

/**
 * Thrown by an [OutputSink] when its target can no longer be written to — e.g. a chosen
 * SAF tree that was deleted or whose persisted permission was revoked (spec §6.5).
 *
 * The conversion service maps this to
 * [io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason.OutputFolderUnavailable] and, per
 * the spec, never silently falls back to writing elsewhere.
 */
class OutputFolderUnavailableException(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
