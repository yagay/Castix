package me.elhizazi.Castix.privilege.domain.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import me.elhizazi.Castix.privilege.domain.model.EngineHealthStatus
import me.elhizazi.Castix.privilege.domain.model.EngineType
import me.elhizazi.Castix.privilege.domain.model.ExecutionResult
import me.elhizazi.Castix.privilege.domain.model.PlaybackOptimizationTask
import me.elhizazi.Castix.privilege.domain.model.ServiceHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LSPatch Engine (Rootless Xposed / Sandboxed In-App Dex Hooking):
 * Enables non-rooted users to use Xposed module hooks directly within target applications
 * by embedding a dex loader (such as Shizuku/LSPatch loader or patched APKs).
 */
class LSPatchEngine : IPrivilegeEngine {

    override val engineType: EngineType = EngineType.LSPATCH
    override val displayName: String = "LSPatch (Rootless)"
    override val description: String = "Rootless Xposed engine injecting audio bypass hooks directly into target APKs without root."

    companion object {
        const val LSPATCH_PACKAGE = "org.lsposed.lspatch"

        @Volatile
        var isModulePatchedActive: Boolean = false
    }

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val managerFound = runCatching { pm.getPackageInfo(LSPATCH_PACKAGE, 0) }.isSuccess

        val loaderFound = runCatching {
            Class.forName("org.lsposed.lspatch.Loader")
            true
        }.getOrDefault(false)

        val xposedBridgeFound = runCatching {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        }.getOrDefault(false)

        // Check if installed or running in a sandboxed patched container
        managerFound || loaderFound || xposedBridgeFound
    }

    override suspend fun checkPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        isModulePatchedActive
    }

    override suspend fun requestPermission(context: Context): Result<Boolean> = withContext(Dispatchers.Main) {
        runCatching {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(LSPATCH_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            isModulePatchedActive
        }
    }

    override suspend fun executeCommand(command: String): ExecutionResult = ExecutionResult(
        command = command,
        exitCode = 3,
        stdout = "",
        stderr = "No APK patcher or active runtime hook is installed by Castix.",
        executionTimeMs = 0L,
        engineUsed = EngineType.LSPATCH
    )

    override suspend fun executePlaybackTask(
        task: PlaybackOptimizationTask,
        targetPackage: String
    ): ExecutionResult = ExecutionResult(
        command = "playback::${task.name} --pkg=$targetPackage",
        exitCode = 3,
        stdout = "",
        stderr = "No APK patcher or active runtime hook is installed by Castix.",
        executionTimeMs = 0L,
        engineUsed = EngineType.LSPATCH
    )

    override suspend fun getHealth(context: Context): ServiceHealth = withContext(Dispatchers.IO) {
        val available = isAvailable(context)
        val permGranted = checkPermission(context)

        val status = when {
            isModulePatchedActive -> EngineHealthStatus.ACTIVE
            permGranted -> EngineHealthStatus.AVAILABLE
            available -> EngineHealthStatus.PERMISSION_REQUIRED
            else -> EngineHealthStatus.NOT_INSTALLED
        }

        val isActuallyUsable = isModulePatchedActive || permGranted

        ServiceHealth(
            type = engineType,
            status = status,
            message = if (isModulePatchedActive) "موديول LSPatch نشط ومحقون" else if (available) "LSPatch متاح (بانتظار التهيئة)" else "LSPatch غير متوفر",
            version = if (available) "0.6 (Rootless)" else null,
            isUsable = isActuallyUsable,
            details = if (available) {
                "LSPatch manager or patched container active. In-process ART hooks supported without root."
            } else {
                "LSPatch not detected. Install LSPatch manager or run within an LSPatch-patched APK."
            }
        )
    }
}
