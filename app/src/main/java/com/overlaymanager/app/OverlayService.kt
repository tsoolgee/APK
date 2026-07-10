package com.overlaymanager.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager

/**
 * The Foreground Service is the Android equivalent of the always-running Windows
 * process: it owns every layered overlay window and keeps them on screen
 * regardless of which app currently has focus, exactly like the WS_EX_LAYERED /
 * WS_EX_TOPMOST windows in the original app.
 *
 * A persistent notification replaces the Windows tray icon, with quick actions
 * to show/hide everything at once (the Ctrl+Alt+H equivalent) and to reopen the app.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val activeViews = mutableMapOf<String, OverlayMediaView>()
    private var allHidden = false

    companion object {
        private const val TAG = "OverlayManager"
        const val CHANNEL_ID = "overlay_manager_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_REFRESH_LAYER = "com.overlaymanager.app.REFRESH_LAYER"   // live preview of one layer while editing
        const val ACTION_REFRESH_ALL = "com.overlaymanager.app.REFRESH_ALL"       // reload every layer from disk
        const val ACTION_TOGGLE_ALL = "com.overlaymanager.app.TOGGLE_ALL"         // Ctrl+Alt+H equivalent
        const val ACTION_STOP = "com.overlaymanager.app.STOP"

        const val EXTRA_LAYER_ID = "layer_id"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            val notificationEnabled = context
                .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                .getBoolean(Prefs.KEY_SHOW_NOTIFICATION, true)
            if (notificationEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                // Either the notification is intentionally off (plain background
                // service, no foreground privileges - see onCreate) or we're on
                // an API level where a foreground service never required one.
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val notificationEnabled = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getBoolean(Prefs.KEY_SHOW_NOTIFICATION, true)
        if (notificationEnabled) {
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        // else: intentionally skipped by the user in Security settings - this
        // runs as a plain background service with no notification at all, at
        // the cost of Android being free to stop it sooner while the app
        // isn't in the foreground (see SecurityActivity's explanation text).
        redrawAllLayers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH_LAYER -> {
                val id = intent.getStringExtra(EXTRA_LAYER_ID)
                val layer = LayerStore.load(this).find { it.id == id }
                if (layer != null) addOrUpdateLayerView(layer) else id?.let { removeLayerView(it) }
            }
            ACTION_REFRESH_ALL -> redrawAllLayers()
            ACTION_TOGGLE_ALL -> toggleAllVisibility()
            ACTION_STOP -> {
                stopSelf()
            }
            else -> redrawAllLayers()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeViews.keys.toList().forEach { removeLayerView(it) }
        super.onDestroy()
    }

    // ---- core rendering -----------------------------------------------------

    private fun redrawAllLayers() {
        val layers = LayerStore.load(this)
        val currentIds = layers.map { it.id }.toSet()
        // remove views for layers that no longer exist
        activeViews.keys.filter { it !in currentIds }.toList().forEach { removeLayerView(it) }
        // add/update the rest
        layers.forEach { layer ->
            if (layer.enabled && !allHidden) addOrUpdateLayerView(layer) else removeLayerView(layer.id)
        }
    }

    private fun toggleAllVisibility() {
        allHidden = !allHidden
        redrawAllLayers()
    }

    private fun addOrUpdateLayerView(layer: OverlayLayer) {
        removeLayerView(layer.id) // simplest correct approach: rebuild the view on every change

        val uriString = layer.imageUri
        if (uriString == null) {
            android.util.Log.w(TAG, "Layer '${layer.name}' has no image selected - skipping")
            return
        }
        val mediaView = OverlayMediaView(this)

        val loaded = try {
            mediaView.loadFrom(this, Uri.parse(uriString))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load image for layer '${layer.name}': ${e.message}")
            false
        }
        if (!loaded) {
            android.util.Log.w(TAG, "Layer '${layer.name}' image could not be decoded - skipping")
            return // missing/unreadable file - skip this layer rather than crash the service
        }

        mediaView.setOverlayAlpha(layer.alpha.coerceIn(0, 255))
        mediaView.setOverlayTint(layer.tintColor)

        val params = buildLayoutParams(layer)
        try {
            windowManager.addView(mediaView, params)
            activeViews[layer.id] = mediaView
            android.util.Log.i(TAG, "Layer '${layer.name}' drawn successfully")
        } catch (e: Exception) {
            // Most likely the SYSTEM_ALERT_WINDOW ("display over other apps")
            // permission is missing or was revoked - MainActivity is expected
            // to check Settings.canDrawOverlays() before starting this
            // service, but logging here too makes `adb logcat` diagnosable.
            android.util.Log.e(TAG, "Failed to draw layer '${layer.name}' - overlay permission missing/revoked?", e)
        }
    }

    private fun removeLayerView(id: String) {
        val view = activeViews[id] ?: return
        try {
            windowManager.removeView(view)
            activeViews.remove(id)
        } catch (e: Exception) {
            // Previously this removed the entry from activeViews *before*
            // attempting windowManager.removeView() and swallowed any
            // exception silently - if the removal ever failed (which is
            // exactly what "show/hide all" surfaced, since it always goes
            // through this path), the view stayed on screen forever but the
            // service had already forgotten about it, so every future
            // add/remove for that layer silently no-op'd or stacked a
            // duplicate on top. Keeping the entry on failure lets the next
            // toggle retry the removal instead of losing the view forever,
            // and logging it makes a real failure visible in `adb logcat`.
            android.util.Log.e(TAG, "Failed to remove overlay view for layer $id - will retry next time", e)
        }
    }

    private fun buildLayoutParams(layer: OverlayLayer): WindowManager.LayoutParams {
        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // FLAG_NOT_TOUCHABLE = click-through (equivalent of WS_EX_TRANSPARENT).
        // Unlocked layers stay touchable so they could be dragged from an
        // editing surface in a future version of LayerEditActivity.
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (layer.locked) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val params = WindowManager.LayoutParams(
            layer.widthPx,
            layer.heightPx,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        )

        val metrics: DisplayMetrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val offsetXPx = (layer.offsetXPercent / 100f * screenW).toInt()
        val offsetYPx = (layer.offsetYPercent / 100f * screenH).toInt()

        params.gravity = when (layer.anchorV) {
            AnchorV.TOP -> Gravity.TOP
            AnchorV.MIDDLE -> Gravity.CENTER_VERTICAL
            AnchorV.BOTTOM -> Gravity.BOTTOM
        } or when (layer.anchorH) {
            AnchorH.LEFT -> Gravity.START
            AnchorH.CENTER -> Gravity.CENTER_HORIZONTAL
            AnchorH.RIGHT -> Gravity.END
        }

        params.x = if (layer.anchorH == AnchorH.CENTER) 0 else offsetXPx
        params.y = if (layer.anchorV == AnchorV.MIDDLE) 0 else offsetYPx

        return params
    }

    // ---- notification (tray icon equivalent) --------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Plain android.app.Notification.Builder - no androidx.core NotificationCompat.
     * Two framework details are hand-gated here because a real Android 4.4
     * phone is API 19, and:
     *  - the (Context, channelId) constructor only exists on API 26+
     *  - Notification.Builder.addAction(int, CharSequence, PendingIntent) was
     *    only added in API 20 ("4.4W"), which real 4.4 phones (API 19) don't
     *    have - calling it there would crash with NoSuchMethodError. So the
     *    action buttons are only added on API 20+; on plain API 19 the
     *    notification still works, just without the two shortcut buttons.
     */
    private fun buildNotification(): Notification {
        // Route through LockActivity (not MainActivity directly) so tapping
        // the notification still requires the app PIN, same as opening the
        // app from the launcher.
        val openAppIntent = Intent(this, LockActivity::class.java)
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val openAppPending = PendingIntent.getActivity(this, 0, openAppIntent, piFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= 20) {
            val toggleIntent = Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE_ALL)
            val togglePending = PendingIntent.getService(this, 1, toggleIntent, piFlags)

            val stopIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
            val stopPending = PendingIntent.getService(this, 2, stopIntent, piFlags)

            builder.addAction(0, getString(R.string.action_toggle_all), togglePending)
            builder.addAction(0, getString(R.string.action_stop), stopPending)
        }

        return builder.build()
    }
}
