package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class JobRepositoryTest {
    // Deterministic id/clock so assertions are stable. Each mocked Uri is a distinct
    // opaque object — the repository only stores it, never calls its methods.
    private var counter = 0
    private fun newRepo(resolveName: (Uri) -> String = { "clip.mp4" }) = JobRepository(
        resolveDisplayName = resolveName,
        now = { (counter).toLong() },
        newId = { "id-${counter++}" },
    )

    private fun uri(): Uri = mock(Uri::class.java)

    private fun JobRepository.stateOf(id: String): JobState? = jobs.value.find { it.id == id }?.state

    // --- enqueue ------------------------------------------------------------------------

    @Test
    fun enqueueAddsQueuedJobsInOrderAndReturnsIds() {
        val repo = newRepo()
        val ids = repo.enqueue(listOf(uri(), uri(), uri()))

        assertEquals(3, ids.size)
        assertEquals(ids, repo.jobs.value.map { it.id }) // preserves FIFO order
        assertTrue(repo.jobs.value.all { it.state is JobState.Queued })
    }

    @Test
    fun enqueueEmptyIsNoOp() {
        val repo = newRepo()
        assertEquals(emptyList<String>(), repo.enqueue(emptyList()))
        assertTrue(repo.jobs.value.isEmpty())
    }

    @Test
    fun enqueueResolvesDisplayNameViaInjectedFunction() {
        val repo = newRepo(resolveName = { "Holiday.mp4" })
        val id = repo.enqueue(listOf(uri())).single()
        assertEquals("Holiday.mp4", repo.jobs.value.single { it.id == id }.displayName)
    }

    // --- FIFO draining ------------------------------------------------------------------

    @Test
    fun nextQueuedReturnsOldestAndAdvancesAsJobsComplete() {
        val repo = newRepo()
        val (a, b, c) = repo.enqueue(listOf(uri(), uri(), uri()))

        assertEquals(a, repo.nextQueued()?.id)
        assertTrue(repo.markRunning(a))
        assertEquals(b, repo.nextQueued()?.id) // running job no longer "next"
        repo.markDone(a, "Music/clip.mp3")
        assertEquals(b, repo.nextQueued()?.id)
        repo.markRunning(b)
        repo.markFailed(b, FailureReason.Unknown)
        assertEquals(c, repo.nextQueued()?.id)
    }

    @Test
    fun nextQueuedIsNullWhenNothingWaiting() {
        val repo = newRepo()
        assertNull(repo.nextQueued())
        val id = repo.enqueue(listOf(uri())).single()
        repo.markRunning(id)
        repo.markDone(id, "Music/clip.mp3")
        assertNull(repo.nextQueued())
    }

    // --- cancel: queued vs running ------------------------------------------------------

    @Test
    fun cancelWhileQueuedMarksCancelledImmediatelyAndSkipsIt() {
        val repo = newRepo()
        val (a, b) = repo.enqueue(listOf(uri(), uri()))

        repo.cancel(a)
        assertEquals(JobState.Cancelled, repo.stateOf(a))
        assertFalse(repo.isCancellationRequested(a)) // resolved synchronously, no pending request
        assertEquals(b, repo.nextQueued()?.id) // cancelled job is skipped
    }

    @Test
    fun cancelWhileRunningRequestsStopThenServiceFinalises() {
        val repo = newRepo()
        val id = repo.enqueue(listOf(uri())).single()
        repo.markRunning(id)

        repo.cancel(id)
        // Still Running until the converter observes the request and the service finalises.
        assertTrue(repo.stateOf(id) is JobState.Running)
        assertTrue(repo.isCancellationRequested(id))

        repo.markCancelled(id)
        assertEquals(JobState.Cancelled, repo.stateOf(id))
        assertFalse(repo.isCancellationRequested(id)) // flag cleared on finalisation
    }

    @Test
    fun cancelIsNoOpForTerminalJobs() {
        val repo = newRepo()
        val id = repo.enqueue(listOf(uri())).single()
        repo.markRunning(id)
        repo.markDone(id, "Music/clip.mp3")

        repo.cancel(id)
        assertEquals(JobState.Done("Music/clip.mp3"), repo.stateOf(id))
        assertFalse(repo.isCancellationRequested(id))
    }

    @Test
    fun markRunningFailsIfJobWasCancelledWhileQueued() {
        val repo = newRepo()
        val id = repo.enqueue(listOf(uri())).single()
        repo.cancel(id) // queued -> Cancelled
        assertFalse(repo.markRunning(id)) // cannot start a cancelled job
        assertEquals(JobState.Cancelled, repo.stateOf(id))
    }

    // --- progress -----------------------------------------------------------------------

    @Test
    fun updateProgressOnlyAppliesWhileRunningAndClamps() {
        val repo = newRepo()
        val id = repo.enqueue(listOf(uri())).single()

        repo.updateProgress(id, 42) // still Queued -> ignored
        assertEquals(JobState.Queued, repo.stateOf(id))

        repo.markRunning(id)
        repo.updateProgress(id, 150) // clamped to 100
        assertEquals(JobState.Running(100), repo.stateOf(id))
        repo.updateProgress(id, -5) // clamped to 0
        assertEquals(JobState.Running(0), repo.stateOf(id))

        repo.markDone(id, "Music/clip.mp3")
        repo.updateProgress(id, 50) // terminal -> ignored
        assertEquals(JobState.Done("Music/clip.mp3"), repo.stateOf(id))
    }

    // --- clear --------------------------------------------------------------------------

    @Test
    fun clearRemovesTerminalJobsOnly() {
        val repo = newRepo()
        val (a, b) = repo.enqueue(listOf(uri(), uri()))

        repo.clear(a) // a is Queued -> no-op
        assertEquals(2, repo.jobs.value.size)

        repo.markRunning(a)
        repo.markDone(a, "Music/clip.mp3")
        repo.clear(a) // now terminal -> removed
        assertEquals(listOf(b), repo.jobs.value.map { it.id })

        repo.markRunning(b)
        repo.clear(b) // Running -> no-op
        assertEquals(listOf(b), repo.jobs.value.map { it.id })
    }
}
