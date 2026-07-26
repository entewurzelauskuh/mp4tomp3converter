package io.github.entewurzelauskuh.mp4tomp3.engine

import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import java.io.ByteArrayOutputStream

/**
 * Minimal ID3v2.3 tag writer. Produces the tag bytes to **prepend** to an MP3 stream —
 * ID3v2 is a prefix, so it needs no seek-back and works with our streaming sink (unlike the Xing
 * tag, see the v0.1.1 duration fix). Text frames use UTF-16 (encoding 0x01) so any character is
 * representable. Pure bytes; no Android dependencies.
 */
object Id3v2 {
    /** The ID3v2.3 tag bytes for [tags], or an empty array if there is nothing to write. */
    fun tag(tags: Id3Tags): ByteArray {
        if (tags.isEmpty()) return ByteArray(0)
        val body = ByteArrayOutputStream()
        fun add(frame: ByteArray?) = frame?.let { body.write(it, 0, it.size) }
        add(textFrame("TIT2", tags.title)) // title
        add(textFrame("TPE1", tags.artist)) // artist / lead performer
        add(textFrame("TALB", tags.album)) // album
        add(textFrame("TPE2", tags.albumArtist)) // album artist / band
        add(textFrame("TRCK", tags.track)) // track number
        add(textFrame("TCON", tags.genre)) // genre (free text in v2.3)
        add(textFrame("TYER", tags.year)) // recording year
        add(textFrame("TCOM", tags.composer)) // composer
        add(textFrame("TPOS", tags.disc)) // disc / part-of-set
        add(commentFrame(tags.comment)) // free-text comment
        val bodyBytes = body.toByteArray()

        val out = ByteArrayOutputStream()
        out.write('I'.code)
        out.write('D'.code)
        out.write('3'.code)
        out.write(3) // major version 3 → ID3v2.3.0
        out.write(0) // revision
        out.write(0) // flags
        synchsafe(bodyBytes.size).let { out.write(it, 0, it.size) } // 28-bit tag size
        out.write(bodyBytes, 0, bodyBytes.size)
        return out.toByteArray()
    }

    /** A UTF-16 `T***` text frame for [id], or null if [text] is blank. */
    private fun textFrame(id: String, text: String?): ByteArray? {
        val value = text?.takeIf { it.isNotBlank() } ?: return null
        val content = ByteArrayOutputStream()
        content.write(0x01) // text encoding: UTF-16 with BOM
        content.write(0xFF) // BOM (little-endian)
        content.write(0xFE)
        value.toByteArray(Charsets.UTF_16LE).let { content.write(it, 0, it.size) }
        content.write(0x00) // UTF-16 string terminator
        content.write(0x00)
        return frameOf(id, content.toByteArray())
    }

    /**
     * A UTF-16 `COMM` comment frame, or null if [text] is blank. COMM differs from a plain text
     * frame: after the encoding byte it carries a 3-byte language and a (here empty) short content
     * description, both before the comment text itself (ID3v2.3 §4.11).
     */
    private fun commentFrame(text: String?): ByteArray? {
        val value = text?.takeIf { it.isNotBlank() } ?: return null
        val content = ByteArrayOutputStream()
        content.write(0x01) // text encoding: UTF-16 with BOM
        "eng".toByteArray(Charsets.US_ASCII).let { content.write(it, 0, it.size) } // language
        // Empty short content description: BOM + UTF-16 terminator.
        content.write(0xFF)
        content.write(0xFE)
        content.write(0x00)
        content.write(0x00)
        // The comment text: BOM + UTF-16LE bytes (runs to the end of the frame).
        content.write(0xFF)
        content.write(0xFE)
        value.toByteArray(Charsets.UTF_16LE).let { content.write(it, 0, it.size) }
        return frameOf("COMM", content.toByteArray())
    }

    /** Wrap [content] in an ID3v2.3 frame header: 4-char id, 32-bit BE size, two flag bytes. */
    private fun frameOf(id: String, content: ByteArray): ByteArray {
        val frame = ByteArrayOutputStream()
        id.toByteArray(Charsets.US_ASCII).let { frame.write(it, 0, it.size) } // 4-char frame id
        sizeBigEndian(content.size).let { frame.write(it, 0, it.size) } // v2.3 frame size: plain 32-bit BE
        frame.write(0x00) // frame flags
        frame.write(0x00)
        frame.write(content, 0, content.size)
        return frame.toByteArray()
    }

    /** 4-byte big-endian integer (ID3v2.3 frame size). */
    private fun sizeBigEndian(n: Int): ByteArray = byteArrayOf(
        (n ushr 24 and 0xFF).toByte(),
        (n ushr 16 and 0xFF).toByte(),
        (n ushr 8 and 0xFF).toByte(),
        (n and 0xFF).toByte(),
    )

    /** 4-byte synchsafe integer (7 significant bits per byte; ID3v2 tag-size format). */
    private fun synchsafe(n: Int): ByteArray = byteArrayOf(
        (n ushr 21 and 0x7F).toByte(),
        (n ushr 14 and 0x7F).toByte(),
        (n ushr 7 and 0x7F).toByte(),
        (n and 0x7F).toByte(),
    )
}
