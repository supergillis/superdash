package com.superdash.kiosk.boot

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService

/** Android blocks an app in the background from opening its own screen, which is
 *  what start-on-boot and launch-on-wake do. The two exemptions a kiosk can use are
 *  being the default home app or holding "display over other apps". */
object BackgroundLaunch {
    fun isAllowed(context: Context): Boolean = Settings.canDrawOverlays(context) || isHomeApp(context)

    fun openOverlaySettings(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")),
        )
    }

    private fun isHomeApp(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(home, 0)
                ?.activityInfo
                ?.packageName == context.packageName
        }
}
