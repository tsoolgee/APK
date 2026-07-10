package com.overlaymanager.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Anti-theft deterrent only - NOT real uninstall protection.
 *
 * Android lets any user disable a normal app's device-admin rights from
 * system Settings at any time; a regular (non-Device-Owner) app cannot put
 * its own password screen in front of that system flow. What this class
 * *can* do, and does:
 *  - onDisableRequested(): shown as a warning inside Android's own
 *    "Deactivate this device admin app?" confirmation dialog, and
 *    immediately locks the screen (dpm.lockNow()) as a deterrent while
 *    admin rights are still active.
 *  - onDisabled(): fires after admin rights are actually removed - too late
 *    to block anything, but still logged/toasted for visibility.
 */
class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, R.string.admin_enabled_toast, Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
        } catch (e: Exception) {
            // Best-effort only.
        }
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, R.string.admin_disabled_toast, Toast.LENGTH_LONG).show()
    }
}
