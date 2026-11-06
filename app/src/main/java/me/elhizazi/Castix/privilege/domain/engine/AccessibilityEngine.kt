package me.elhizazi.Castix.privilege.domain.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import me.elhizazi.Castix.privilege.domain.model.EngineHealthStatus
import me.elhizazi.Castix.privilege.domain.model.EngineType
import me.elhizazi.Castix.privilege.domain.model.ExecutionResult
import me.elhizazi.Castix.privilege.domain.model.PlaybackOptimizationTask
import me.elhizazi.Castix.privilege.domain.model.ServiceHealth
import me.elhizazi.Castix.service.CastixAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standard Android Accessibility Framework Engine (Zero-Root / Zero-Shizuku):
 * Provides all standard non-rooted users with elevated system process priority,
 * instant foreground window state change detection (for the Floating Button),
 * and immune background persistence directly through Android System Settings.
 */
class AccessibilityEngine : IPrivilegeEngine {

    override val engineType: EngineType = EngineType.ACCESSIBILITY
    override val displayName: String = "إمكانية الوصول (Accessibility)"
    override val description: String = "صلاحية نظام رسمية لا تتطلب روت: تتيح كشف التطبيقات المستهدفة وتأمين استمرار الخدمة في الخلفية."

    override suspend fun isAvailable(context: Context): Boolean = true // Always supported on all Android devices

    override suspend fun checkPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        CastixAccessibilityService.isAccessibilityEnabled(context)
    }

    override suspend fun requestPermission(context: Context): Result<Boolean> = withContext(Dispatchers.Main) {
        runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }
    }

    override suspend fun executeCommand(command: String): ExecutionResult = withContext(Dispatchers.Default) {
        val foregroundQuery = command.contains("foreground", ignoreCase = true) ||
            command.contains("detect", ignoreCase = true)
        if (foregroundQuery && CastixAccessibilityService.isServiceConnected.value) {
            val current = CastixAccessibilityService.currentForegroundPackage.value ?: "Unknown"
            ExecutionResult(command, 0, current, "", 0L, EngineType.ACCESSIBILITY)
        } else {
            ExecutionResult(command, 3, "",
                "Unsupported: Accessibility only reports window events; it cannot execute privileged commands.",
                0L, EngineType.ACCESSIBILITY)
        }
    }

    override suspend fun executePlaybackTask(
        task: PlaybackOptimizationTask,
        targetPackage: String
    ): ExecutionResult = ExecutionResult(
        "playback::${task.name}", 3, "",
        "Unsupported: Accessibility cannot change the playback policy of $targetPackage.",
        0L, EngineType.ACCESSIBILITY
    )

    override suspend fun getHealth(context: Context): ServiceHealth = withContext(Dispatchers.IO) {
        val isEnabled = CastixAccessibilityService.isAccessibilityEnabled(context)
        val isConnected = CastixAccessibilityService.isServiceConnected.value
        val isActuallyUsable = isConnected || isEnabled

        val status = when {
            isConnected -> EngineHealthStatus.ACTIVE
            isEnabled -> EngineHealthStatus.AVAILABLE
            else -> EngineHealthStatus.PERMISSION_REQUIRED
        }

        ServiceHealth(
            type = engineType,
            status = status,
            message = if (isConnected) "خدمة إمكانية الوصول نشطة" else if (isEnabled) "إمكانية الوصول مفعلة" else "بحاجة لتفعيل إمكانية الوصول",
            version = "Android Native API",
            isUsable = isActuallyUsable,
            details = if (isActuallyUsable) {
                "خدمة إمكانية الوصول مفعلة بنجاح. تمنح التطبيق أولوية تشغيل مرتفعة ورصد فوري للتطبيقات بدون روت."
            } else {
                "متاحة لجميع الأجهزة بدون روت. اضغط لتفعيل 'Castix' في إعدادات إمكانية الوصول (Accessibility) بهاتفك."
            }
        )
    }
}
