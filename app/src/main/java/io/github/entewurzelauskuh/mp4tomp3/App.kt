package io.github.entewurzelauskuh.mp4tomp3

import android.app.Application
import io.github.entewurzelauskuh.mp4tomp3.engine.AudioConverter
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionDependencies
import io.github.entewurzelauskuh.mp4tomp3.jobs.JobRepository
import io.github.entewurzelauskuh.mp4tomp3.output.OutputSink
import io.github.entewurzelauskuh.mp4tomp3.settings.OutputTarget
import io.github.entewurzelauskuh.mp4tomp3.settings.SettingsRepository

/**
 * Application entry point. Owns the [AppContainer] and implements [ConversionDependencies] so
 * [io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionService] (which Android instantiates,
 * hence no constructor injection) can read its collaborators from `application as
 * ConversionDependencies`.
 */
class App :
    Application(),
    ConversionDependencies {
    val container: AppContainer by lazy { AppContainer(this) }

    override val repository: JobRepository get() = container.jobRepository
    override val converter: AudioConverter get() = container.converter
    override val sinkProvider: (OutputTarget) -> OutputSink get() = container.sinkProvider
    override val settings: SettingsRepository get() = container.settingsRepository
}
