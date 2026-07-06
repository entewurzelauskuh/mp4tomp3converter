package io.github.entewurzelauskuh.mp4tomp3.ui.main

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private var counter = 0
    private fun repo() = JobRepository(
        resolveDisplayName = { "clip.mp4" },
        now = { 0L },
        newId = { "id-${counter++}" },
    )

    private fun uri() = Uri.parse("content://test/$counter")

    @Test
    fun emptyStateIsShownWhenNoJobs() {
        val vm = MainViewModel(repo(), startConversions = {})
        compose.setContent { MainScreen(viewModel = vm, onOpenSettings = {}) }
        compose.onNodeWithText(str(R.string.empty_hint)).assertIsDisplayed()
    }

    @Test
    fun eachJobStateRendersItsLabel() {
        val repository = repo()

        val queued = repository.enqueue(listOf(uri())).single()

        val running = repository.enqueue(listOf(uri())).single()
        repository.markRunning(running)
        repository.updateProgress(running, 50)

        val done = repository.enqueue(listOf(uri())).single()
        repository.markRunning(done)
        repository.markDone(done, "Music/clip.mp3")

        val failed = repository.enqueue(listOf(uri())).single()
        repository.markRunning(failed)
        repository.markFailed(failed, FailureReason.NoAudioTrack)

        val cancelled = repository.enqueue(listOf(uri())).single()
        repository.markRunning(cancelled)
        repository.markCancelled(cancelled)

        // silence "unused" — ids exist purely to drive state transitions above
        assertTrue(listOf(queued).isNotEmpty())

        val vm = MainViewModel(repository, startConversions = {})
        compose.setContent { MainScreen(viewModel = vm, onOpenSettings = {}) }

        compose.onNodeWithText(str(R.string.state_queued)).assertIsDisplayed()
        compose.onNodeWithText("${str(R.string.state_converting)} 50%").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.state_done, "Music/clip.mp3")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.failure_no_audio_track)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.state_cancelled)).assertIsDisplayed()
    }

    @Test
    fun overflowMenuNavigatesToSettings() {
        var opened = false
        val vm = MainViewModel(repo(), startConversions = {})
        compose.setContent { MainScreen(viewModel = vm, onOpenSettings = { opened = true }) }

        compose.onNodeWithContentDescription(str(R.string.cd_more_options)).performClick()
        compose.onNodeWithText(str(R.string.menu_settings)).performClick()
        assertTrue("tapping Settings should invoke onOpenSettings", opened)
    }
}
