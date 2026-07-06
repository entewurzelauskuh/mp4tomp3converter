package io.github.entewurzelauskuh.mp4tomp3.ui

import androidx.annotation.StringRes
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason

/**
 * Maps a [FailureReason] to its user-facing message (spec §6.5). Kept in the UI layer so all
 * strings live in `strings.xml` and the `jobs/` domain stays free of presentation concerns.
 */
@StringRes
fun FailureReason.messageRes(): Int = when (this) {
    FailureReason.NoAudioTrack -> R.string.failure_no_audio_track
    FailureReason.UnsupportedAudioCodec -> R.string.failure_unsupported_codec
    FailureReason.UnsupportedChannelLayout -> R.string.failure_unsupported_channels
    FailureReason.SourceUnreadable -> R.string.failure_source_unreadable
    FailureReason.OutputFolderUnavailable -> R.string.failure_output_folder_unavailable
    FailureReason.StorageFull -> R.string.failure_storage_full
    FailureReason.Unknown -> R.string.failure_unknown
}
