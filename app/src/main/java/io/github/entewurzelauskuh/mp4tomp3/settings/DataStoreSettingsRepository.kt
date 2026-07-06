package io.github.entewurzelauskuh.mp4tomp3.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Process-wide DataStore instance backing settings persistence. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Production [SettingsRepository] persisting to DataStore Preferences (spec §3, §6.6).
 * The (de)serialisation is delegated to the pure [OutputTargetSerialization] so the
 * mapping stays unit-tested; this class only does the DataStore plumbing.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val outputTarget: Flow<OutputTarget> =
        dataStore.data.map { prefs -> OutputTargetSerialization.decode(prefs[OUTPUT_TREE_URI]) }

    override suspend fun setOutputTarget(target: OutputTarget) {
        dataStore.edit { prefs ->
            when (val encoded = OutputTargetSerialization.encode(target)) {
                null -> prefs.remove(OUTPUT_TREE_URI)
                else -> prefs[OUTPUT_TREE_URI] = encoded
            }
        }
    }

    companion object {
        private val OUTPUT_TREE_URI = stringPreferencesKey("output_tree_uri")

        /** Build one bound to the app-wide DataStore for [context]. */
        fun from(context: Context): DataStoreSettingsRepository = DataStoreSettingsRepository(context.applicationContext.settingsDataStore)
    }
}
