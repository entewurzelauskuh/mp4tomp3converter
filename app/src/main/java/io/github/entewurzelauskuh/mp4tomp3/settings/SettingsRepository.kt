package io.github.entewurzelauskuh.mp4tomp3.settings

import kotlinx.coroutines.flow.Flow

/**
 * Persisted user settings (spec F6). v1 exposes exactly one setting: the output target.
 *
 * Two implementations: [DataStoreSettingsRepository] (production, DataStore-backed) and
 * [InMemorySettingsRepository] (non-persistent — used by tests and Compose previews).
 */
interface SettingsRepository {
    /** The current output target, emitting on every change. */
    val outputTarget: Flow<OutputTarget>

    /** Persist a new output target ([OutputTarget.Default] resets to the Music folder). */
    suspend fun setOutputTarget(target: OutputTarget)
}
