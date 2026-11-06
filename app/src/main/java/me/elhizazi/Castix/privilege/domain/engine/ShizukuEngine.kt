package me.elhizazi.Castix.privilege.domain.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import me.elhizazi.Castix.privilege.domain.model.EngineHealthStatus
import me.elhizazi.Castix.privilege.domain.model.EngineType
import me.elhizazi.Castix.privilege.domain.model.ExecutionResult
import me.elhizazi.Castix.privilege.domain.model.PlaybackOptimizationTask
import me.elhizazi.Castix.privilege.domain.model.ServiceHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import rikka.shizuku.Shizuku

/**
 * Shizuku Privilege Escalation Engine:
 * Communicates with the Shizuku server daemon via Android Binder IPC / ContentProvider protocol.
 * Enables non-root access to hidden APIs and system command execution via ADB-level privileges.
 */
class ShizukuEngine : IPrivilegeEngine {

    override val engineType: EngineType = EngineType.SHIZUKU
    override val displayName: String = "Shizuku (Binder IPC)"
    override val description: String = "Direct Binder IPC via user-authorized Shizuku daemon (UID 2000 shell)."

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_PLUS_PACKAGE = "af.shizuku.plus.api"
        const val SHEVERY_PACKAGE = "com.hamondev.shevery"

        val SHIZUKU_PACKAGES = listOf(
            SHIZUKU_PACKAGE,
            SHIZUKU_PLUS_PACKAGE,
            SHEVERY_PACKAGE
        )

        val SHIZUKU_AUTHORITIES = listOf(
            "moe.shizuku.privileged.api.provider",
            "af.shizuku.plus.api.provider",
            "com.hamondev.shevery.provider"
        )

        const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
        const val SHIZUKU_REQUEST_CODE = 1001

        fun getInstalledManagerName(context: Context): String? {
            val pm = context.packageManager
            if (isPackageInstalled(pm, SHEVERY_PACKAGE)) return "Shevery"
            if (isPackageInstalled(pm, SHIZUKU_PLUS_PACKAGE)) return "Shizuku+"
            if (isPackageInstalled(pm, SHIZUKU_PACKAGE)) return "Shizuku"
            return null
        }

        fun getInstalledManagerPackage(context: Context): String? {
            val pm = context.packageManager
            if (isPackageInstalled(pm, SHEVERY_PACKAGE)) return SHEVERY_PACKAGE
            if (isPackageInstalled(pm, SHIZUKU_PLUS_PACKAGE)) return SHIZUKU_PLUS_PACKAGE
            if (isPackageInstalled(pm, SHIZUKU_PACKAGE)) return SHIZUKU_PACKAGE
            return null
        }

