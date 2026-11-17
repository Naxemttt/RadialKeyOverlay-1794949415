package com.custom.radialpad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var profiles = mutableListOf<RadialProfile>()
    private var activeProfileIdx = 0
    private var activeButtonIdx = 0
    private var suppressSpinnerEvents = false

    private lateinit var spinnerProfiles: Spinner
    private lateinit var editProfileName: EditText
    private lateinit var spinnerButtons: Spinner
    private lateinit var editButtonName: EditText
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
        activeProfileIdx = loaded.first
        profiles = loaded.second

        spinnerProfiles = findViewById(R.id.spinnerProfiles)
        editProfileName = findViewById(R.id.editProfileName)
        spinnerButtons = findViewById(R.id.spinnerButtons)
        editButtonName = findViewById(R.id.editButtonName)
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

        spinnerProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerEvents) return
                saveCurrentFieldsToMemory()
                activeProfileIdx = position
                activeButtonIdx = 0
                refreshSpinnersAndFields()
                pushLiveUpdate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerButtons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerEvents) return
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
            val p = profiles[activeProfileIdx]
            val idx = p.buttons.size + 1
            p.buttons.add(RadialButtonConfig(name = "Bouton $idx", x = 200 + (idx * 90), y = 500))
            activeButtonIdx = p.buttons.size - 1
            refreshSpinnersAndFields()
            pushLiveUpdate()
        }

        findViewById<Button>(R.id.btnDelButton).setOnClickListener {
            val p = profiles[activeProfileIdx]
            if (p.buttons.size > 1) {
                p.buttons.removeAt(activeButtonIdx)
                activeButtonIdx = (activeButtonIdx - 1).coerceAtLeast(0)
                refreshSpinnersAndFields()
                pushLiveUpdate()
            } else {
                Toast.makeText(this, "Un profil doit garder au moins 1 bouton", Toast.LENGTH_SHORT).show()
            }
        }

        seekButtonSize.setOnSeekBarChangeListener(simpleSeek { txtButtonSize.text = "Taille bouton central : ${it.coerceAtLeast(30)} dp" })
        seekInnerRadius.setOnSeekBarChangeListener(simpleSeek { txtInnerRadius.text = "Rayon Anneau 1 : ${it.coerceAtLeast(80)} dp" })
        seekOuterRadius.setOnSeekBarChangeListener(simpleSeek { txtOuterRadius.text = "Rayon Anneau 2 (Externe) : ${it.coerceAtLeast(130)} dp" })
        seekOpacity.setOnSeekBarChangeListener(simpleSeek { txtOpacity.text = "Opacité : ${it.coerceAtLeast(10)}%" })

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveCurrentFieldsToMemory()
            refreshSpinnersAndFields()
            pushLiveUpdate()
            Toast.makeText(this, "Profil & Boutons appliqués en direct !", Toast.LENGTH_SHORT).show()
        }

        refreshSpinnersAndFields()
    }

    override fun onResume() {
        super.onResume()
        // Synchronise les positions X/Y si l'utilisateur a déplacé les boutons sur l'écran pendant que l'app était en arrière-plan
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

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    private fun saveCurrentFieldsToMemory() {
        syncPositionsFromDisk()
        val p = profiles.getOrNull(activeProfileIdx) ?: return
        p.name = editProfileName.text.toString().ifBlank { "Profil" }
        p.shellCmd = editShellCmd.text.toString().ifBlank { "input keyevent %d" }

        val b = p.buttons.getOrNull(activeButtonIdx) ?: return
        b.name = editButtonName.text.toString().ifBlank { "Bouton" }
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
        suppressSpinnerEvents = true
        val pNames = profiles.map { it.name }
        spinnerProfiles.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, pNames)
        spinnerProfiles.setSelection(activeProfileIdx)

        val currentProfile = profiles[activeProfileIdx]
        editProfileName.setText(currentProfile.name)
        editShellCmd.setText(currentProfile.shellCmd)

        val bNames = currentProfile.buttons.map { it.name }
        spinnerButtons.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bNames)
        activeButtonIdx = activeButtonIdx.coerceIn(0, currentProfile.buttons.size - 1)
        spinnerButtons.setSelection(activeButtonIdx)

        populateButtonFields()
        suppressSpinnerEvents = false
    }

    private fun populateButtonFields() {
        val b = profiles[activeProfileIdx].buttons[activeButtonIdx]
        editButtonName.setText(b.name)
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
    }

    private fun pushLiveUpdate() {
        syncPositionsFromDisk()
        ConfigManager.saveAll(this, activeProfileIdx, profiles)
        sendBroadcast(Intent("com.custom.radialpad.UPDATE_CONFIG").setPackage(packageName))
    }
}
