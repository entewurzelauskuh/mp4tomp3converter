package io.github.entewurzelauskuh.mp4tomp3.output

import android.system.ErrnoException
import android.system.OsConstants

/**
 * Best-effort detection of an out-of-space ("ENOSPC") condition anywhere in a throwable's
 * cause chain. Used by the engine and the service to map write failures to
 * [io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason.StorageFull] (spec §6.5) while
 * everything else stays `Unknown`.
 */
object StorageErrors {
    fun isOutOfSpace(throwable: Throwable?): Boolean {
        var cause = throwable
        val seen = HashSet<Throwable>()
        while (cause != null && seen.add(cause)) {
            if (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) return true
            val message = cause.message
            if (message != null &&
                (message.contains("ENOSPC") || message.contains("No space left", ignoreCase = true))
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
