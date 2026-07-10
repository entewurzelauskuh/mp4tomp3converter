package io.github.entewurzelauskuh.mp4tomp3.jobs

/**
 * Per-conversion encoding options, chosen on the pre-conversion options screen (issue #5) before
 * a batch is enqueued. Carried on each [ConversionJob] and handed to
 * [io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter.convert].
 *
 * **This is the extension point for per-conversion settings.** Today it holds only the MP3
 * [bitrateKbps] (issue #6 — the worked example wired end-to-end). Future controls on the same
 * screen add a field here, each with a sensible default:
 *  - a trim range (issue #7)
 *  - ID3 tags (issue #8)
 *
 * Keep every new field **defaulted** so existing call sites, jobs, and tests stay valid, and add
 * a matching control on `ConversionOptionsScreen`.
 *
 * No Android dependencies — the `jobs/` package stays unit-testable (plain values only).
 *
 * @property bitrateKbps constant-bitrate MP3 rate in kbps; expected to be one of [BITRATE_CHOICES].
 */
data class ConversionOptions(
    val bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
) {
    companion object {
        /** CBR rate used unless the user changes it — a good quality/size balance for most audio. */
        const val DEFAULT_BITRATE_KBPS = 192

        /** The selectable CBR bitrates (issue #6), smallest to largest. */
        val BITRATE_CHOICES: List<Int> = listOf(128, 192, 256, 320)

        /** Standard options: [DEFAULT_BITRATE_KBPS] (and full length + filename title once #7/#8 land). */
        val Default = ConversionOptions()
    }
}
