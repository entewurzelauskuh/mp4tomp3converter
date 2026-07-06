package io.github.entewurzelauskuh.mp4tomp3.settings

/**
 * Where converted `.mp3` files are written (spec F6, §6.6). Either the default public
 * Music folder via `MediaStore`, or a user-chosen SAF tree identified by its persisted
 * tree-URI string. The URI is kept as a plain [String] so this model stays Android-free
 * and unit-testable; the SAF sink reconstructs a `Uri` from it.
 */
sealed interface OutputTarget {
    /** Public Music folder via `MediaStore` (`RELATIVE_PATH = "Music/"`). The default. */
    data object Default : OutputTarget

    /** A user-chosen folder, addressed by its persistable SAF tree URI. */
    data class SafTree(val treeUri: String) : OutputTarget
}
