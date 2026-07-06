package io.github.entewurzelauskuh.mp4tomp3.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-persistent [SettingsRepository] backed by an in-memory [MutableStateFlow].
 *
 * A real (if forgetful) implementation — not a test-only mock — so it is fine in `main`:
 * it drives Compose previews and unit tests, and could serve as a fallback if DataStore
 * were unavailable. Production uses [DataStoreSettingsRepository].
 */
class InMemorySettingsRepository(
    initial: OutputTarget = OutputTarget.Default,
) : SettingsRepository {
    private val _outputTarget = MutableStateFlow(initial)
    override val outputTarget: Flow<OutputTarget> = _outputTarget.asStateFlow()

    override suspend fun setOutputTarget(target: OutputTarget) {
        _outputTarget.value = target
    }
}
