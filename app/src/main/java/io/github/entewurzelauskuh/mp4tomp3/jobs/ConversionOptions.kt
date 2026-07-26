package io.github.entewurzelauskuh.mp4tomp3.jobs

/**
 * Per-conversion encoding options, chosen on the pre-conversion options screen before
 * a batch is enqueued. Carried on each [ConversionJob] and handed to
 * [io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter.convert].
 *
 * **The extension point for per-conversion settings.** Every field is defaulted so existing call
 * sites, jobs, and tests stay valid. No Android dependencies — the `jobs/` package stays
 * unit-testable (plain values only).
 *
 * @property bitrateKbps constant-bitrate MP3 rate in kbps; expected to be one of [BITRATE_CHOICES].
 * @property trimStartMs start of the range to convert, in ms from the source start.
 * @property trimEndMs end of the range to convert, in ms; [TRIM_END_FULL] means "to the end".
 * @property tags ID3 tags to write on the output MP3; [Id3Tags.None] writes nothing.
 * @property deleteSourceAfter attempt to delete the source file after a successful conversion.
 */
data class ConversionOptions(
    val bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = TRIM_END_FULL,
    val tags: Id3Tags = Id3Tags.None,
    val deleteSourceAfter: Boolean = false,
) {
    /** True if this converts only a sub-range of the source. */
    val isTrimmed: Boolean get() = trimStartMs > 0L || trimEndMs != TRIM_END_FULL

    companion object {
        /** CBR rate used unless the user changes it — a good quality/size balance for most audio. */
        const val DEFAULT_BITRATE_KBPS = 192

        /** The selectable CBR bitrates, smallest to largest. */
        val BITRATE_CHOICES: List<Int> = listOf(128, 192, 256, 320)

        /** [trimEndMs] sentinel meaning "convert through to the end of the source". */
        const val TRIM_END_FULL = -1L

        /** Standard options: default bitrate, full length, no tags, no deletion. */
        val Default = ConversionOptions()
    }
}

/**
 * ID3 metadata to write on the output MP3. MP4 sources use iTunes-style atoms, not ID3;
 * these are read from the source via `MediaMetadataRetriever` and written as ID3v2.3 on the MP3.
 * Blank/`null` fields are omitted. Cover art is intentionally out of scope for now.
 *
 * The "extended" field set the user chose: the three core fields plus album artist, track, genre,
 * year, composer, disc, and a free-text comment. Each maps to a single ID3v2.3 frame in
 * [io.github.entewurzelauskuh.mp4tomp3.engine.Id3v2].
 *
 * @property title song/clip title (TIT2). @property artist lead performer (TPE1).
 * @property album album (TALB). @property albumArtist album artist / band (TPE2).
 * @property track track number, free text e.g. "3" or "3/12" (TRCK). @property genre genre (TCON).
 * @property year recording year (TYER). @property composer composer (TCOM).
 * @property disc disc number, free text e.g. "1/2" (TPOS). @property comment free-text comment (COMM).
 */
data class Id3Tags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val composer: String? = null,
    val disc: String? = null,
    val comment: String? = null,
) {
    /** True if no field is set — nothing to write. */
    fun isEmpty(): Boolean = title.isNullOrBlank() && artist.isNullOrBlank() && album.isNullOrBlank() &&
        albumArtist.isNullOrBlank() && track.isNullOrBlank() && genre.isNullOrBlank() &&
        year.isNullOrBlank() && composer.isNullOrBlank() && disc.isNullOrBlank() &&
        comment.isNullOrBlank()

    companion object {
        val None = Id3Tags()
    }
}
