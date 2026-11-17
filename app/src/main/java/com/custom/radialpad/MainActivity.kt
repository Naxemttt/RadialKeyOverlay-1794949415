package com.custom.radialpad

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("radial_prefs", Context.MODE_PRIVATE)
        val btnOverlayPerm = findViewById<Button>(R.id.btnOverlayPerm)
        val btnAccService = findViewById<Button>(R.id.btnAccService)
        val editKeys = findViewById<EditText>(R.id.editKeys)
        val seekButtonSize = findViewById<SeekBar>(R.id.seekButtonSize)
        val txtButtonSize = findViewById<TextView>(R.id.txtButtonSize)
        val seekMenuRadius = findViewById<SeekBar>(R.id.seekMenuRadius)
        val txtMenuRadius = findViewById<TextView>(R.id.txtMenuRadius)
        val seekOpacity = findViewById<SeekBar>(R.id.seekOpacity)
        val txtOpacity = findViewById<TextView>(R.id.txtOpacity)
        val editMainColor = findViewById<EditText>(R.id.editMainColor)
        val editAccentColor = findViewById<EditText>(R.id.editAccentColor)
        val editShellCmd = findViewById<EditText>(R.id.editShellCmd)
        val btnSave = findViewById<Button>(R.id.btnSave)

        editKeys.setText(prefs.getString("keys", "1,2,3,4,5,6"))
        seekButtonSize.progress = prefs.getInt("btn_size", 70)
        txtButtonSize.text = "Taille du bouton flottant : ${seekButtonSize.progress} dp"
        seekMenuRadius.progress = prefs.getInt("menu_radius", 160)
        txtMenuRadius.text = "Rayon du menu déployé : ${seekMenuRadius.progress} dp"
        seekOpacity.progress = prefs.getInt("opacity", 80)
        txtOpacity.text = "Opacité : ${seekOpacity.progress}%"
        editMainColor.setText(prefs.getString("main_color", "#222222"))
        editAccentColor.setText(prefs.getString("accent_color", "#FF5722"))
        editShellCmd.setText(prefs.getString("shell_cmd", "input keyevent %d"))

        btnOverlayPerm.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Autorisation Overlay déjà accordée !", Toast.LENGTH_SHORT).show()
            }
        }
        btnAccService.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        seekButtonSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { txtButtonSize.text = "Taille du bouton flottant : ${p.coerceAtLeast(30)} dp" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seekMenuRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { txtMenuRadius.text = "Rayon du menu déployé : ${p.coerceAtLeast(80)} dp" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) { txtOpacity.text = "Opacité : ${p.coerceAtLeast(10)}%" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            prefs.edit().apply {
                putString("keys", editKeys.text.toString())
                putInt("btn_size", seekButtonSize.progress.coerceAtLeast(30))
                putInt("menu_radius", seekMenuRadius.progress.coerceAtLeast(80))
                putInt("opacity", seekOpacity.progress.coerceAtLeast(10))
                putString("main_color", editMainColor.text.toString())
                putString("accent_color", editAccentColor.text.toString())
                putString("shell_cmd", editShellCmd.text.toString())
                apply()
            }
            sendBroadcast(Intent("com.custom.radialpad.UPDATE_CONFIG").setPackage(packageName))
            Toast.makeText(this, "Configuration mise à jour !", Toast.LENGTH_SHORT).show()
        }
    }
}
