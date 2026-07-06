package io.github.entewurzelauskuh.mp4tomp3.output

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [MediaStoreSink] against the real device `MediaStore` (spec Phase 3
 * list). Each test cleans up any rows it creates so runs are repeatable and leave no residue
 * in the emulator's Music folder.
 *
 * Runs on an emulator only (spec §10.2). Not runnable on the JVM: it needs a live
 * `ContentResolver` and `MediaStore`.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreSinkTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val sink by lazy { MediaStoreSink(context) }

    // Track every display name we touch so tearDown can delete leftover rows even if a test
    // fails before its own abort/cleanup runs.
    private val createdNames = mutableSetOf<String>()

    private val baseName = "MediaStoreSinkTest_sample.mp4"
    private val expectedMp3 = "MediaStoreSinkTest_sample.mp3"

    @Before
    fun setUp() {
        deleteAllTestRows()
    }

    @After
    fun tearDown() {
        deleteAllTestRows()
    }

    @Test
    fun openThenFinalizeAppearsWithCorrectNameAndMime() {
        val output = sink.open(baseName)
        createdNames += expectedMp3
        assertEquals("Music/$expectedMp3", output.humanPath)

        output.stream.use { it.write(byteArrayOf(0x49, 0x44, 0x33)) } // "ID3"
        sink.finalize(output.handle)

        val row = queryByDisplayName(expectedMp3)
        assertNotNull("finalized file should be visible in MediaStore", row)
        assertEquals("audio/mpeg", row!!.mimeType)
        assertEquals("Music/", row.relativePath)
        assertEquals(0, row.isPending)
    }

    @Test
    fun abortLeavesNoRow() {
        val output = sink.open(baseName)
        createdNames += expectedMp3
        output.stream.use { it.write(byteArrayOf(0x00)) }

        sink.abort(output.handle)

        assertNull("aborted output must not leave a row", queryByDisplayName(expectedMp3))
    }

    @Test
    fun collisionProducesNumberedSuffix() {
        val first = sink.open(baseName)
        createdNames += expectedMp3
        first.stream.use { it.write(byteArrayOf(0x49, 0x44, 0x33)) }
        sink.finalize(first.handle)

        // Second open with the same source must dodge the finalized first file.
        val second = sink.open(baseName)
        val collisionName = "MediaStoreSinkTest_sample (1).mp3"
        createdNames += collisionName
        assertEquals("Music/$collisionName", second.humanPath)
        second.stream.use { it.write(byteArrayOf(0x49, 0x44, 0x33)) }
        sink.finalize(second.handle)

        assertNotNull(queryByDisplayName(expectedMp3))
        assertNotNull(queryByDisplayName(collisionName))
    }

    // --- helpers ------------------------------------------------------------------------

    private data class Row(
        val uri: Uri,
        val mimeType: String?,
        val relativePath: String?,
        val isPending: Int,
    )

    private fun collectionUri() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun queryByDisplayName(name: String): Row? {
        val projection =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.IS_PENDING,
            )
        // Include pending rows so tests observe the pre-finalize state and cleanup can delete
        // rows left pending by a failed test (default queries hide pending rows on API >= 30).
        val queryArgs =
            Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                )
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(name, "Music/"))
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
        context.contentResolver
            .query(collectionUri(), projection, queryArgs, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fun col(name: String) = cursor.getColumnIndexOrThrow(name)
                    val id = cursor.getLong(col(MediaStore.Audio.Media._ID))
                    return Row(
                        uri = Uri.withAppendedPath(collectionUri(), id.toString()),
                        mimeType = cursor.getString(col(MediaStore.Audio.Media.MIME_TYPE)),
                        relativePath = cursor.getString(col(MediaStore.Audio.Media.RELATIVE_PATH)),
                        isPending = cursor.getInt(col(MediaStore.Audio.Media.IS_PENDING)),
                    )
                }
            }
        return null
    }

    /** Delete every row this test class may have created (by known display names). */
    private fun deleteAllTestRows() {
        val names = (createdNames + expectedMp3 + "MediaStoreSinkTest_sample (1).mp3").toSet()
        for (name in names) {
            queryByDisplayName(name)?.let { row ->
                context.contentResolver.delete(row.uri, null, null)
            }
        }
        createdNames.clear()
    }
}
