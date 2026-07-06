package io.github.entewurzelauskuh.mp4tomp3

import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.entewurzelauskuh.mp4tomp3.engine.ConverterResult
import io.github.entewurzelauskuh.mp4tomp3.engine.MediaCodecLameConverter
import io.github.entewurzelauskuh.mp4tomp3.output.MediaStoreSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * End-to-end (spec §7 Phase 7): a real fixture is converted by the LAME engine straight into
 * the Music folder via [MediaStoreSink], then read back to prove the output is a valid,
 * playable MP3 of the right duration. Emulator only.
 */
@RunWith(AndroidJUnit4::class)
class EndToEndConversionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun fixtureConvertsToPlayableMp3InMediaStore() {
        val source = File(context.cacheDir, "e2e_source.mp4")
        instrumentation.context.assets.open("sine_stereo_aac_3s.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }

        val uniqueBase = "e2e_${System.nanoTime()}.mp4"
        val sink = MediaStoreSink(context)
        val open = sink.open(uniqueBase)
        var rowUri: Uri? = null
        try {
            val result = MediaCodecLameConverter()
                .convert(context, Uri.fromFile(source), open.stream, onProgress = {}, isCancelled = { false })
            open.stream.close()
            assertEquals(ConverterResult.Success, result)
            sink.finalize(open.handle)

            val finalName = open.humanPath.removePrefix("Music/")
            rowUri = findInMusic(finalName)
            assertNotNull("converted file should be queryable in MediaStore", rowUri)

            // Roughly the 3.0 s source length. A generous bound: the test's job is "valid,
            // playable, roughly-right-length MP3", not sample-accurate duration (LAME encoder
            // delay + decoder padding on a CBR re-encode shift it by a few frames).
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, rowUri)
                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                assertTrue("duration was $durationMs ms", abs(durationMs - 3000L) <= 300L)
            }

            // MediaPlayer.prepare() must succeed on the output.
            val player = MediaPlayer()
            try {
                player.setDataSource(context, rowUri!!)
                player.prepare()
                assertTrue(player.duration > 0)
            } finally {
                player.release()
            }
        } finally {
            // Clean up via the sink handle (deletes the row whether still pending or finalized),
            // so a failure before rowUri is assigned doesn't leak an orphan row. Also release the
            // output FD in case convert() threw before the in-try close().
            runCatching { open.stream.close() }
            runCatching { sink.abort(open.handle) }
            source.delete()
        }
    }

    private fun findInMusic(displayName: String): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(displayName, "Music/"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        return null
    }
}
