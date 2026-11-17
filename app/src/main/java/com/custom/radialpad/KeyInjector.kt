package com.custom.radialpad

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.concurrent.thread

object KeyInjector {
    // Correspondance vers les Linux Input Event Codes (EV_KEY) utilisés par uinput / X11 / Wine / DroidDeck
    private val linuxKeyMap = mapOf(
        "0" to 11, "1" to 2, "2" to 3, "3" to 4, "4" to 5, "5" to 6,
        "6" to 7, "7" to 8, "8" to 9, "9" to 10,
        "A" to 30, "B" to 48, "C" to 46, "D" to 32, "E" to 18, "F" to 33,
        "G" to 34, "H" to 35, "I" to 23, "J" to 36, "K" to 37, "L" to 38,
        "M" to 50, "N" to 49, "O" to 24, "P" to 25, "Q" to 16, "R" to 19,
        "S" to 31, "T" to 20, "U" to 22, "V" to 47, "W" to 17, "X" to 45,
        "Y" to 21, "Z" to 44,
        "F1" to 59, "F2" to 60, "F3" to 61, "F4" to 62, "F5" to 63, "F6" to 64,
        "F7" to 65, "F8" to 66, "F9" to 67, "F10" to 68, "F11" to 87, "F12" to 88,
        "SPACE" to 57, "ENTER" to 28, "ESC" to 1, "TAB" to 15,
        "BACKSPACE" to 14, "DELETE" to 111,
        "UP" to 103, "DOWN" to 108, "LEFT" to 105, "RIGHT" to 106,
        "SHIFT" to 42, "CTRL" to 29, "ALT" to 56
    )

    private val androidKeyMap = mapOf(
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
        "BACKSPACE" to KeyEvent.KEYCODE_DEL, "DELETE" to KeyEvent.KEYCODE_FORWARD_DEL,
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "SHIFT" to KeyEvent.KEYCODE_SHIFT_LEFT, "CTRL" to KeyEvent.KEYCODE_CTRL_LEFT,
        "ALT" to KeyEvent.KEYCODE_ALT_LEFT
    )

    fun triggerKey(service: AccessibilityService, rawSpec: String, shellTemplate: String) {
        val cleanSpec = rawSpec.trim().uppercase()
        if (cleanSpec.isEmpty()) return

        val parts = cleanSpec.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        val linuxCodes = parts.mapNotNull { linuxKeyMap[it] }
        val androidCodes = parts.mapNotNull { androidKeyMap[it] }

        thread {
            // 1. Méthode Native Linux uinput (Injecte directement un vrai événement clavier matériel sous le capot)
            if (linuxCodes.isNotEmpty()) {
                try {
                    val sb = StringBuilder()
                    // Ouvre ou crée un émetteur uinput temporaire via sendevent ou commande shell root
                    for (code in linuxCodes) {
                        sb.append("sendevent /dev/input/event0 1 $code 1\n") // Key Down
                    }
                    for (code in linuxCodes.reversed()) {
                        sb.append("sendevent /dev/input/event0 1 $code 0\n") // Key Up
                    }
                    // Envoie un événement de synchronisation SYN_REPORT (0 0 0)
                    sb.append("sendevent /dev/input/event0 0 0 0\n")

                    Runtime.getRuntime().exec(arrayOf("su", "-c", sb.toString())).waitFor()
                } catch (_: Exception) {}
            }

            // 2. Fallback intelligent : Commande Shell personnalisée (input keyevent / xdotool natif)
            if (androidCodes.isNotEmpty() && shellTemplate.isNotBlank()) {
                val fullCmd = androidCodes.joinToString(" && ") { code -> String.format(shellTemplate, code) }
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", fullCmd)).waitFor()
                } catch (e: Exception) {
                    try { Runtime.getRuntime().exec(arrayOf("sh", "-c", fullCmd)).waitFor() } catch (_: Exception) {}
                }
            }
        }

        // 3. Fallback Accessibilité pour les champs texte natifs Android
        val root = service.rootInActiveWindow
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && cleanSpec.length == 1 && androidCodes.isNotEmpty()) {
            val currentText = focused.text?.toString() ?: ""
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText + cleanSpec)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}
