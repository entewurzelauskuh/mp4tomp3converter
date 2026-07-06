package io.github.entewurzelauskuh.mp4tomp3.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.entewurzelauskuh.mp4tomp3.R
import io.github.entewurzelauskuh.mp4tomp3.settings.InMemorySettingsRepository
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Test
    fun showsMusicDefaultAndChooseFolder() {
        val vm = SettingsViewModel(InMemorySettingsRepository())
        compose.setContent { SettingsScreen(viewModel = vm, onBack = {}) }

        compose.onNodeWithText(str(R.string.settings_output_music_default)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_choose_folder)).assertIsDisplayed()
    }
}