        private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                true
            }.getOrDefault(false)
        }
    }

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (binderAlive) return@withContext true

        val pm = context.packageManager
        val anyPackageInstalled = SHIZUKU_PACKAGES.any { pkg -> isPackageInstalled(pm, pkg) }
        val anyProviderResolved = SHIZUKU_AUTHORITIES.any { auth ->
            runCatching { pm.resolveContentProvider(auth, 0) != null }.getOrDefault(false)
        }

        anyPackageInstalled || anyProviderResolved
    }

    override suspend fun checkPermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        val ping = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!ping) return@withContext false
        val perm = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        perm
    }

    override suspend fun requestPermission(context: Context): Result<Boolean> = withContext(Dispatchers.Main) {
        runCatching {
            val ping = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            if (ping) {
                val granted = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
                if (granted) {
                    // Already granted, nothing more to request
                    return@runCatching true
                }
                if (Shizuku.isPreV11()) {
                    val pm = context.packageManager
                    for (pkg in SHIZUKU_PACKAGES) {
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return@runCatching true
                        }
                    }
                } else {
                    // Triggers the official native Shizuku / Shevery / Shizuku+ system permission dialog!
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
                return@runCatching true
            }

            // If Shizuku Binder is not active yet, try triggering request in case it's in progress
            val directAttempt = runCatching {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                true
            }
            if (directAttempt.isSuccess) {
                return@runCatching true
            }

            // Open the installed Shizuku/Shevery/Shizuku+ application on device so user can start the service
            val pm = context.packageManager
            for (pkg in SHIZUKU_PACKAGES) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        setPackage(pkg)
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }

                if (launchIntent.resolveActivity(pm) != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val name = getInstalledManagerName(context) ?: "Shizuku"
                    throw IllegalStateException("تم فتح تطبيق $name. يرجى بدء الخدمة (عبر تصحيح الأخطاء اللاسلكي أو الروت) ثم العودة لمنح الإذن.")
                }
            }

            throw IllegalStateException("لم يتم العثور على تطبيق Shizuku أو Shevery أو Shizuku+ على هذا الجهاز. يرجى تثبيت أحدهم.")
        }
    }

    /**
     * Execute only through the authorized Shizuku remote process.
     * Never pretend a normal app-shell command was dispatched through Binder.
     * Shizuku.newProcess is a legacy compatibility path; on unsupported versions
     * report failure rather than executing with the wrong identity.
     */
    @Suppress("DEPRECATION")
    override suspend fun executeCommand(command: String): ExecutionResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val cmd = command.trim()
        if (cmd.isEmpty()) {
            return@withContext ExecutionResult(cmd, 2, "", "Empty command", 0L, EngineType.SHIZUKU)
        }
        if (!runCatching { Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
            return@withContext ExecutionResult(cmd, 1, "", "Shizuku is not connected or authorized", 0L, EngineType.SHIZUKU)
        }
        var process: Process? = null
        try {
            val current = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                .also { process = it }
            val stdout = java.io.ByteArrayOutputStream()
            val stderr = java.io.ByteArrayOutputStream()
            val outReader = Thread({
                try { current.inputStream.use { it.copyTo(stdout) } } catch (_: Exception) {}
            }, "Castix-shizuku-stdout").apply { isDaemon = true; start() }
            val errReader = Thread({
                try { current.errorStream.use { it.copyTo(stderr) } } catch (_: Exception) {}
            }, "Castix-shizuku-stderr").apply { isDaemon = true; start() }
            val completed = current.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) current.destroyForcibly()
            outReader.join(1500)
            errReader.join(1500)
            ExecutionResult(
                command = cmd,
                exitCode = if (completed) current.exitValue() else -1,
                stdout = stdout.toString("UTF-8").trim(),
                stderr = if (completed) stderr.toString("UTF-8").trim() else "Shizuku command timed out after 20 seconds",
                executionTimeMs = SystemClock.elapsedRealtime() - started,
                engineUsed = EngineType.SHIZUKU
            )
        } catch (e: Exception) {
            process?.destroyForcibly()
            ExecutionResult(
                command = cmd,
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku remote execution is unavailable: ${e.localizedMessage}",
                executionTimeMs = SystemClock.elapsedRealtime() - started,
                engineUsed = EngineType.SHIZUKU
            )
        }
    }

    override suspend fun executePlaybackTask(
        task: PlaybackOptimizationTask,
        targetPackage: String
    ): ExecutionResult {
        if (!targetPackage.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"))) {
            return ExecutionResult("", 2, "", "Invalid target package", 0L, EngineType.SHIZUKU)
        }
        val cmd = when (task) {
            PlaybackOptimizationTask.BATTERY_WHITELIST ->
                "dumpsys deviceidle whitelist +$targetPackage"
            PlaybackOptimizationTask.STANDBY_BUCKET_ACTIVE ->
                "am set-standby-bucket $targetPackage active"
            PlaybackOptimizationTask.APP_OPS_BACKGROUND ->
                "cmd appops set $targetPackage RUN_IN_BACKGROUND allow && cmd appops set $targetPackage WAKE_LOCK allow"
            PlaybackOptimizationTask.PHANTOM_KILLER_DISABLE, PlaybackOptimizationTask.AUDIO_FOCUS_HIJACK -> null
        }
        return if (cmd != null) executeCommand(cmd) else ExecutionResult(
            "playback::${task.name}", 3, "",
            "Unsupported: ${task.name} has no safe Shizuku implementation.",
            0L, EngineType.SHIZUKU
        )
    }

    override suspend fun getHealth(context: Context): ServiceHealth = withContext(Dispatchers.IO) {
        val isInstalled = isAvailable(context)
        val isPingAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val isAuthorized = if (isPingAlive) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        } else false

        when {
            isPingAlive && isAuthorized -> {
                ServiceHealth(
                    type = EngineType.SHIZUKU,
                    status = EngineHealthStatus.ACTIVE,
                    message = "خدمة Shizuku نشطة ومصرّحة",
                    version = "v13.5+ (ADB Binder)",
                    isUsable = true,
                    details = "تم الاتصال بخدمة Shizuku بنجاح وصلاحيات ADB كاملة ومتاحة."
                )
            }
            isPingAlive && !isAuthorized -> {
                ServiceHealth(
                    type = EngineType.SHIZUKU,
                    status = EngineHealthStatus.PERMISSION_REQUIRED,
                    message = "خدمة Shizuku قيد التشغيل • بانتظار الإذن",
                    version = "v13.5+",
                    isUsable = false,
                    details = "خدمة Shizuku تعمل في الخلفية؛ اضغط لمنح الإذن للتطبيق."
                )
            }
            isInstalled -> {
                ServiceHealth(
                    type = EngineType.SHIZUKU,
                    status = EngineHealthStatus.INSTALLED_INACTIVE,
                    message = "تطبيق Shizuku مثبت ولكن الخدمة متوقفة",
                    version = null,
                    isUsable = false,
                    details = "افتح Shizuku وشغّل الخدمة عبر تصحيح الأخطاء اللاسلكي أو الروت."
                )
            }
            else -> {
                ServiceHealth(
                    type = EngineType.SHIZUKU,
                    status = EngineHealthStatus.NOT_INSTALLED,
                    message = "Shizuku غير مثبت على هذا الجهاز",
                    version = null,
                    isUsable = false,
                    details = "ثبّت تطبيق Shizuku أو Shevery لاستخدامه."
                )
            }
        }
    }
}
