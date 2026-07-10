package io.github.entewurzelauskuh.mp4tomp3.ui.options

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for the pre-conversion options screen (issue #5) and its bitrate control (issue
 * #6) — the reference example contributors copy for #7/#8.
 */
class ConversionOptionsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Test
    fun showsBitrateChoicesAndStartAction() {
        compose.setContent { ConversionOptionsScreen(fileCount = 2, onStart = {}, onCancel = {}) }

        compose.onNodeWithText("128").assertIsDisplayed()
        compose.onNodeWithText("320").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_start_conversion)).assertIsDisplayed()
    }

    @Test
    fun startReportsTheChosenBitrate() {
        var chosen: ConversionOptions? = null
        compose.setContent { ConversionOptionsScreen(fileCount = 1, onStart = { chosen = it }, onCancel = {}) }

        compose.onNodeWithText("320").performClick()
        compose.onNodeWithText(str(R.string.action_start_conversion)).performClick()

        assertEquals(320, chosen?.bitrateKbps)
    }

    @Test
    fun startWithoutChangesUsesTheDefaultBitrate() {
        var chosen: ConversionOptions? = null
        compose.setContent { ConversionOptionsScreen(fileCount = 1, onStart = { chosen = it }, onCancel = {}) }

        compose.onNodeWithText(str(R.string.action_start_conversion)).performClick()

        assertEquals(ConversionOptions.DEFAULT_BITRATE_KBPS, chosen?.bitrateKbps)
    }

    @Test
    fun toolbarBackCancelsWithoutStarting() {
        var cancelled = false
        var started = false
        compose.setContent {
            ConversionOptionsScreen(fileCount = 1, onStart = { started = true }, onCancel = { cancelled = true })
        }

        compose.onNodeWithContentDescription(str(R.string.cd_back)).performClick()

        assertTrue(cancelled)
        assertTrue(!started)
    }
}
