package com.overlaymanager.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

/**
 * Plain android.app.Activity - no AppCompat. Image picking uses the classic
 * startActivityForResult/onActivityResult pair instead of
 * androidx.activity.result.contract.ActivityResultContracts, which (like
 * AppCompatActivity) requires API 21+ internally.
 */
class LayerEditActivity : Activity() {

    companion object {
        const val EXTRA_LAYER_ID = "layer_id"
        private const val REQUEST_PICK_IMAGE = 2001
    }

    private var layer: OverlayLayer = OverlayLayer()
    private var isNew = true

    private lateinit var nameField: EditText
    private lateinit var imagePreview: ImageView
    private lateinit var pickImageButton: Button
    private lateinit var widthField: EditText
    private lateinit var heightField: EditText
    private lateinit var anchorHSpinner: Spinner
    private lateinit var anchorVSpinner: Spinner
    private lateinit var offsetXField: EditText
    private lateinit var offsetYField: EditText
    private lateinit var alphaSeekBar: SeekBar
    private lateinit var alphaLabel: TextView
    private lateinit var tintSpinner: Spinner
    private lateinit var lockedSwitch: Switch

    private val tintOptions = listOf(
        R.string.tint_none to null,
        R.string.tint_red to 0xFFFF0000.toInt(),
        R.string.tint_green to 0xFF00FF00.toInt(),
        R.string.tint_blue to 0xFF0000FF.toInt(),
        R.string.tint_yellow to 0xFFFFFF00.toInt(),
        R.string.tint_black to 0xFF000000.toInt(),
        R.string.tint_white to 0xFFFFFFFF.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_layer_edit)

        val layerId = intent.getStringExtra(EXTRA_LAYER_ID)
        if (layerId != null) {
            LayerStore.load(this).find { it.id == layerId }?.let {
                layer = it
                isNew = false
            }
        }

