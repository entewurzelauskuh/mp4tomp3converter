package io.github.entewurzelauskuh.mp4tomp3.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemorySettingsRepositoryTest {
    @Test
    fun defaultsToOutputTargetDefault() = runTest {
        val repo = InMemorySettingsRepository()
        assertEquals(OutputTarget.Default, repo.outputTarget.first())
    }

    @Test
    fun setOutputTargetUpdatesTheFlow() = runTest {
        val repo = InMemorySettingsRepository()
        val safTree = OutputTarget.SafTree("content://tree/x")

        repo.setOutputTarget(safTree)
        assertEquals(safTree, repo.outputTarget.first())

        repo.setOutputTarget(OutputTarget.Default)
        assertEquals(OutputTarget.Default, repo.outputTarget.first())
    }
}
