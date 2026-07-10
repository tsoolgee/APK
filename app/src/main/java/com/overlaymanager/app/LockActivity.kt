package com.overlaymanager.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

/**
 * App-entry PIN lock. This protects the app's own screens from someone who
 * already has the unlocked phone in hand - it is NOT device/OS-level
 * security and doesn't touch app permissions or uninstall in any way.
 *
 * First run (no PIN saved yet): asks for a new PIN twice, saves its SHA-256
 * hash (never the PIN itself), then opens MainActivity.
 * Every other run: asks for the PIN, and only opens MainActivity if the hash
 * matches.
 */
class LockActivity : Activity() {

    private lateinit var titleView: TextView
    private lateinit var ownerInfoView: TextView
    private lateinit var pinField: EditText
    private lateinit var pinConfirmField: EditText
    private lateinit var unlockButton: Button
    private lateinit var skipButton: Button

    private var isSetupMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        // Explicit "change PIN" / "re-enable PIN" flow requested from
        // SecurityActivity always acts like a fresh setup, even if a PIN
        // already exists or the lock was previously skipped.
        val forceSetup = intent.getBooleanExtra(EXTRA_FORCE_SETUP, false)

        // User previously chose "continue without a PIN" - go straight to
        // MainActivity every launch, without even showing this screen, unless
        // SecurityActivity is explicitly asking to set one up now.
        if (!forceSetup && prefs.getBoolean(Prefs.KEY_LOCK_DISABLED, false)) {
            openMain()
            return
        }

        setContentView(R.layout.activity_lock)

        titleView = findViewById(R.id.textLockTitle)
        ownerInfoView = findViewById(R.id.textOwnerInfo)
        pinField = findViewById(R.id.fieldPin)
        pinConfirmField = findViewById(R.id.fieldPinConfirm)
        unlockButton = findViewById(R.id.buttonUnlock)
        skipButton = findViewById(R.id.buttonSkipPin)

        val existingHash = prefs.getString(Prefs.KEY_PIN_HASH, null)
        isSetupMode = existingHash == null || forceSetup

        val ownerName = prefs.getString(Prefs.KEY_OWNER_NAME, "").orEmpty()
        val ownerPhone = prefs.getString(Prefs.KEY_OWNER_PHONE, "").orEmpty()
        if (!isSetupMode && (ownerName.isNotBlank() || ownerPhone.isNotBlank())) {
            ownerInfoView.visibility = android.view.View.VISIBLE
            ownerInfoView.text = getString(R.string.owner_stamp_format, ownerName, ownerPhone)
        }

        if (isSetupMode) {
            titleView.setText(R.string.set_new_pin)
            pinField.hint = getString(R.string.pin_hint)
            pinConfirmField.visibility = android.view.View.VISIBLE
            skipButton.visibility = android.view.View.VISIBLE
        } else {
            titleView.setText(R.string.enter_pin)
        }

        unlockButton.setOnClickListener {
            if (isSetupMode) handleSetup(prefs) else handleUnlock(prefs)
        }

        skipButton.setOnClickListener {
            prefs.edit()
                .putBoolean(Prefs.KEY_LOCK_DISABLED, true)
                .remove(Prefs.KEY_PIN_HASH)
                .apply()
            openMain()
        }
    }

    private fun handleSetup(prefs: android.content.SharedPreferences) {
        val pin = pinField.text.toString()
        val confirm = pinConfirmField.text.toString()
        if (pin.length < 4) {
            Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (pin != confirm) {
            Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
            pinConfirmField.text.clear()
            return
        }
        prefs.edit()
            .putString(Prefs.KEY_PIN_HASH, sha256(pin))
            .putBoolean(Prefs.KEY_LOCK_DISABLED, false)
            .apply()
        Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
        openMain()
    }

    private fun handleUnlock(prefs: android.content.SharedPreferences) {
        val pin = pinField.text.toString()
        val storedHash = prefs.getString(Prefs.KEY_PIN_HASH, null)
        if (storedHash != null && storedHash == sha256(pin)) {
            openMain()
        } else {
            Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
            pinField.text.clear()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val EXTRA_FORCE_SETUP = "force_setup"
    }
}
