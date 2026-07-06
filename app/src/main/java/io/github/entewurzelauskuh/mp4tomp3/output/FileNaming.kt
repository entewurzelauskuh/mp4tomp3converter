package io.github.entewurzelauskuh.mp4tomp3.output

/**
 * Pure filename logic shared by all [OutputSink] implementations (spec F7). No Android,
 * no I/O — fully unit-tested.
 *
 * - [toMp3Name] swaps the source extension for `.mp3` and sanitises illegal characters.
 * - [resolveCollision] applies the deterministic ` (1)`, ` (2)`, … scheme, driven by an
 *   `exists` predicate the sink supplies (a `MediaStore` query or a `DocumentFile` lookup).
 * - [mp3NameFor] composes the two.
 */
object FileNaming {
    private const val EXTENSION = ".mp3"

    /**
     * Conservative maximum filename length in characters. 255 is the common ext4/FAT limit;
     * we count characters (not bytes), which is safe because it can only under-fill.
     */
    private const val MAX_FILENAME_LENGTH = 255

    /** Used when sanitising leaves nothing usable (e.g. a name of only illegal characters). */
    private const val FALLBACK_BASE = "audio"

    /** Characters disallowed on FAT/ext4/SAF targets, plus C0 control characters. */
    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

    /** Leading/trailing dots and whitespace are trimmed (a trailing dot is illegal on FAT). */
    private val TRIM_EDGES = Regex("""^[.\s]+|[.\s]+$""")

    /**
     * Convert a source display name to a sanitised `<base>.mp3` name.
     *
     * Examples: `"a/b:c.mp4"` → `"a_b_c.mp3"`, `"Café.MP4"` → `"Café.mp3"`,
     * `"noext"` → `"noext.mp3"`.
     */
    fun toMp3Name(sourceDisplayName: String): String {
        val base = sanitizeBase(stripExtension(sourceDisplayName))
        return capBase(base, reserved = 0) + EXTENSION
    }

    /**
     * First variant of [desiredName] (already a `*.mp3` name) for which [exists] is false,
     * appending ` (1)`, ` (2)`, … Returns [desiredName] unchanged if it does not collide.
     * The numeric suffix is inserted before the extension and the base is shortened if
     * needed to keep the whole name within [MAX_FILENAME_LENGTH].
     */
    fun resolveCollision(desiredName: String, exists: (String) -> Boolean): String {
        if (!exists(desiredName)) return desiredName
        val base = desiredName.removeSuffix(EXTENSION)
        var n = 1
        while (true) {
            val suffix = " ($n)"
            val candidate = capBase(base, reserved = suffix.length) + suffix + EXTENSION
            if (!exists(candidate)) return candidate
            n++
        }
    }

    /** Convenience: [toMp3Name] then [resolveCollision]. */
    fun mp3NameFor(sourceDisplayName: String, exists: (String) -> Boolean): String = resolveCollision(toMp3Name(sourceDisplayName), exists)

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        // Only treat a trailing ".ext" as an extension; ignore a leading dot ("hidden" files).
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun sanitizeBase(raw: String): String {
        val cleaned = raw.replace(ILLEGAL_CHARS, "_").replace(TRIM_EDGES, "")
        return cleaned.ifEmpty { FALLBACK_BASE }
    }

    /** Truncate [base] so that `base + reserved + ".mp3"` fits within [MAX_FILENAME_LENGTH]. */
    private fun capBase(base: String, reserved: Int): String {
        val allowed = (MAX_FILENAME_LENGTH - EXTENSION.length - reserved).coerceAtLeast(1)
        return if (base.length > allowed) base.substring(0, allowed) else base
    }
}
