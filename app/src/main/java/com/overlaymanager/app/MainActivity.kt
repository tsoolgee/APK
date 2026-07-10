package com.overlaymanager.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Plain android.app.Activity - no AppCompat. AppCompatActivity (and the
 * androidx.activity Activity Result APIs it drags in) require API 21+
 * internally, which would silently break every device running the real
 * Android 4.4 this app targets. A ListView + BaseAdapter replaces
 * RecyclerView for the same reason.
 */
class MainActivity : Activity() {

    private lateinit var adapter: LayerAdapter
    private lateinit var listView: ListView
    private lateinit var emptyText: TextView
    private lateinit var masterSwitch: Switch
    private lateinit var autoStartSwitch: Switch

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_NOTIFICATION_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listLayers)
        emptyText = findViewById(R.id.textEmpty)
        masterSwitch = findViewById(R.id.switchMaster)
        autoStartSwitch = findViewById(R.id.switchAutoStart)

        adapter = LayerAdapter(
            context = this,
            onEdit = { layer -> openEditor(layer.id) },
            onDelete = { layer -> deleteLayer(layer) },
            onToggleEnabled = { layer, checked -> toggleLayerEnabled(layer, checked) }
        )
        listView.adapter = adapter

        findViewById<Button>(R.id.buttonAddLayer).setOnClickListener { openEditor(null) }
        findViewById<Button>(R.id.buttonSecurity).setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        autoStartSwitch.isChecked = prefs.getBoolean(Prefs.KEY_AUTO_START, false)
        autoStartSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            prefs.edit().putBoolean(Prefs.KEY_AUTO_START, checked).apply()
        }

        masterSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            if (checked) requestPermissionsAndStart() else stopOverlayService()
        }

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        // The overlay-permission screen doesn't reliably return a usable
        // result code, so re-check the real permission state every time the
        // user comes back to this screen (e.g. after visiting Settings).
        syncMasterSwitchWithRealPermissionState()
    }

    private fun refreshList() {
        val layers = LayerStore.load(this)
        adapter.submitList(layers)
        emptyText.visibility = if (layers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(layerId: String?) {
        val intent = Intent(this, LayerEditActivity::class.java)
        layerId?.let { intent.putExtra(LayerEditActivity.EXTRA_LAYER_ID, it) }
        startActivity(intent)
    }

    private fun deleteLayer(layer: OverlayLayer) {
        val layers = LayerStore.load(this)
        layers.removeAll { it.id == layer.id }
        LayerStore.save(this, layers)
        refreshList()
        notifyServiceRefreshLayer(layer.id, removed = true)
    }

    private fun toggleLayerEnabled(layer: OverlayLayer, enabled: Boolean) {
        val layers = LayerStore.load(this)
        layers.find { it.id == layer.id }?.enabled = enabled
        LayerStore.save(this, layers)
        notifyServiceRefreshLayer(layer.id, removed = !enabled)
    }

    // ---- overlay permission ("הצגה מעל אפליקציות אחרות") --------------------

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestPermissionsAndStart() {
        if (hasOverlayPermission()) {
            startOverlayService()
            return
        }
        // Below Android 6 (API 23) SYSTEM_ALERT_WINDOW is a normal permission
        // granted automatically at install time from the manifest - there is
        // no runtime prompt and Settings.canDrawOverlays()/ACTION_MANAGE_OVERLAY_PERMISSION
        // don't even exist on these OS versions, hasOverlayPermission() already
        // returns true for them above.
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
            masterSwitch.isChecked = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                startOverlayService()
            } else {
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
                masterSwitch.isChecked = false
            }
        }
    }

    private fun syncMasterSwitchWithRealPermissionState() {
        if (masterSwitch.isChecked && !hasOverlayPermission()) {
            masterSwitch.isChecked = false
        }
    }

    private fun maybeRequestNotificationPermission() {
        // POST_NOTIFICATIONS only exists starting API 33 (Tiramisu); every
        // API used in this block is guarded to only run on 33+.
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
        masterSwitch.isChecked = true
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP)
        startService(intent)
        masterSwitch.isChecked = false
    }

    private fun notifyServiceRefreshLayer(layerId: String, removed: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_REFRESH_LAYER)
            .putExtra(OverlayService.EXTRA_LAYER_ID, layerId)
        startService(intent)
    }
}
