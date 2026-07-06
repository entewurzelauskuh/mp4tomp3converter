package io.github.entewurzelauskuh.mp4tomp3.settings

/**
 * Pure mapping between [OutputTarget] and the single stored string it persists to
 * (spec §9.1 "settings serialization"). The DataStore layer holds one nullable
 * `output_tree_uri` preference: absent/blank ⇒ [OutputTarget.Default], otherwise
 * [OutputTarget.SafTree].
 */
object OutputTargetSerialization {
    /** The persisted string for [target], or `null` to clear the key (meaning "default"). */
    fun encode(target: OutputTarget): String? = when (target) {
        is OutputTarget.Default -> null
        is OutputTarget.SafTree -> target.treeUri
    }

    /** The [OutputTarget] for a stored string; `null`/blank ⇒ [OutputTarget.Default]. */
    fun decode(stored: String?): OutputTarget = if (stored.isNullOrBlank()) OutputTarget.Default else OutputTarget.SafTree(stored)
}
