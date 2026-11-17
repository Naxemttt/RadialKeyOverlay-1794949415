package com.custom.radialpad

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class RadialAccessibilityService : AccessibilityService() {

    private data class OverlayInstance(
        val config: RadialButtonConfig,
        val view: RadialMenuView,
        val params: WindowManager.LayoutParams
    )

    private lateinit var windowManager: WindowManager
    private val overlays = mutableListOf<OverlayInstance>()
    private var currentShellCmd = "input keyevent %d"

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rebuildOverlays()
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
        rebuildOverlays()
    }

    private fun dpToEvenPx(dp: Int): Int {
        val px = (dp * resources.displayMetrics.density).toInt()
        return if (px % 2 == 0) px else px + 1
    }

    private fun clearAllOverlays() {
        overlays.forEach {
            try { windowManager.removeView(it.view) } catch (_: Exception) {}
        }
        overlays.clear()
    }

    private fun rebuildOverlays() {
        clearAllOverlays()
        val (activeIdx, profiles) = ConfigManager.loadAll(this)
        val activeProfile = profiles[activeIdx]
        currentShellCmd = activeProfile.shellCmd

        activeProfile.buttons.forEach { btnConfig ->
            val btnSize = dpToEvenPx(btnConfig.buttonSizeDp)
            val params = WindowManager.LayoutParams(
                btnSize, btnSize,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = btnConfig.x
                y = btnConfig.y
                windowAnimations = 0
            }

            lateinit var radialView: RadialMenuView
            radialView = RadialMenuView(
                context = this,
                config = btnConfig,
                onKeySelected = { keySpec ->
                    KeyInjector.triggerKey(this, keySpec, currentShellCmd)
                },
                onMoveOverlay = { dx, dy ->
                    btnConfig.x += dx
                    btnConfig.y += dy
                    params.x = btnConfig.x
                    params.y = btnConfig.y
                    windowManager.updateViewLayout(radialView, params)
                    ConfigManager.saveAll(this, activeIdx, profiles)
                },
                onExpandChanged = { expanded ->
                    val collapsedSize = radialView.buttonSizePx.toInt()
                    var expandedSize = (radialView.getMaxActiveRadiusPx() * 2.12f).toInt()
                    if (expandedSize % 2 != 0) expandedSize += 1

                    if (expanded) {
                        val offset = (expandedSize - collapsedSize) / 2
                        params.x = btnConfig.x - offset
                        params.y = btnConfig.y - offset
                        params.width = expandedSize
                        params.height = expandedSize
                    } else {
                        params.x = btnConfig.x
                        params.y = btnConfig.y
                        params.width = collapsedSize
                        params.height = collapsedSize
                    }
                    windowManager.updateViewLayout(radialView, params)
                }
            )

            radialView.syncFromConfig(resources.displayMetrics.density)
            windowManager.addView(radialView, params)
            overlays.add(OverlayInstance(btnConfig, radialView, params))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        clearAllOverlays()
    }
}
