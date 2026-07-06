package io.github.entewurzelauskuh.mp4tomp3.jobs

import android.content.Context
import android.net.Uri
import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.engine.ConverterResult
import io.github.entewurzelauskuh.mp4tomp3.output.OpenOutput
import io.github.entewurzelauskuh.mp4tomp3.output.OutputFolderUnavailableException
import io.github.entewurzelauskuh.mp4tomp3.output.OutputHandle
import io.github.entewurzelauskuh.mp4tomp3.output.OutputSink
import io.github.entewurzelauskuh.mp4tomp3.settings.InMemorySettingsRepository
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream

class QueueDrainerTest {
    private val context: Context = mock(Context::class.java)
    private var idCounter = 0
    private fun repo() = JobRepository(
        resolveDisplayName = { "clip.mp4" },
        now = { 0L },
        newId = { "id-${idCounter++}" },
    )

    private fun uri(): Uri = mock(Uri::class.java)

    /** Records lifecycle calls; open() returns a throwaway stream. */
    private class RecordingSink : OutputSink {
        val events = mutableListOf<String>()
        override fun open(desiredBaseName: String): OpenOutput {
            events.add("open")
            return OpenOutput(ByteArrayOutputStream(), "Music/$desiredBaseName", object : OutputHandle {})
        }

        override fun finalize(handle: OutputHandle) {
            events.add("finalize")
        }

        override fun abort(handle: OutputHandle) {
            events.add("abort")
        }
    }

    private class FakeConverter(
        private val result: ConverterResult = ConverterResult.Success,
        private val progress: List<Int> = listOf(100),
        private val duringConvert: () -> Unit = {},
    ) : AudioConverter {
        override fun convert(
            context: Context,
            sourceUri: Uri,
            output: java.io.OutputStream,
            onProgress: (percent: Int) -> Unit,
            isCancelled: () -> Boolean,
        ): ConverterResult {
            progress.forEach(onProgress)
            duringConvert()
            return result
        }
    }

    @Test
    fun drainsAllQueuedJobsSequentiallyToDone() = runTest {
        val repo = repo()
        val sink = RecordingSink()
        repo.enqueue(listOf(uri(), uri(), uri()))

        QueueDrainer(repo, FakeConverter(), { sink }, InMemorySettingsRepository(), context).drainQueue()

        assertTrue(repo.jobs.value.all { it.state is JobState.Done })
        // Each job: open -> finalize (no aborts on the success path).
        assertEquals(listOf("open", "finalize", "open", "finalize", "open", "finalize"), sink.events)
    }

    @Test
    fun failureAbortsAndMarksFailed() = runTest {
        val repo = repo()
        val sink = RecordingSink()
        val id = repo.enqueue(listOf(uri())).single()

        QueueDrainer(
            repo,
            FakeConverter(result = ConverterResult.Failure(FailureReason.UnsupportedAudioCodec)),
            { sink },
            InMemorySettingsRepository(),
            context,
        ).drainQueue()

        assertEquals(JobState.Failed(FailureReason.UnsupportedAudioCodec), repo.jobs.value.single { it.id == id }.state)
        assertEquals(listOf("open", "abort"), sink.events)
    }

    @Test
    fun cancellationDuringConversionAbortsAndMarksCancelled() = runTest {
        val repo = repo()
        val sink = RecordingSink()
        val id = repo.enqueue(listOf(uri())).single()

        // Cancel the running job from inside convert(): the drainer sees the request afterward.
        val converter = FakeConverter(duringConvert = { repo.cancel(id) })
        QueueDrainer(repo, converter, { sink }, InMemorySettingsRepository(), context).drainQueue()

        assertEquals(JobState.Cancelled, repo.jobs.value.single { it.id == id }.state)
        assertEquals(listOf("open", "abort"), sink.events)
    }

    @Test
    fun converterThrowingFailsTheJobAndAbortsInsteadOfStayingRunning() = runTest {
        val repo = repo()
        val sink = RecordingSink()
        val id = repo.enqueue(listOf(uri())).single()
        val throwing = object : AudioConverter {
            override fun convert(
                context: Context,
                sourceUri: Uri,
                output: java.io.OutputStream,
                onProgress: (percent: Int) -> Unit,
                isCancelled: () -> Boolean,
            ): ConverterResult = throw RuntimeException("boom")
        }

        QueueDrainer(repo, throwing, { sink }, InMemorySettingsRepository(), context).drainQueue()

        // Must not be left Running; the partial output is aborted.
        assertTrue(repo.jobs.value.single { it.id == id }.state is JobState.Failed)
        assertEquals(listOf("open", "abort"), sink.events)
    }

    @Test
    fun missingOutputFolderFailsWithOutputFolderUnavailable() = runTest {
        val repo = repo()
        val id = repo.enqueue(listOf(uri())).single()
        val unavailableSink = object : OutputSink {
            override fun open(desiredBaseName: String): OpenOutput = throw OutputFolderUnavailableException("gone")

            override fun finalize(handle: OutputHandle) = Unit
            override fun abort(handle: OutputHandle) = Unit
        }

        QueueDrainer(repo, FakeConverter(), { unavailableSink }, InMemorySettingsRepository(), context).drainQueue()

        assertEquals(JobState.Failed(FailureReason.OutputFolderUnavailable), repo.jobs.value.single { it.id == id }.state)
    }

    @Test
    fun sinkIsChosenForTheCurrentOutputTarget() = runTest {
        val repo = repo()
        repo.enqueue(listOf(uri()))
        val settings = InMemorySettingsRepository(OutputTarget.SafTree("content://tree/x"))
        var seenTarget: OutputTarget? = null

        QueueDrainer(
            repo,
            FakeConverter(),
            sinkProvider = { target ->
                seenTarget = target
                RecordingSink()
            },
            settings = settings,
            context = context,
        ).drainQueue()

        assertEquals(OutputTarget.SafTree("content://tree/x"), seenTarget)
    }
}
