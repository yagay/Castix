package me.elhizazi.Castix.xposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import me.elhizazi.Castix.privilege.domain.repository.TargetAppsRepository

/**
 * Read-only hook policy endpoint. The target app can only query its own policy:
 * packages from arbitrary calling UIDs cannot enumerate the user's selected list.
 * A real LSPosed hook must still be enabled in the LSPosed Manager scope.
 */
class HookPolicyProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val reply = Bundle().apply { putBoolean("enabled", false) }
        if (method != "isAudioFocusGuardEnabled") return reply
        val ctx = context ?: return reply
        val packageName = arg ?: return reply
        if (packageName.isBlank()) return reply

        val uid = Binder.getCallingUid()
        val callerOwnsPackage = uid == Process.myUid() ||
            ctx.packageManager.getPackagesForUid(uid)?.contains(packageName) == true
        if (!callerOwnsPackage) return reply

        val policy = TargetAppsRepository(ctx)
        reply.putBoolean(
            "enabled",
            policy.isAudioFocusGuardEnabled() &&
                policy.getSelectedPackages().contains(packageName)
        )
        return reply
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}
