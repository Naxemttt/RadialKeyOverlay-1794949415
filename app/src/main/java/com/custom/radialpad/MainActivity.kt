package com.custom.radialpad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var profiles = mutableListOf<RadialProfile>()
    private var activeProfileIdx = 0
    private var activeButtonIdx = 0
    private var isUpdatingUi = false

    private lateinit var spinnerProfiles: Spinner
    private lateinit var editProfileName: EditText
    private lateinit var spinnerButtons: Spinner
    private lateinit var editButtonName: EditText
    private lateinit var editCenterLabel: EditText
    private lateinit var editInnerKeys: EditText
    private lateinit var chkOuterRing: CheckBox
    private lateinit var editOuterKeys: EditText
    private lateinit var editCenterTapKey: EditText
    private lateinit var chkQuickFlick: CheckBox
    private lateinit var seekButtonSize: SeekBar
    private lateinit var txtButtonSize: TextView
    private lateinit var seekInnerRadius: SeekBar
    private lateinit var txtInnerRadius: TextView
    private lateinit var seekOuterRadius: SeekBar
    private lateinit var txtOuterRadius: TextView
    private lateinit var seekOpacity: SeekBar
    private lateinit var txtOpacity: TextView
    private lateinit var editMainColor: EditText
    private lateinit var editAccentColor: EditText
    private lateinit var editShellCmd: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val loaded = ConfigManager.loadAll(this)
        activeProfileIdx = loaded.first.coerceIn(0, loaded.second.size - 1)
        profiles = loaded.second

        spinnerProfiles = findViewById(R.id.spinnerProfiles)
        editProfileName = findViewById(R.id.editProfileName)
        spinnerButtons = findViewById(R.id.spinnerButtons)
        editButtonName = findViewById(R.id.editButtonName)
        editCenterLabel = findViewById(R.id.editCenterLabel)
        editInnerKeys = findViewById(R.id.editInnerKeys)
        chkOuterRing = findViewById(R.id.chkOuterRing)
        editOuterKeys = findViewById(R.id.editOuterKeys)
        editCenterTapKey = findViewById(R.id.editCenterTapKey)
        chkQuickFlick = findViewById(R.id.chkQuickFlick)
        seekButtonSize = findViewById(R.id.seekButtonSize)
        txtButtonSize = findViewById(R.id.txtButtonSize)
        seekInnerRadius = findViewById(R.id.seekInnerRadius)
        txtInnerRadius = findViewById(R.id.txtInnerRadius)
        seekOuterRadius = findViewById(R.id.seekOuterRadius)
        txtOuterRadius = findViewById(R.id.txtOuterRadius)
        seekOpacity = findViewById(R.id.seekOpacity)
        txtOpacity = findViewById(R.id.txtOpacity)
        editMainColor = findViewById(R.id.editMainColor)
        editAccentColor = findViewById(R.id.editAccentColor)
        editShellCmd = findViewById(R.id.editShellCmd)

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Autorisation Overlay active !", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnAccService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Renommage en direct du profil pendant la frappe
        editProfileName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingUi) return
                val p = profiles.getOrNull(activeProfileIdx) ?: return
                val newName = s.toString()
                if (newName.isNotBlank() && p.name != newName) {
                    p.name = newName
                    val pNames = profiles.map { it.name }
                    val currentPos = spinnerProfiles.selectedItemPosition
                    spinnerProfiles.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, pNames)
                    spinnerProfiles.setSelection(currentPos, false)
                    pushLiveUpdate()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Renommage en direct du bouton pendant la frappe
        editButtonName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingUi) return
                val p = profiles.getOrNull(activeProfileIdx) ?: return
                val b = p.buttons.getOrNull(activeButtonIdx) ?: return
                val newName = s.toString()
                if (newName.isNotBlank() && b.name != newName) {
                    b.name = newName
                    val bNames = p.buttons.map { it.name }
                    val currentPos = spinnerButtons.selectedItemPosition
                    spinnerButtons.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bNames)
                    spinnerButtons.setSelection(currentPos, false)
                    pushLiveUpdate()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.btnPickInnerKeys).setOnClickListener {
            KeyPickerDialog(this, editInnerKeys.text.toString().trim()) { pickedKey ->
                if (pickedKey.isNotBlank()) {
                    val current = editInnerKeys.text.toString().trim()
                    editInnerKeys.setText(if (current.isEmpty()) pickedKey else "$current, $pickedKey")
                    saveCurrentFieldsToMemory()
                    pushLiveUpdate()
                }
            }.show()
        }

        findViewById<Button>(R.id.btnPickOuterKeys).setOnClickListener {
            KeyPickerDialog(this, editOuterKeys.text.toString().trim()) { pickedKey ->
                if (pickedKey.isNotBlank()) {
                    val current = editOuterKeys.text.toString().trim()
                    editOuterKeys.setText(if (current.isEmpty()) pickedKey else "$current, $pickedKey")
                    saveCurrentFieldsToMemory()
                    pushLiveUpdate()
                }
            }.show()
        }

        findViewById<Button>(R.id.btnPickCenterKey).setOnClickListener {
            KeyPickerDialog(this, editCenterTapKey.text.toString().trim()) { pickedKey ->
                editCenterTapKey.setText(pickedKey)
                saveCurrentFieldsToMemory()
                pushLiveUpdate()
            }.show()
        }

        spinnerProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingUi || position == activeProfileIdx) return
                saveCurrentFieldsToMemory()
                activeProfileIdx = position.coerceIn(0, profiles.size - 1)
                activeButtonIdx = 0
                refreshSpinnersAndFields()
                pushLiveUpdate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerButtons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val p = profiles.getOrNull(activeProfileIdx) ?: return
                if (isUpdatingUi || position == activeButtonIdx || position >= p.buttons.size) return
                saveCurrentFieldsToMemory()
                activeButtonIdx = position
                populateButtonFields()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnAddProfile).setOnClickListener {
            saveCurrentFieldsToMemory()
            val newP = RadialProfile(name = "Profil ${profiles.size + 1}")
            profiles.add(newP)
            activeProfileIdx = profiles.size - 1
            activeButtonIdx = 0
            refreshSpinnersAndFields()
            pushLiveUpdate()
        }

        findViewById<Button>(R.id.btnDelProfile).setOnClickListener {
            if (profiles.size > 1) {
                profiles.removeAt(activeProfileIdx)
                activeProfileIdx = (activeProfileIdx - 1).coerceAtLeast(0)
                activeButtonIdx = 0
                refreshSpinnersAndFields()
                pushLiveUpdate()
            } else {
                Toast.makeText(this, "Impossible de supprimer le dernier profil", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAddButton).setOnClickListener {
            saveCurrentFieldsToMemory()
            val p = profiles.getOrNull(activeProfileIdx) ?: return@setOnClickListener
            val idx = p.buttons.size + 1
            p.buttons.add(RadialButtonConfig(name = "Bouton $idx", x = 200 + (idx * 60), y = 400 + (idx * 60)))
            activeButtonIdx = p.buttons.size - 1
            refreshSpinnersAndFields()
            pushLiveUpdate()
        }

        findViewById<Button>(R.id.btnDelButton).setOnClickListener {
            val p = profiles.getOrNull(activeProfileIdx) ?: return@setOnClickListener
            if (p.buttons.size > 1) {
                p.buttons.removeAt(activeButtonIdx.coerceIn(0, p.buttons.size - 1))
                activeButtonIdx = (activeButtonIdx - 1).coerceAtLeast(0)
                refreshSpinnersAndFields()
                pushLiveUpdate()
            } else {
                Toast.makeText(this, "Un profil doit garder au moins 1 bouton", Toast.LENGTH_SHORT).show()
            }
        }

        seekButtonSize.setOnSeekBarChangeListener(liveSeek { txtButtonSize.text = "Taille bouton central : ${it.coerceAtLeast(30)} dp" })
        seekInnerRadius.setOnSeekBarChangeListener(liveSeek { txtInnerRadius.text = "Rayon Anneau 1 : ${it.coerceAtLeast(80)} dp" })
        seekOuterRadius.setOnSeekBarChangeListener(liveSeek { txtOuterRadius.text = "Rayon Anneau 2 (Externe) : ${it.coerceAtLeast(130)} dp" })
        seekOpacity.setOnSeekBarChangeListener(liveSeek { txtOpacity.text = "Opacité : ${it.coerceAtLeast(10)}%" })

        chkOuterRing.setOnCheckedChangeListener { _, _ ->
            if (!isUpdatingUi) {
                saveCurrentFieldsToMemory()
                pushLiveUpdate()
            }
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveCurrentFieldsToMemory()
            refreshSpinnersAndFields()
            pushLiveUpdate()
            Toast.makeText(this, "Modifications enregistrées et appliquées !", Toast.LENGTH_SHORT).show()
        }

        refreshSpinnersAndFields()
    }

    override fun onResume() {
        super.onResume()
        syncPositionsFromDisk()
    }

    private fun syncPositionsFromDisk() {
        val (_, diskProfiles) = ConfigManager.loadAll(this)
        for (p in profiles) {
            val diskP = diskProfiles.find { it.name == p.name } ?: continue
            for (b in p.buttons) {
                val diskB = diskP.buttons.find { it.id == b.id } ?: continue
                b.x = diskB.x
                b.y = diskB.y
            }
        }
    }

    private fun liveSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            onChange(p)
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {
            if (!isUpdatingUi) {
                saveCurrentFieldsToMemory()
                pushLiveUpdate()
            }
        }
    }

    private fun saveCurrentFieldsToMemory() {
        syncPositionsFromDisk()
        val p = profiles.getOrNull(activeProfileIdx) ?: return
        p.shellCmd = editShellCmd.text.toString().ifBlank { "input keyevent %d" }

        val b = p.buttons.getOrNull(activeButtonIdx) ?: return
        b.centerLabel = editCenterLabel.text.toString().ifBlank { "❖" }
        b.innerKeys = editInnerKeys.text.toString()
        b.enableOuterRing = chkOuterRing.isChecked
        b.outerKeys = editOuterKeys.text.toString()
        b.centerTapKey = editCenterTapKey.text.toString().trim()
        b.quickFlick = chkQuickFlick.isChecked
        b.buttonSizeDp = seekButtonSize.progress.coerceAtLeast(30)
        b.innerRadiusDp = seekInnerRadius.progress.coerceAtLeast(80)
        b.outerRadiusDp = seekOuterRadius.progress.coerceAtLeast(b.innerRadiusDp + 40)
        b.opacity = seekOpacity.progress.coerceAtLeast(10)
        b.mainColor = editMainColor.text.toString().ifBlank { "#222222" }
        b.accentColor = editAccentColor.text.toString().ifBlank { "#FF5722" }
    }

    private fun refreshSpinnersAndFields() {
        isUpdatingUi = true
        val pNames = profiles.map { it.name }
        spinnerProfiles.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, pNames)
        spinnerProfiles.setSelection(activeProfileIdx.coerceIn(0, (profiles.size - 1).coerceAtLeast(0)), false)

        val currentProfile = profiles.getOrNull(activeProfileIdx) ?: profiles[0]
        editProfileName.setText(currentProfile.name)
        editShellCmd.setText(currentProfile.shellCmd)

        val bNames = currentProfile.buttons.map { it.name }
        spinnerButtons.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bNames)
        activeButtonIdx = activeButtonIdx.coerceIn(0, (currentProfile.buttons.size - 1).coerceAtLeast(0))
        spinnerButtons.setSelection(activeButtonIdx, false)

        populateButtonFields()
        spinnerProfiles.post { isUpdatingUi = false }
    }

    private fun populateButtonFields() {
        val wasUpdating = isUpdatingUi
        isUpdatingUi = true
        val p = profiles.getOrNull(activeProfileIdx) ?: profiles[0]
        val b = p.buttons.getOrNull(activeButtonIdx) ?: p.buttons[0]

        editButtonName.setText(b.name)
        editCenterLabel.setText(b.centerLabel)
        editInnerKeys.setText(b.innerKeys)
        chkOuterRing.isChecked = b.enableOuterRing
        editOuterKeys.setText(b.outerKeys)
        editCenterTapKey.setText(b.centerTapKey)
        chkQuickFlick.isChecked = b.quickFlick
        seekButtonSize.progress = b.buttonSizeDp
        txtButtonSize.text = "Taille bouton central : ${b.buttonSizeDp} dp"
        seekInnerRadius.progress = b.innerRadiusDp
        txtInnerRadius.text = "Rayon Anneau 1 : ${b.innerRadiusDp} dp"
        seekOuterRadius.progress = b.outerRadiusDp
        txtOuterRadius.text = "Rayon Anneau 2 (Externe) : ${b.outerRadiusDp} dp"
        seekOpacity.progress = b.opacity
        txtOpacity.text = "Opacité : ${b.opacity}%"
        editMainColor.setText(b.mainColor)
        editAccentColor.setText(b.accentColor)
        isUpdatingUi = wasUpdating
    }

    private fun pushLiveUpdate() {
        syncPositionsFromDisk()
        ConfigManager.saveAll(this, activeProfileIdx, profiles)
        sendBroadcast(Intent("com.custom.radialpad.UPDATE_CONFIG").setPackage(packageName))
    }
}
