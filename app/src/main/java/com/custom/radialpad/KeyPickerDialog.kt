package com.custom.radialpad

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*

class KeyPickerDialog(
    context: Context,
    private val currentSpec: String,
    private val onKeyPicked: (String) -> Unit
) : Dialog(context) {

    private var selectedKey = currentSpec
    private var useShift = false
    private var useCtrl = false
    private var useAlt = false

    private lateinit var txtPreview: TextView
    private lateinit var btnShift: ToggleButton
    private lateinit var btnCtrl: ToggleButton
    private lateinit var btnAlt: ToggleButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_key_picker)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        txtPreview = findViewById(R.id.txtKeyPreview)
        btnShift = findViewById(R.id.btnToggleShift)
        btnCtrl = findViewById(R.id.btnToggleCtrl)
        btnAlt = findViewById(R.id.btnToggleAlt)

        // Analyse de la spec actuelle (ex: SHIFT+F1)
        val parts = currentSpec.uppercase().split("+")
        useShift = parts.contains("SHIFT")
        useCtrl = parts.contains("CTRL")
        useAlt = parts.contains("ALT")
        selectedKey = parts.lastOrNull { it != "SHIFT" && it != "CTRL" && it != "ALT" } ?: "1"

        btnShift.isChecked = useShift
        btnCtrl.isChecked = useCtrl
        btnAlt.isChecked = useAlt

        btnShift.setOnCheckedChangeListener { _, isChecked -> useShift = isChecked; updatePreview() }
        btnCtrl.setOnCheckedChangeListener { _, isChecked -> useCtrl = isChecked; updatePreview() }
        btnAlt.setOnCheckedChangeListener { _, isChecked -> useAlt = isChecked; updatePreview() }

        updatePreview()

        // Association des clics de tous les boutons du clavier virtuel
        val btnIds = listOf(
            R.id.k_f1 to "F1", R.id.k_f2 to "F2", R.id.k_f3 to "F3", R.id.k_f4 to "F4",
            R.id.k_f5 to "F5", R.id.k_f6 to "F6", R.id.k_f7 to "F7", R.id.k_f8 to "F8",
            R.id.k_f9 to "F9", R.id.k_f10 to "F10", R.id.k_f11 to "F11", R.id.k_f12 to "F12",
            R.id.k_esc to "ESC", R.id.k_tab to "TAB", R.id.k_space to "SPACE", R.id.k_enter to "ENTER",
            R.id.k_bksp to "BACKSPACE", R.id.k_del to "DELETE",
            R.id.k_up to "UP", R.id.k_down to "DOWN", R.id.k_left to "LEFT", R.id.k_right to "RIGHT",
            R.id.k_0 to "0", R.id.k_1 to "1", R.id.k_2 to "2", R.id.k_3 to "3", R.id.k_4 to "4",
            R.id.k_5 to "5", R.id.k_6 to "6", R.id.k_7 to "7", R.id.k_8 to "8", R.id.k_9 to "9",
            R.id.k_a to "A", R.id.k_b to "B", R.id.k_c to "C", R.id.k_d to "D", R.id.k_e to "E",
            R.id.k_f to "F", R.id.k_g to "G", R.id.k_h to "H", R.id.k_i to "I", R.id.k_j to "J",
            R.id.k_k to "K", R.id.k_l to "L", R.id.k_m to "M", R.id.k_n to "N", R.id.k_o to "O",
            R.id.k_p to "P", R.id.k_q to "Q", R.id.k_r to "R", R.id.k_s to "S", R.id.k_t to "T",
            R.id.k_u to "U", R.id.k_v to "V", R.id.k_w to "W", R.id.k_x to "X", R.id.k_y to "Y",
            R.id.k_z to "Z"
        )

        for ((id, keyName) in btnIds) {
            findViewById<Button>(id)?.setOnClickListener {
                selectedKey = keyName
                updatePreview()
                onKeyPicked(buildResultSpec())
                dismiss()
            }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            onKeyPicked("")
            dismiss()
        }
    }

    private fun updatePreview() {
        txtPreview.text = "Touche sélectionnée : ${buildResultSpec()}"
    }

    private fun buildResultSpec(): String {
        val sb = mutableListOf<String>()
        if (useShift) sb.add("SHIFT")
        if (useCtrl) sb.add("CTRL")
        if (useAlt) sb.add("ALT")
        sb.add(selectedKey)
        return sb.joinToString("+")
    }
}
