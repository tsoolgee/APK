package com.overlaymanager.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

/**
 * Equivalent of "auto-start with Windows". Only relaunches the service if
 * the user previously enabled it AND the overlay permission is still granted -
 * otherwise the service would immediately fail to draw anything.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs: SharedPreferences = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(Prefs.KEY_AUTO_START, false)
        if (!autoStart) return

        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if (canDraw) {
            OverlayService.start(context)
        }
    }
}

object Prefs {
    const val NAME = "overlay_manager_prefs"
    const val KEY_AUTO_START = "auto_start_enabled"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_OWNER_NAME = "owner_name"
    const val KEY_OWNER_PHONE = "owner_phone"
    // true = user explicitly chose "continue without a PIN"; LockActivity
    // skips straight to MainActivity while this is set, even if a PIN hash
    // exists from a previous setup.
    const val KEY_LOCK_DISABLED = "lock_disabled"
    // false = user chose to hide the persistent "overlay running" notification.
    // Default true because Android requires it for a *foreground* service -
    // turning it off means the service runs as a plain background service
    // instead, which trades away the survive-after-swipe-away guarantee.
    const val KEY_SHOW_NOTIFICATION = "show_notification"
}
