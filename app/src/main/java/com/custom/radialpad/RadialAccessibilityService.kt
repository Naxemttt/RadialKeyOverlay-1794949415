package com.custom.radialpad

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class RadialAccessibilityService : AccessibilityService() {

    private data class OverlayInstance(
        val config: RadialButtonConfig,
        val view: RadialMenuView,
        val params: WindowManager.LayoutParams
    )

    private lateinit var windowManager: WindowManager
    private var wheelRenderOverlay: WheelRenderOverlayView? = null
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
        wheelRenderOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        wheelRenderOverlay = null
    }

    private fun rebuildOverlays() {
        clearAllOverlays()
        val (activeIdx, profiles) = ConfigManager.loadAll(this)
        val activeProfile = profiles[activeIdx]
        currentShellCmd = activeProfile.shellCmd

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val renderView = WheelRenderOverlayView(this).apply {
            visibility = View.GONE
        }
        val renderParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }

        activeProfile.buttons.forEach { btnConfig ->
            val btnSize = dpToEvenPx(btnConfig.buttonSizeDp)
            val params = WindowManager.LayoutParams(
                btnSize, btnSize,
                overlayType,
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
                wheelRenderer = renderView,
                onKeySelected = { keySpec ->
                    KeyInjector.triggerKey(this, keySpec, currentShellCmd)
                },
                onMoveOverlay = { dx, dy ->
                    btnConfig.x += dx
                    btnConfig.y += dy
                    params.x = btnConfig.x
                    params.y = btnConfig.y
                    windowManager.updateViewLayout(radialView, params)
                },
                onMoveFinished = {
                    // Sauvegarde persistante des nouvelles coordonnées X/Y dès qu'on relâche le déplacement
                    val (latestIdx, latestProfiles) = ConfigManager.loadAll(this)
                    val targetBtn = latestProfiles.getOrNull(latestIdx)?.buttons?.find { it.id == btnConfig.id }
                    if (targetBtn != null) {
                        targetBtn.x = btnConfig.x
                        targetBtn.y = btnConfig.y
                        ConfigManager.saveAll(this, latestIdx, latestProfiles)
                    } else {
                        ConfigManager.saveAll(this, activeIdx, profiles)
                    }
                }
            )

            radialView.syncFromConfig(resources.displayMetrics.density)
            windowManager.addView(radialView, params)
            overlays.add(OverlayInstance(btnConfig, radialView, params))
        }

        // Ajouté en dernier pour être dessiné au-dessus de tous les boutons quand activé
        windowManager.addView(renderView, renderParams)
        wheelRenderOverlay = renderView
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        clearAllOverlays()
    }
}
