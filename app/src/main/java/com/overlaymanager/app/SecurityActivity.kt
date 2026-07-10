package com.overlaymanager.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * One screen for every "protect the device / protect this app" option:
 * owner stamp shown on the lock screen, changing the app PIN, the
 * device-admin anti-theft deterrent (see AppDeviceAdminReceiver for its real
 * limits), and exempting the app from battery optimization so the overlay
 * service survives being swiped away from Recents.
 */
class SecurityActivity : Activity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var adminStatusView: TextView
    private lateinit var toggleAdminButton: Button
    private lateinit var pinStatusView: TextView
    private lateinit var togglePinButton: Button
    private lateinit var notificationStatusView: TextView
    private lateinit var toggleNotificationButton: Button

    companion object {
        private const val REQUEST_ENABLE_ADMIN = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)
        title = getString(R.string.security_title)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val nameField = findViewById<EditText>(R.id.fieldOwnerName)
        val phoneField = findViewById<EditText>(R.id.fieldOwnerPhone)
        nameField.setText(prefs.getString(Prefs.KEY_OWNER_NAME, ""))
        phoneField.setText(prefs.getString(Prefs.KEY_OWNER_PHONE, ""))

        findViewById<Button>(R.id.buttonSaveOwnerInfo).setOnClickListener {
            prefs.edit()
                .putString(Prefs.KEY_OWNER_NAME, nameField.text.toString().trim())
                .putString(Prefs.KEY_OWNER_PHONE, phoneField.text.toString().trim())
                .apply()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonChangePin).setOnClickListener {
            startActivity(Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_FORCE_SETUP, true))
        }

        pinStatusView = findViewById(R.id.textPinStatus)
        togglePinButton = findViewById(R.id.buttonTogglePinLock)
        togglePinButton.setOnClickListener { togglePinLock(prefs) }
        refreshPinStatus(prefs)

        adminStatusView = findViewById(R.id.textAdminStatus)
        toggleAdminButton = findViewById(R.id.buttonToggleAdmin)
        toggleAdminButton.setOnClickListener { toggleAdmin() }
        refreshAdminStatus()

        findViewById<Button>(R.id.buttonBatteryExemption).setOnClickListener {
            requestBatteryExemption()
        }

        notificationStatusView = findViewById(R.id.textNotificationStatus)
        toggleNotificationButton = findViewById(R.id.buttonToggleNotification)
        toggleNotificationButton.setOnClickListener { toggleNotification(prefs) }
        refreshNotificationStatus(prefs)
    }

    override fun onResume() {
        super.onResume()
        refreshAdminStatus()
        refreshPinStatus(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
        refreshNotificationStatus(getSharedPreferences(Prefs.NAME, MODE_PRIVATE))
    }

    /**
     * "PIN protection" is considered off either if no PIN was ever set or if
     * the user explicitly skipped it on the lock screen (Prefs.KEY_LOCK_DISABLED).
     */
    private fun refreshPinStatus(prefs: android.content.SharedPreferences) {
        val hasPin = prefs.getString(Prefs.KEY_PIN_HASH, null) != null
        val disabled = prefs.getBoolean(Prefs.KEY_LOCK_DISABLED, false)
        val active = hasPin && !disabled
        pinStatusView.setText(if (active) R.string.pin_status_on else R.string.pin_status_off)
        togglePinButton.setText(if (active) R.string.disable_pin_lock else R.string.enable_pin_lock)
    }

    private fun togglePinLock(prefs: android.content.SharedPreferences) {
        val hasPin = prefs.getString(Prefs.KEY_PIN_HASH, null) != null
        val disabled = prefs.getBoolean(Prefs.KEY_LOCK_DISABLED, false)
        val active = hasPin && !disabled
        if (active) {
            // Turn protection off without discarding the PIN itself, so
            // re-enabling later (below) doesn't force choosing a brand new one.
            prefs.edit().putBoolean(Prefs.KEY_LOCK_DISABLED, true).apply()
            refreshPinStatus(prefs)
            Toast.makeText(this, R.string.pin_disabled_toast, Toast.LENGTH_LONG).show()
        } else {
            // Send them through the normal setup screen so they pick a PIN
            // (or a new one, if one existed but they'd forgotten it).
            startActivity(Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_FORCE_SETUP, true))
        }
    }

    /**
     * Toggling this restarts OverlayService (if it's currently running) so the
     * change takes effect immediately instead of waiting for the next manual
     * start. Turning the notification off switches the service from a
     * foreground service to a plain background one - Android is then free to
     * stop it sooner while the app isn't in the foreground; that tradeoff is
     * spelled out in battery_section_desc-style text in the layout.
     */
    private fun refreshNotificationStatus(prefs: android.content.SharedPreferences) {
        val shown = prefs.getBoolean(Prefs.KEY_SHOW_NOTIFICATION, true)
        notificationStatusView.setText(if (shown) R.string.notification_status_on else R.string.notification_status_off)
        toggleNotificationButton.setText(if (shown) R.string.hide_notification else R.string.show_notification_action)
    }

    private fun toggleNotification(prefs: android.content.SharedPreferences) {
        val shown = prefs.getBoolean(Prefs.KEY_SHOW_NOTIFICATION, true)
        prefs.edit().putBoolean(Prefs.KEY_SHOW_NOTIFICATION, !shown).apply()
        refreshNotificationStatus(prefs)
        Toast.makeText(
            this,
            if (shown) R.string.notification_hidden_toast else R.string.notification_shown_toast,
            Toast.LENGTH_LONG
        ).show()
        restartOverlayServiceIfRunning()
    }

    private fun restartOverlayServiceIfRunning() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        val isRunning = manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == OverlayService::class.java.name }
        if (isRunning) {
            stopService(Intent(this, OverlayService::class.java))
            OverlayService.start(this)
        }
    }

    private fun refreshAdminStatus() {
        val active = dpm.isAdminActive(adminComponent)
        adminStatusView.setText(if (active) R.string.admin_status_on else R.string.admin_status_off)
        toggleAdminButton.setText(if (active) R.string.disable_admin else R.string.enable_admin)
    }

    private fun toggleAdmin() {
        if (dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent)
            refreshAdminStatus()
            Toast.makeText(this, R.string.admin_disabled_toast, Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_add_explanation))
            }
            try {
                startActivityForResult(intent, REQUEST_ENABLE_ADMIN)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, R.string.admin_not_supported, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_ADMIN) {
            refreshAdminStatus()
        }
    }

    /**
     * Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS only exists on
     * API 23+; before that, Android doesn't kill foreground-service apps
     * for battery reasons in the first place, so there is nothing to ask for.
     */
    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, R.string.battery_exemption_not_needed, Toast.LENGTH_SHORT).show()
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, R.string.battery_exemption_already_granted, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Some OEM ROMs (mainly Xiaomi/Huawei) hide this screen and require
            // going through their own custom battery-manager app instead -
            // nothing more we can do generically here.
            Toast.makeText(this, R.string.battery_exemption_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            // Some OEM ROMs block this intent outright even with the permission
            // declared. Fail loudly instead of doing nothing, and send the user
            // to the OEM's own battery settings screen as a fallback.
            Toast.makeText(this, R.string.battery_exemption_unavailable, Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                // Nothing more we can do.
            }
        }
    }
}
