package me.elhizazi.Castix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.elhizazi.Castix.privilege.domain.engine.AccessibilityEngine
import me.elhizazi.Castix.privilege.domain.engine.DhizukuEngine
import me.elhizazi.Castix.privilege.domain.engine.LSPatchEngine
import me.elhizazi.Castix.privilege.domain.engine.LSPosedEngine
import me.elhizazi.Castix.privilege.domain.engine.RootEngine
import me.elhizazi.Castix.privilege.domain.model.PlaybackOptimizationTask
import me.elhizazi.Castix.privilege.domain.repository.TargetAppsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract checks: unsupported backend paths must not return a fake success.
 * No root, LSPosed or device-policy privileges are needed for these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackendSafetyTest {
    @Test
    fun unsupportedPlaybackTasksNeverReportSuccess() = runBlocking {
        val target = "com.example.player"
        val task = PlaybackOptimizationTask.AUDIO_FOCUS_HIJACK
        val engines = listOf(
            RootEngine(), DhizukuEngine(), LSPosedEngine(),
            LSPatchEngine(), AccessibilityEngine()
        )
        for (engine in engines) {
            val result = engine.executePlaybackTask(task, target)
            assertFalse("${engine.displayName} reported fake success", result.isSuccess)
            assertTrue(result.stderr.isNotBlank())
        }
    }

    @Test
    fun invalidRootTargetIsRejectedBeforeExecutingShell() = runBlocking {
        val result = RootEngine().executePlaybackTask(
            PlaybackOptimizationTask.BATTERY_WHITELIST, "com.example.app;reboot"
        )
        assertFalse(result.isSuccess)
        assertEquals(2, result.exitCode)
    }

    @Test
    fun experimentalAudioFocusPolicyDefaultsOffAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TargetAppsRepository(context)
        repo.setAudioFocusGuardEnabled(false)
        assertFalse(repo.isAudioFocusGuardEnabled())
        repo.setAudioFocusGuardEnabled(true)
        assertTrue(TargetAppsRepository(context).isAudioFocusGuardEnabled())
        repo.setAudioFocusGuardEnabled(false)
    }
}
