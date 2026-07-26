package io.github.entewurzelauskuh.mp4tomp3.ui.options

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.Id3Tags
import io.github.entewurzelauskuh.mp4tomp3.jobs.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for the pre-conversion options screen and its bitrate control — the reference
 * example contributors copy for further per-conversion controls.
 */
class ConversionOptionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Test
    fun showsBitrateChoicesAndStartAction() {
        compose.setContent { ConversionOptionsScreen(fileCount = 2, sourceInfo = null, onStart = {}, onCancel = {}) }

        compose.onNodeWithText("128").assertIsDisplayed()
        compose.onNodeWithText("320").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_start_conversion)).assertIsDisplayed()
    }

    @Test
    fun startReportsTheChosenBitrate() {
        var chosen: ConversionOptions? = null
        compose.setContent { ConversionOptionsScreen(fileCount = 1, sourceInfo = null, onStart = { chosen = it }, onCancel = {}) }

        compose.onNodeWithText("320").performClick()
        compose.onNodeWithText(str(R.string.action_start_conversion)).performClick()

        assertEquals(320, chosen?.bitrateKbps)
    }

    @Test
    fun startWithoutChangesUsesTheDefaultBitrate() {
        var chosen: ConversionOptions? = null
        compose.setContent { ConversionOptionsScreen(fileCount = 1, sourceInfo = null, onStart = { chosen = it }, onCancel = {}) }

        compose.onNodeWithText(str(R.string.action_start_conversion)).performClick()

        assertEquals(ConversionOptions.DEFAULT_BITRATE_KBPS, chosen?.bitrateKbps)
    }

    @Test
    fun toolbarBackCancelsWithoutStarting() {
        var cancelled = false
        var started = false
        compose.setContent {
            ConversionOptionsScreen(fileCount = 1, sourceInfo = null, onStart = { started = true }, onCancel = { cancelled = true })
        }

        compose.onNodeWithContentDescription(str(R.string.cd_back)).performClick()

        assertTrue(cancelled)
        assertTrue(!started)
    }

    @Test
    fun singleFileShowsPerFileControls() {
        compose.setContent {
            ConversionOptionsScreen(
                fileCount = 1,
                sourceInfo = SourceInfo(durationMs = 10_000L, suggestedTags = Id3Tags(title = "My Clip")),
                onStart = {},
                onCancel = {},
            )
        }
        compose.onNodeWithText(str(R.string.option_trim_title)).assertIsDisplayed() // trim
        compose.onNodeWithText(str(R.string.option_tags_title)).performScrollTo().assertIsDisplayed() // tags
        compose.onNodeWithText(str(R.string.option_delete_title)).performScrollTo().assertIsDisplayed() // delete

        // Tags collapse behind the "Edit tags" row; opening it reveals the pre-filled fields.
        compose.onNodeWithText("My Clip").assertDoesNotExist()
        compose.onNodeWithText(str(R.string.option_tags_edit_toggle)).performScrollTo().performClick()
        compose.onNodeWithText("My Clip").performScrollTo().assertIsDisplayed() // title pre-filled from the source
    }

    @Test
    fun singleFileStartReportsTagsAndFullTrim() {
        var chosen: ConversionOptions? = null
        compose.setContent {
            ConversionOptionsScreen(
                fileCount = 1,
                sourceInfo = SourceInfo(durationMs = 10_000L, suggestedTags = Id3Tags(title = "My Clip")),
                onStart = { chosen = it },
                onCancel = {},
            )
        }
        compose.onNodeWithText(str(R.string.action_start_conversion)).performScrollTo().performClick()

        // Editor left closed → the file's detected tags are still written.
        assertEquals("My Clip", chosen?.tags?.title)
        // Untouched range → full length (the end sentinel), start at 0.
        assertEquals(0L, chosen?.trimStartMs)
        assertEquals(ConversionOptions.TRIM_END_FULL, chosen?.trimEndMs)
    }
}
