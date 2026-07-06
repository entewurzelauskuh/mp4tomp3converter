package io.github.entewurzelauskuh.mp4tomp3.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/**
 * Default [OutputSink]: writes the `.mp3` into the device's public **Music** folder via
 * `MediaStore` (spec F5, §6.6). No storage permission is needed on `minSdk ≥ 29` because
 * the app owns the rows it inserts.
 *
 * Lifecycle mirrors `MediaStore`'s `IS_PENDING` protocol: [open] inserts a pending row and
 * hands back its stream, [finalize] clears `IS_PENDING` so the file becomes visible to music
 * players/file managers, and [abort] deletes the row (and its partial bytes) on failure or
 * cancel. Collisions are resolved deterministically here — we pre-query existing display
 * names and drive [FileNaming.mp3NameFor] ourselves rather than letting `MediaStore`
 * auto-rename, so the ` (1)`, ` (2)` scheme is our own (spec §6.6).
 */
class MediaStoreSink(
    context: Context,
) : OutputSink {
    // Hold the application context so the sink outlives any Activity/Service that created it.
    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    override fun open(desiredBaseName: String): OpenOutput {
        val finalName =
            FileNaming.mp3NameFor(desiredBaseName) { candidate ->
                existingNames().contains(candidate)
            }

        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, finalName)
                put(MediaStore.Audio.Media.MIME_TYPE, MIME_MP3)
                put(MediaStore.Audio.Media.RELATIVE_PATH, MUSIC_RELATIVE_PATH)
                // Pending hides the row from other apps until we finalise it, and lets us
                // delete it cleanly if the conversion fails or is cancelled.
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

        val rowUri =
            resolver.insert(collectionUri(), values)
                ?: throw IOException("MediaStore returned no Uri for $finalName")

        // If the stream can't be opened, roll the pending row back so no orphan remains.
        val stream =
            try {
                resolver.openOutputStream(rowUri, "w")
                    ?: throw IOException("Could not open output stream for $rowUri")
            } catch (e: IOException) {
                runCatching { resolver.delete(rowUri, null, null) }
                throw e
            }

        return OpenOutput(
            stream = stream,
            humanPath = MUSIC_RELATIVE_PATH + finalName,
            handle = MediaStoreHandle(rowUri),
        )
    }

    override fun finalize(handle: OutputHandle) {
        val rowUri = (handle as MediaStoreHandle).uri
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        resolver.update(rowUri, values, null, null)
    }

    override fun abort(handle: OutputHandle) {
        val rowUri = (handle as MediaStoreHandle).uri
        // Best-effort: aborting must never itself throw (it runs on the failure path).
        runCatching { resolver.delete(rowUri, null, null) }
    }

    /**
     * The set of `DISPLAY_NAME`s already present under [MUSIC_RELATIVE_PATH]. Queried fresh
     * per [open] so the collision check reflects files added since the sink was built. We
     * scope by `RELATIVE_PATH` so unrelated Music sub-folders don't inflate the check.
     */
    private fun existingNames(): Set<String> {
        val names = HashSet<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val args = arrayOf(MUSIC_RELATIVE_PATH)
        resolver.query(collectionUri(), projection, selection, args, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(nameColumn)?.let(names::add)
            }
        }
        return names
    }

    // The primary shared volume is where the public Music folder lives; pending inserts and
    // queries must target the same collection so the pre-query matches what we insert.
    private fun collectionUri(): Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private companion object {
        const val MIME_MP3 = "audio/mpeg"

        // MediaStore stores and reports RELATIVE_PATH with a trailing slash; match that form
        // in both the insert and the collision query so they agree.
        const val MUSIC_RELATIVE_PATH = "Music/"
    }
}

/** [OutputHandle] wrapping the inserted `MediaStore` row [uri]. Opaque to callers. */
private class MediaStoreHandle(
    val uri: Uri,
) : OutputHandle
