package io.github.entewurzelauskuh.mp4tomp3.output

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.OutputStream

/**
 * [OutputSink] that writes the `.mp3` into a user-chosen folder addressed by a persisted SAF
 * tree URI (spec F6, §6.6). Used when the output target is
 * [io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget.SafTree].
 *
 * Unlike [MediaStoreSink] there is no pending protocol: a `DocumentFile` created via
 * [DocumentFile.createFile] exists immediately, so [finalize] is a no-op and [abort] deletes
 * the created document. If the tree is gone or its persisted permission was revoked, every
 * operation throws [OutputFolderUnavailableException] and **never** silently writes elsewhere
 * (spec §6.5, `OutputFolderUnavailable`).
 */
class SafTreeSink(
    context: Context,
    treeUriString: String,
) : OutputSink {
    // Hold the application context so the sink outlives any Activity/Service that created it.
    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver
    private val treeUri: Uri = Uri.parse(treeUriString)

    override fun open(desiredBaseName: String): OpenOutput = try {
        // fromTreeUri returns null when the URI is unusable; canWrite() covers a revoked
        // or read-only grant. Either way the folder is unavailable — surface it, never
        // fall back to writing elsewhere.
        val tree =
            DocumentFile.fromTreeUri(appContext, treeUri)
                ?: throw OutputFolderUnavailableException("Output tree could not be opened")
        if (!tree.canWrite()) {
            throw OutputFolderUnavailableException("Output tree is not writable")
        }

        val finalName =
            FileNaming.mp3NameFor(desiredBaseName) { candidate ->
                tree.findFile(candidate) != null
            }

        val created =
            tree.createFile(MIME_MP3, finalName)
                ?: throw IOException("Could not create $finalName in the output folder")

        // Everything after createFile can throw (rename/stream-open, incl. SecurityException on
        // a revoked grant). If it does, delete the just-created document so we never leave an
        // empty/misnamed orphan behind — mirroring MediaStoreSink's pending-row rollback.
        try {
            val onDiskName = ensureDeterministicName(created, finalName, tree)
            OpenOutput(
                stream = openStream(created),
                humanPath = humanTreePath(tree) + "/" + onDiskName,
                handle = SafHandle(created),
            )
        } catch (t: Throwable) {
            runCatching { created.delete() }
            throw t
        }
    } catch (e: SecurityException) {
        // A lost persisted permission manifests as SecurityException mid-operation.
        throw OutputFolderUnavailableException("Lost permission to the output folder", e)
    }

    /** No pending concept for SAF — the file already exists once created. */
    override fun finalize(handle: OutputHandle) {
        // Intentionally nothing: DocumentFile has no IS_PENDING equivalent. The stream is
        // flushed/closed by the caller; there is nothing to commit here.
    }

    override fun abort(handle: OutputHandle) {
        val doc = (handle as SafHandle).document
        // Best-effort: aborting runs on the failure path and must never itself throw.
        runCatching { doc.delete() }
    }

    /**
     * Make [created]'s display name exactly [desiredName] when the provider changed it,
     * returning the name that is actually on disk afterwards. Renaming can itself hit a
     * collision the provider invented, so we only rename when the current name differs and we
     * accept the provider's result if the rename does not stick.
     *
     * **Known limitation (inherent to SAF):** some providers auto-rename on `createFile`
     * (e.g. case-insensitive collisions [findFile] didn't catch, or appended extensions). When
     * the forced rename can't reclaim [desiredName], the provider's name wins and the returned
     * [humanPath] reflects it — so the app never lies about where the file is, but the exact
     * deterministic ` (n)` scheme isn't guaranteed on such providers. The default MediaStore
     * path has no such caveat.
     */
    private fun ensureDeterministicName(
        created: DocumentFile,
        desiredName: String,
        tree: DocumentFile,
    ): String {
        val current = created.name
        if (current == desiredName) return desiredName
        // Only attempt a rename if the deterministic name isn't now taken by another file.
        val clash = tree.findFile(desiredName)
        if (clash != null && clash.uri != created.uri) {
            return current ?: desiredName
        }
        val renamed = runCatching { created.renameTo(desiredName) }.getOrDefault(false)
        return if (renamed) desiredName else (created.name ?: desiredName)
    }

    private fun openStream(doc: DocumentFile): OutputStream = resolver.openOutputStream(doc.uri)
        ?: throw IOException("Could not open output stream for ${doc.uri}")

    /**
     * A short human-readable label for the chosen tree (spec F6 shows "the chosen folder's
     * human-readable path"). Reuses the already-resolved [tree] rather than re-resolving; falls
     * back to the last path segment of the tree URI if the provider exposes no display name.
     */
    private fun humanTreePath(tree: DocumentFile): String {
        val treeName = runCatching { tree.name }.getOrNull()
        if (!treeName.isNullOrBlank()) return treeName
        return treeUri.lastPathSegment ?: treeUri.toString()
    }

    private companion object {
        const val MIME_MP3 = "audio/mpeg"
    }
}

/** [OutputHandle] wrapping the created [document]. Opaque to callers. */
private class SafHandle(
    val document: DocumentFile,
) : OutputHandle
