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
        var config: RadialButtonConfig,
        val view: RadialMenuView,
        val params: WindowManager.LayoutParams
    )

    private lateinit var windowManager: WindowManager
    private var wheelRenderOverlay: WheelRenderOverlayView? = null
    private val overlays = mutableListOf<OverlayInstance>()
    private var currentShellCmd = "input keyevent %d"

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncOverlaysInPlace()
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
        syncOverlaysInPlace()
    }

    private fun dpToEvenPx(dp: Int): Int {
        val px = (dp * resources.displayMetrics.density).toInt()
        return if (px % 2 == 0) px else px + 1
    }

    private fun ensureRenderOverlay(): WheelRenderOverlayView {
        val existing = wheelRenderOverlay
        if (existing != null) return existing

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
        windowManager.addView(renderView, renderParams)
        wheelRenderOverlay = renderView
        return renderView
    }

    // Met à jour les boutons existants sans les supprimer (Zéro clignotement !)
    private fun syncOverlaysInPlace() {
        val (activeIdx, profiles) = ConfigManager.loadAll(this)
        val activeProfile = profiles[activeIdx]
        currentShellCmd = activeProfile.shellCmd

        val renderView = ensureRenderOverlay()
        val desiredConfigs = activeProfile.buttons
        val desiredIds = desiredConfigs.map { it.id }.toSet()

        // 1. Supprime uniquement les boutons qui n'existent plus dans ce profil
        val iterator = overlays.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.config.id !in desiredIds) {
                try { windowManager.removeView(item.view) } catch (_: Exception) {}
                iterator.remove()
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // 2. Met à jour les boutons existants ou ajoute les nouveaux
        desiredConfigs.forEach { btnConfig ->
            val existing = overlays.find { it.config.id == btnConfig.id }
            val btnSize = dpToEvenPx(btnConfig.buttonSizeDp)

            if (existing != null) {
                existing.config = btnConfig
                existing.view.config = btnConfig
                existing.view.syncFromConfig(resources.displayMetrics.density)

                if (!existing.view.isMoveMode && !existing.view.isExpanded) {
                    var changed = false
                    if (existing.params.width != btnSize || existing.params.height != btnSize) {
                        existing.params.width = btnSize
                        existing.params.height = btnSize
                        changed = true
                    }
                    if (existing.params.x != btnConfig.x || existing.params.y != btnConfig.y) {
                        existing.params.x = btnConfig.x
                        existing.params.y = btnConfig.y
                        changed = true
                    }
                    if (changed) {
                        windowManager.updateViewLayout(existing.view, existing.params)
                    }
                }
            } else {
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
                        radialView.config.x += dx
                        radialView.config.y += dy
                        params.x = radialView.config.x
                        params.y = radialView.config.y
                        windowManager.updateViewLayout(radialView, params)
                    },
                    onMoveFinished = {
                        val (latestIdx, latestProfiles) = ConfigManager.loadAll(this)
                        val targetBtn = latestProfiles.getOrNull(latestIdx)?.buttons?.find { it.id == radialView.config.id }
                        if (targetBtn != null) {
                            targetBtn.x = radialView.config.x
                            targetBtn.y = radialView.config.y
                            ConfigManager.saveAll(this, latestIdx, latestProfiles)
                        }
                    }
                )

                radialView.syncFromConfig(resources.displayMetrics.density)
                windowManager.addView(radialView, params)
                overlays.add(OverlayInstance(btnConfig, radialView, params))
            }
        }
        renderView.invalidate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        overlays.forEach {
            try { windowManager.removeView(it.view) } catch (_: Exception) {}
        }
        overlays.clear()
        wheelRenderOverlay?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        wheelRenderOverlay = null
    }
}
