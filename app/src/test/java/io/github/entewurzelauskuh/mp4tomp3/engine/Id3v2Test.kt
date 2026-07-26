package io.github.entewurzelauskuh.mp4tomp3.engine

import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural tests for the ID3v2.3 tag writer. */
class Id3v2Test {
    @Test
    fun emptyTagsProduceNoBytes() {
        assertTrue(Id3v2.tag(Id3Tags.None).isEmpty())
        assertTrue(Id3v2.tag(Id3Tags(title = "   ")).isEmpty())
    }

    @Test
    fun startsWithId3v23Header() {
        val bytes = Id3v2.tag(Id3Tags(title = "Song"))
        assertTrue(bytes.size > 10)
        assertEquals('I'.code.toByte(), bytes[0])
        assertEquals('D'.code.toByte(), bytes[1])
        assertEquals('3'.code.toByte(), bytes[2])
        assertEquals(3.toByte(), bytes[3]) // major version → ID3v2.3
        assertEquals(0.toByte(), bytes[4])
    }

    @Test
    fun writesTextFrameForEachSetField() {
        val ascii = String(Id3v2.tag(Id3Tags(title = "Song", artist = "Me", album = "Al")), Charsets.ISO_8859_1)
        assertTrue(ascii.contains("TIT2")) // title
        assertTrue(ascii.contains("TPE1")) // artist
        assertTrue(ascii.contains("TALB")) // album
    }

    @Test
    fun omitsFramesForBlankFields() {
        val ascii = String(Id3v2.tag(Id3Tags(title = "Song", artist = "  ")), Charsets.ISO_8859_1)
        assertTrue(ascii.contains("TIT2"))
        assertTrue(!ascii.contains("TPE1"))
    }

    @Test
    fun writesEachExtendedFieldAsItsFrame() {
        val ascii = String(
            Id3v2.tag(
                Id3Tags(
                    albumArtist = "Band",
                    track = "3",
                    genre = "Rock",
                    year = "2026",
                    composer = "Writer",
                    disc = "1",
                    comment = "Nice",
                ),
            ),
            Charsets.ISO_8859_1,
        )
        // Each extended field maps to its ID3v2.3 frame id.
        listOf("TPE2", "TRCK", "TCON", "TYER", "TCOM", "TPOS", "COMM").forEach {
            assertTrue("expected frame $it", ascii.contains(it))
        }
        // COMM carries a language code before its text.
        assertTrue("COMM language", ascii.substringAfter("COMM").contains("eng"))
    }

    @Test
    fun commentOnlyStillProducesATag() {
        val bytes = Id3v2.tag(Id3Tags(comment = "Hello"))
        assertTrue(bytes.size > 10)
        assertEquals('I'.code.toByte(), bytes[0])
    }

    @Test
    fun tagSizeIsSynchsafeBodyLength() {
        val bytes = Id3v2.tag(Id3Tags(title = "Song"))
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        assertEquals(bytes.size - 10, size)
    }
}
