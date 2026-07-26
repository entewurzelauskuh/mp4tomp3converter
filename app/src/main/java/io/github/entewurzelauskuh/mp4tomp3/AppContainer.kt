package io.github.entewurzelauskuh.mp4tomp3

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.engine.MediaCodecLameConverter
import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import io.github.entewurzelauskuh.mp4tomp3.jobs.SourceInfo
import io.github.entewurzelauskuh.mp4tomp3.output.MediaStoreSink
import io.github.entewurzelauskuh.mp4tomp3.output.OutputSink
import io.github.entewurzelauskuh.mp4tomp3.output.SafTreeSink
import io.github.entewurzelauskuh.mp4tomp3.settings.DataStoreSettingsRepository
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import io.github.entewurzelauskuh.mp4tomp3.settings.SettingsRepository

/**
 * The app's tiny manual-DI container (spec §3 "no DI framework"). Constructs and holds the
 * single instances that make up the running app and wires them together. Created once by [App].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository.from(appContext)

    val converter: AudioConverter = MediaCodecLameConverter()

    val jobRepository: JobRepository = JobRepository(
        resolveDisplayName = { uri -> resolveDisplayName(uri) },
    )

    /** Builds the sink for the current target: MediaStore for Default, a SAF sink otherwise. */
    val sinkProvider: (OutputTarget) -> OutputSink = { target ->
        when (target) {
            is OutputTarget.Default -> MediaStoreSink(appContext)
            is OutputTarget.SafTree -> SafTreeSink(appContext, target.treeUri)
        }
    }

    /**
     * Best-effort source deletion after a successful conversion. Commonly refused: the
     * document picker (`OpenMultipleDocuments`) grants read-only access, so `deleteDocument` throws
     * or returns false unless the provider happens to allow deletion. Returns true only if deleted.
     */
    val deleteSource: (Uri) -> Boolean = { uri ->
        runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
            .getOrDefault(false)
    }

    /**
     * Probe a source for the options screen: its duration and ID3 tags suggested
     * from the source's own metadata, falling back to the filename (minus extension) for the title.
     * Blocking (opens the media) — call off the main thread.
     */
    val probeSource: (Uri) -> SourceInfo = { uri ->
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            SourceInfo(
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                suggestedTags = Id3Tags(
                    title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: baseName(resolveDisplayName(uri)),
                    artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    // YEAR is often absent on MP4; DATE is usually a full ISO timestamp, so pull just
                    // the 4-digit year out of it (TYER is defined as a 4-character year).
                    year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?: meta(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { Regex("""\d{4}""").find(it)?.value },
                    composer = meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                        ?: meta(MediaMetadataRetriever.METADATA_KEY_WRITER),
                    disc = meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                    // No standard MediaMetadataRetriever key for a comment → left for the user.
                ),
            )
        } catch (t: Throwable) {
            SourceInfo(0L, Id3Tags(title = baseName(resolveDisplayName(uri))))
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** File name without its extension, e.g. `"Holiday.mp4"` → `"Holiday"`. */
    private fun baseName(displayName: String): String = displayName.substringBeforeLast('.')

    /**
     * Query the content resolver for a source's display name, falling back to the URI's tail.
     *
     * Called synchronously by `JobRepository.enqueue`, which runs on the UI thread from the
     * picker result. A `DISPLAY_NAME` query IPCs to the document provider, so this can briefly
     * block for a slow/remote provider — acceptable for the small multi-select counts in v1.
     */
    private fun resolveDisplayName(uri: Uri): String {
        var name: String? = null
        runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        name = cursor.getString(index)
                    }
                }
        }
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: "video.mp4"
    }
}
