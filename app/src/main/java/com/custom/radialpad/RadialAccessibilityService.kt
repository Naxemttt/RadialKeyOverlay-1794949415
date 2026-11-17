package com.custom.radialpad

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class RadialAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var radialView: RadialMenuView? = null
    private lateinit var params: WindowManager.LayoutParams

    private var savedCenterX = 300
    private var savedCenterY = 600
    private var shellCmdTemplate = "input keyevent %d"

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadPreferencesAndUpdate()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter("com.custom.radialpad.UPDATE_CONFIG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        createOverlay()
        loadPreferencesAndUpdate()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun createOverlay() {
        params = WindowManager.LayoutParams(
            dpToPx(70), dpToPx(70),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedCenterX
            y = savedCenterY
        }

        radialView = RadialMenuView(
            context = this,
            onKeySelected = { key -> KeyInjector.triggerKey(this, key, shellCmdTemplate) },
            onMoveOverlay = { dx, dy ->
                savedCenterX += dx
                savedCenterY += dy
                params.x = savedCenterX
                params.y = savedCenterY
                windowManager.updateViewLayout(radialView, params)
            },
            onExpandChanged = { expanded ->
                val view = radialView ?: return@RadialMenuView
                val btnSize = view.buttonSizePx.toInt()
                val expandedSize = (view.menuRadiusPx * 2.2f).toInt()
                if (expanded) {
                    val offset = (expandedSize - btnSize) / 2
                    params.width = expandedSize
                    params.height = expandedSize
                    params.x = savedCenterX - offset
                    params.y = savedCenterY - offset
                } else {
                    params.width = btnSize
                    params.height = btnSize
                    params.x = savedCenterX
                    params.y = savedCenterY
                }
                windowManager.updateViewLayout(view, params)
            }
        )
        windowManager.addView(radialView, params)
    }

    private fun loadPreferencesAndUpdate() {
        val prefs = getSharedPreferences("radial_prefs", Context.MODE_PRIVATE)
        val keysStr = prefs.getString("keys", "1,2,3,4,5,6") ?: "1,2,3,4,5,6"
        val btnDp = prefs.getInt("btn_size", 70).coerceAtLeast(30)
        val radiusDp = prefs.getInt("menu_radius", 160).coerceAtLeast(80)
        val opacity = prefs.getInt("opacity", 80).coerceIn(10, 100)
        val mainHex = prefs.getString("main_color", "#222222") ?: "#222222"
        val accentHex = prefs.getString("accent_color", "#FF5722") ?: "#FF5722"
        shellCmdTemplate = prefs.getString("shell_cmd", "input keyevent %d") ?: "input keyevent %d"

        radialView?.let { view ->
            view.keys = keysStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            view.buttonSizePx = dpToPx(btnDp).toFloat()
            view.menuRadiusPx = dpToPx(radiusDp).toFloat()
            view.alphaRatio = opacity / 100f
            try { view.mainColor = Color.parseColor(mainHex) } catch (_: Exception) {}
            try { view.accentColor = Color.parseColor(accentHex) } catch (_: Exception) {}
            params.width = view.buttonSizePx.toInt()
            params.height = view.buttonSizePx.toInt()
            windowManager.updateViewLayout(view, params)
            view.invalidate()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        radialView?.let { windowManager.removeView(it) }
    }
}
