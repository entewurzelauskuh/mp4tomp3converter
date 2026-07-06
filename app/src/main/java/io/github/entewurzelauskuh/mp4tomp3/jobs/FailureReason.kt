package io.github.entewurzelauskuh.mp4tomp3.jobs

/**
 * Categorised reason a [ConversionJob] failed (spec §6.5).
 *
 * Kept as a pure enum with **no** user-facing text — the mapping from a reason to a
 * localised message lives in the UI layer (`strings.xml`), per the "strings only via
 * strings.xml" rule. Each constant documents its trigger.
 */
enum class FailureReason {
    /** The MP4 has no audio track (no `audio/…` MIME). → "This video has no audio track." */
    NoAudioTrack,

    /** A decoder could not be created/configured for the audio codec.
     *  → "The audio format in this file isn't supported by this device." */
    UnsupportedAudioCodec,

    /** The audio has more than two channels (surround); downmix is a future feature.
     *  → "Surround-sound audio isn't supported yet." */
    UnsupportedChannelLayout,

    /** The source URI could not be opened (permission revoked / gone).
     *  → "The file could not be read." */
    SourceUnreadable,

    /** The chosen SAF output folder is missing or its permission was lost.
     *  → "The output folder is no longer available — check Settings." */
    OutputFolderUnavailable,

    /** Ran out of storage while writing (IOException / ENOSPC).
     *  → "Not enough storage space." */
    StorageFull,

    /** Anything else; the UI shows a generic message and the cause is logged.
     *  → "Conversion failed." */
    Unknown,
}