        bindViews()
        populateFromLayer()
        wireLiveUpdates()
    }

    private fun bindViews() {
        nameField = findViewById(R.id.fieldName)
        imagePreview = findViewById(R.id.imagePreview)
        pickImageButton = findViewById(R.id.buttonPickImage)
        widthField = findViewById(R.id.fieldWidth)
        heightField = findViewById(R.id.fieldHeight)
        anchorHSpinner = findViewById(R.id.spinnerAnchorH)
        anchorVSpinner = findViewById(R.id.spinnerAnchorV)
        offsetXField = findViewById(R.id.fieldOffsetX)
        offsetYField = findViewById(R.id.fieldOffsetY)
        alphaSeekBar = findViewById(R.id.seekAlpha)
        alphaLabel = findViewById(R.id.labelAlpha)
        tintSpinner = findViewById(R.id.spinnerTint)
        lockedSwitch = findViewById(R.id.switchLocked)

        anchorHSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.anchor_left), getString(R.string.anchor_center), getString(R.string.anchor_right))
        )
        anchorVSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.anchor_top), getString(R.string.anchor_middle), getString(R.string.anchor_bottom))
        )
        tintSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            tintOptions.map { getString(it.first) }
        )

        pickImageButton.setOnClickListener { launchImagePicker() }

        findViewById<Button>(R.id.buttonSave).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.buttonDelete).apply {
            visibility = if (isNew) android.view.View.GONE else android.view.View.VISIBLE
            setOnClickListener { deleteAndFinish() }
        }
    }

    /**
     * ACTION_OPEN_DOCUMENT gives a persistable read grant and has existed
     * since API 19 (KitKat) itself, so it works on the oldest device this
     * app supports. A small number of very old/custom ROMs ship without a
     * document-picker app at all, so ACTION_GET_CONTENT (present since API 1)
     * is used as a fallback in that case.
     */
    private fun launchImagePicker() {
        val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(openDocument, REQUEST_PICK_IMAGE)
        } catch (e: ActivityNotFoundException) {
            val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            try {
                startActivityForResult(getContent, REQUEST_PICK_IMAGE)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_image_picker_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != Activity.RESULT_OK) return

        val uri: Uri = data?.data ?: return

        // Persist read permission so the image survives a reboot/service
        // restart, same as before. ACTION_GET_CONTENT results don't always
        // grant a persistable permission, so this is best-effort.
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Fine - the URI will still work for this session even if it
            // can't be persisted across a reboot.
        }

        layer.imageUri = uri.toString()
        imagePreview.setImageURI(uri)
        pushLivePreview()
    }

    private fun populateFromLayer() {
        nameField.setText(layer.name)
        widthField.setText(layer.widthPx.toString())
        heightField.setText(layer.heightPx.toString())
        anchorHSpinner.setSelection(AnchorH.entries.indexOf(layer.anchorH))
        anchorVSpinner.setSelection(AnchorV.entries.indexOf(layer.anchorV))
        offsetXField.setText(layer.offsetXPercent.toString())
        offsetYField.setText(layer.offsetYPercent.toString())
        alphaSeekBar.max = 255
        alphaSeekBar.progress = layer.alpha
        alphaLabel.text = layer.alpha.toString()
        tintSpinner.setSelection(tintOptions.indexOfFirst { it.second == layer.tintColor }.coerceAtLeast(0))
        lockedSwitch.isChecked = layer.locked

        layer.imageUri?.let { imagePreview.setImageURI(Uri.parse(it)) }
    }

    /**
     * Every change in the form is written into `layer` immediately and pushed to
     * the running OverlayService, exactly like "כל שינוי בטופס מוצג מיד על המסך"
     * in the original app. The bug fixed in the original README (auto-filled
     * fields triggering the live preview mid-population, mixing old/new values)
     * is avoided here by only wiring listeners AFTER populateFromLayer() ran.
     */
    private fun wireLiveUpdates() {
        nameField.doAfterTextChangedSafe { layer.name = it }

        widthField.doAfterTextChangedSafe { layer.widthPx = it.toIntOrNull() ?: layer.widthPx; pushLivePreview() }
        heightField.doAfterTextChangedSafe { layer.heightPx = it.toIntOrNull() ?: layer.heightPx; pushLivePreview() }
        offsetXField.doAfterTextChangedSafe { layer.offsetXPercent = it.toFloatOrNull() ?: layer.offsetXPercent; pushLivePreview() }
        offsetYField.doAfterTextChangedSafe { layer.offsetYPercent = it.toFloatOrNull() ?: layer.offsetYPercent; pushLivePreview() }

        anchorHSpinner.onItemSelectedListener = simpleSpinnerListener {
            layer.anchorH = AnchorH.entries[it]; pushLivePreview()
        }
        anchorVSpinner.onItemSelectedListener = simpleSpinnerListener {
            layer.anchorV = AnchorV.entries[it]; pushLivePreview()
        }
        tintSpinner.onItemSelectedListener = simpleSpinnerListener {
            layer.tintColor = tintOptions[it].second; pushLivePreview()
        }

        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                layer.alpha = progress
                alphaLabel.text = progress.toString()
                if (fromUser) pushLivePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        lockedSwitch.setOnCheckedChangeListener { _, checked ->
            layer.locked = checked
            pushLivePreview()
        }
    }

    private fun simpleSpinnerListener(onSelected: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                onSelected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    private fun EditText.doAfterTextChangedSafe(action: (String) -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { action(s?.toString().orEmpty()) }
        })
    }

    /**
     * Persists the current in-memory `layer` and asks the running service to
     * redraw just this one. If the overlay permission is missing, tell the
     * user why nothing appeared instead of silently doing nothing.
     */
    private fun pushLivePreview() {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (!hasPermission) {
            Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show()
            return
        }
        val layers = LayerStore.load(this)
        val idx = layers.indexOfFirst { it.id == layer.id }
        if (idx >= 0) layers[idx] = layer else layers.add(layer)
        LayerStore.save(this, layers)

        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_REFRESH_LAYER)
            .putExtra(OverlayService.EXTRA_LAYER_ID, layer.id)
        startService(intent)
    }

    private fun saveAndFinish() {
        layer.name = nameField.text.toString().ifBlank { getString(R.string.default_layer_name) }
        val layers = LayerStore.load(this)
        val idx = layers.indexOfFirst { it.id == layer.id }
        if (idx >= 0) layers[idx] = layer else layers.add(layer)
        LayerStore.save(this, layers)
        finish()
    }

    private fun deleteAndFinish() {
        val layers = LayerStore.load(this)
        layers.removeAll { it.id == layer.id }
        LayerStore.save(this, layers)
        val intent = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_REFRESH_LAYER)
            .putExtra(OverlayService.EXTRA_LAYER_ID, layer.id)
        startService(intent)
        finish()
    }
}
