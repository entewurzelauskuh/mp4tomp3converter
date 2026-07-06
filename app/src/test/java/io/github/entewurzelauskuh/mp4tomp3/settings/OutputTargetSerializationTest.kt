package io.github.entewurzelauskuh.mp4tomp3.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutputTargetSerializationTest {
    @Test
    fun encodeDefaultClearsKey() {
        assertNull(OutputTargetSerialization.encode(OutputTarget.Default))
    }

    @Test
    fun encodeSafTreeStoresUri() {
        assertEquals(
            "content://tree/primary%3AMusic",
            OutputTargetSerialization.encode(OutputTarget.SafTree("content://tree/primary%3AMusic")),
        )
    }

    @Test
    fun decodeNullOrBlankIsDefault() {
        assertEquals(OutputTarget.Default, OutputTargetSerialization.decode(null))
        assertEquals(OutputTarget.Default, OutputTargetSerialization.decode(""))
        assertEquals(OutputTarget.Default, OutputTargetSerialization.decode("   "))
    }

    @Test
    fun decodeNonBlankIsSafTree() {
        assertEquals(
            OutputTarget.SafTree("content://tree/x"),
            OutputTargetSerialization.decode("content://tree/x"),
        )
    }

    @Test
    fun roundTrips() {
        val safTree = OutputTarget.SafTree("content://tree/x")
        assertEquals(safTree, OutputTargetSerialization.decode(OutputTargetSerialization.encode(safTree)))
        assertEquals(
            OutputTarget.Default,
            OutputTargetSerialization.decode(OutputTargetSerialization.encode(OutputTarget.Default)),
        )
    }
}
