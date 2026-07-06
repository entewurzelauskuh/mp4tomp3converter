package io.github.entewurzelauskuh.mp4tomp3

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.engine.MediaCodecLameConverter
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
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
