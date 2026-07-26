package io.github.entewurzelauskuh.mp4tomp3.jobs

/**
 * A source file's probed info for the pre-conversion options screen: its playable duration in ms
 * (for trim and the size estimate) and ID3 tags suggested from its metadata/filename.
 * Plain data — no Android dependencies; the probe itself lives in `AppContainer`.
 */
data class SourceInfo(
    val durationMs: Long,
    val suggestedTags: Id3Tags = Id3Tags.None,
)
