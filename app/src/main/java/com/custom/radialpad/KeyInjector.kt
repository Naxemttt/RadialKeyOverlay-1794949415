package com.custom.radialpad

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

object KeyInjector {
    private val keyMap = mapOf(
        "0" to KeyEvent.KEYCODE_0, "1" to KeyEvent.KEYCODE_1, "2" to KeyEvent.KEYCODE_2,
        "3" to KeyEvent.KEYCODE_3, "4" to KeyEvent.KEYCODE_4, "5" to KeyEvent.KEYCODE_5,
        "6" to KeyEvent.KEYCODE_6, "7" to KeyEvent.KEYCODE_7, "8" to KeyEvent.KEYCODE_8,
        "9" to KeyEvent.KEYCODE_9,
        "A" to KeyEvent.KEYCODE_A, "B" to KeyEvent.KEYCODE_B, "C" to KeyEvent.KEYCODE_C,
        "D" to KeyEvent.KEYCODE_D, "E" to KeyEvent.KEYCODE_E, "F" to KeyEvent.KEYCODE_F,
        "G" to KeyEvent.KEYCODE_G, "H" to KeyEvent.KEYCODE_H, "I" to KeyEvent.KEYCODE_I,
        "J" to KeyEvent.KEYCODE_J, "K" to KeyEvent.KEYCODE_K, "L" to KeyEvent.KEYCODE_L,
        "M" to KeyEvent.KEYCODE_M, "N" to KeyEvent.KEYCODE_N, "O" to KeyEvent.KEYCODE_O,
        "P" to KeyEvent.KEYCODE_P, "Q" to KeyEvent.KEYCODE_Q, "R" to KeyEvent.KEYCODE_R,
        "S" to KeyEvent.KEYCODE_S, "T" to KeyEvent.KEYCODE_T, "U" to KeyEvent.KEYCODE_U,
        "V" to KeyEvent.KEYCODE_V, "W" to KeyEvent.KEYCODE_W, "X" to KeyEvent.KEYCODE_X,
        "Y" to KeyEvent.KEYCODE_Y, "Z" to KeyEvent.KEYCODE_Z,
        "F1" to KeyEvent.KEYCODE_F1, "F2" to KeyEvent.KEYCODE_F2, "F3" to KeyEvent.KEYCODE_F3,
        "F4" to KeyEvent.KEYCODE_F4, "F5" to KeyEvent.KEYCODE_F5, "F6" to KeyEvent.KEYCODE_F6,
        "F7" to KeyEvent.KEYCODE_F7, "F8" to KeyEvent.KEYCODE_F8, "F9" to KeyEvent.KEYCODE_F9,
        "F10" to KeyEvent.KEYCODE_F10, "F11" to KeyEvent.KEYCODE_F11, "F12" to KeyEvent.KEYCODE_F12,
        "SPACE" to KeyEvent.KEYCODE_SPACE, "ENTER" to KeyEvent.KEYCODE_ENTER,
        "ESC" to KeyEvent.KEYCODE_ESCAPE, "TAB" to KeyEvent.KEYCODE_TAB,
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "SHIFT" to KeyEvent.KEYCODE_SHIFT_LEFT, "CTRL" to KeyEvent.KEYCODE_CTRL_LEFT,
        "ALT" to KeyEvent.KEYCODE_ALT_LEFT
    )

    fun triggerKey(service: AccessibilityService, label: String, shellTemplate: String) {
        val cleanLabel = label.trim().uppercase()
        val keyCode = keyMap[cleanLabel] ?: KeyEvent.KEYCODE_UNKNOWN

        thread {
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN && shellTemplate.isNotBlank()) {
                val cmd = String.format(shellTemplate, keyCode)
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
                } catch (e: Exception) {
                    try { Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).waitFor() } catch (_: Exception) {}
                }
            }
            try {
                val socket = DatagramSocket()
                val payload = "KEY:$cleanLabel:$keyCode".toByteArray()
                val packet = DatagramPacket(payload, payload.size, InetAddress.getByName("127.0.0.1"), 9999)
                socket.send(packet)
                socket.close()
            } catch (_: Exception) {}
        }

        val root = service.rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && cleanLabel.length == 1) {
            val currentText = focused.text?.toString() ?: ""
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText + cleanLabel)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}
