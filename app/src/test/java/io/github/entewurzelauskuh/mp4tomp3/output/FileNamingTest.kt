package io.github.entewurzelauskuh.mp4tomp3.output

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamingTest {
    // --- toMp3Name: extension swap + sanitisation ---------------------------------------

    @Test
    fun swapsExtensionToMp3() {
        assertEquals("Holiday.mp3", FileNaming.toMp3Name("Holiday.mp4"))
    }

    @Test
    fun swapsAnyExtensionAndIsCaseInsensitiveOnInput() {
        assertEquals("clip.mp3", FileNaming.toMp3Name("clip.MP4"))
        assertEquals("clip.mp3", FileNaming.toMp3Name("clip.MOV"))
    }

    @Test
    fun addsExtensionWhenSourceHasNone() {
        assertEquals("noext.mp3", FileNaming.toMp3Name("noext"))
    }

    @Test
    fun sanitizesIllegalPathCharacters() {
        assertEquals("a_b_c.mp3", FileNaming.toMp3Name("a/b:c.mp4"))
        assertEquals("x_y_z_w.mp3", FileNaming.toMp3Name("""x\y*z?w.mp4"""))
    }

    @Test
    fun preservesUnicode() {
        assertEquals("Café Ünïcode.mp3", FileNaming.toMp3Name("Café Ünïcode.mp4"))
        assertEquals("日本語.mp3", FileNaming.toMp3Name("日本語.mp4"))
    }

    @Test
    fun fallsBackWhenNothingUsableRemains() {
        // Only whitespace/dots before the extension -> trimmed to empty -> fallback base.
        assertEquals("audio.mp3", FileNaming.toMp3Name("   .mp4"))
    }

    @Test
    fun keepsModerateLengthNamesIntact() {
        val base = "a".repeat(200)
        val result = FileNaming.toMp3Name("$base.mp4")
        assertEquals("$base.mp3", result)
        assertEquals(204, result.length)
    }

    @Test
    fun capsOverlongNamesTo255Chars() {
        val base = "b".repeat(300)
        val result = FileNaming.toMp3Name("$base.mp4")
        assertTrue(result.endsWith(".mp3"))
        assertEquals(255, result.length)
    }

    // --- resolveCollision: deterministic " (n)" scheme ----------------------------------

    @Test
    fun returnsNameUnchangedWhenNoCollision() {
        assertEquals("Foo.mp3", FileNaming.resolveCollision("Foo.mp3") { false })
    }

    @Test
    fun appendsFirstFreeNumericSuffix() {
        val taken = setOf("Foo.mp3", "Foo (1).mp3", "Foo (2).mp3")
        assertEquals("Foo (3).mp3", FileNaming.resolveCollision("Foo.mp3") { it in taken })
    }

    @Test
    fun collisionKeepsResultWithinLengthCap() {
        val base = "c".repeat(300)
        val desired = FileNaming.toMp3Name("$base.mp4") // already capped to 255
        val result = FileNaming.resolveCollision(desired) { it == desired }
        assertTrue(result.endsWith(" (1).mp3"))
        assertTrue("length was ${result.length}", result.length <= 255)
    }

    // --- mp3NameFor: composition --------------------------------------------------------

    @Test
    fun mp3NameForComposesSwapAndCollision() {
        val taken = setOf("Foo.mp3")
        assertEquals("Foo (1).mp3", FileNaming.mp3NameFor("Foo.mp4") { it in taken })
    }
}
