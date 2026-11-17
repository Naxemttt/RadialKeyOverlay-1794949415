package com.custom.radialpad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class KeySlice(val label: String, val keySpec: String)

data class RadialButtonConfig(
    var id: String = "btn_" + System.currentTimeMillis(),
    var name: String = "Bouton 1",
    var x: Int = 300,
    var y: Int = 500,
    var buttonSizeDp: Int = 68,
    var innerRadiusDp: Int = 145,
    var outerRadiusDp: Int = 225,
    var opacity: Int = 85,
    var mainColor: String = "#222222",
    var accentColor: String = "#FF5722",
    var innerKeys: String = "⚡:1, 🔥:2, 🛡️:3, 🩹:4, 🐺:5, 💥:6",
    var enableOuterRing: Boolean = false,
    var outerKeys: String = "S1:SHIFT+1, S2:SHIFT+2, S3:SHIFT+3, S4:SHIFT+4, S5:SHIFT+5, S6:SHIFT+6",
    var centerTapKey: String = "SPACE",
    var quickFlick: Boolean = false
) {
    fun parseSlices(raw: String): List<KeySlice> {
        return raw.split(",").mapNotNull { item ->
            val trimmed = item.trim()
            if (trimmed.isEmpty()) null
            else if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                KeySlice(parts[0].trim(), parts[1].trim())
            } else {
                KeySlice(trimmed, trimmed)
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("x", x)
        put("y", y)
        put("buttonSizeDp", buttonSizeDp)
        put("innerRadiusDp", innerRadiusDp)
        put("outerRadiusDp", outerRadiusDp)
        put("opacity", opacity)
        put("mainColor", mainColor)
        put("accentColor", accentColor)
        put("innerKeys", innerKeys)
        put("enableOuterRing", enableOuterRing)
        put("outerKeys", outerKeys)
        put("centerTapKey", centerTapKey)
        put("quickFlick", quickFlick)
    }

    companion object {
        fun fromJson(o: JSONObject) = RadialButtonConfig(
            id = o.optString("id", "btn_" + System.currentTimeMillis()),
            name = o.optString("name", "Bouton"),
            x = o.optInt("x", 300),
            y = o.optInt("y", 500),
            buttonSizeDp = o.optInt("buttonSizeDp", 68),
            innerRadiusDp = o.optInt("innerRadiusDp", 145),
            outerRadiusDp = o.optInt("outerRadiusDp", 225),
            opacity = o.optInt("opacity", 85),
            mainColor = o.optString("mainColor", "#222222"),
            accentColor = o.optString("accentColor", "#FF5722"),
            innerKeys = o.optString("innerKeys", "1,2,3,4,5,6"),
            enableOuterRing = o.optBoolean("enableOuterRing", false),
            outerKeys = o.optString("outerKeys", "F1,F2,F3,F4,F5,F6"),
            centerTapKey = o.optString("centerTapKey", ""),
            quickFlick = o.optBoolean("quickFlick", false)
        )
    }
}

data class RadialProfile(
    var name: String = "Profil Principal",
    var shellCmd: String = "input keyevent %d",
    var buttons: MutableList<RadialButtonConfig> = mutableListOf(RadialButtonConfig())
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("shellCmd", shellCmd)
        val arr = JSONArray()
        buttons.forEach { arr.put(it.toJson()) }
        put("buttons", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): RadialProfile {
            val btns = mutableListOf<RadialButtonConfig>()
            val arr = o.optJSONArray("buttons")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    btns.add(RadialButtonConfig.fromJson(arr.getJSONObject(i)))
                }
            }
            if (btns.isEmpty()) btns.add(RadialButtonConfig())
            return RadialProfile(
                name = o.optString("name", "Profil"),
                shellCmd = o.optString("shellCmd", "input keyevent %d"),
                buttons = btns
            )
        }
    }
}

object ConfigManager {
    private const val PREF_NAME = "radial_v2_prefs"
    private const val KEY_DATA = "profiles_json"

    fun loadAll(context: Context): Pair<Int, MutableList<RadialProfile>> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DATA, null)
        if (raw.isNullOrBlank()) {
            return 0 to mutableListOf(RadialProfile())
        }
        return try {
            val root = JSONObject(raw)
            val activeIdx = root.optInt("activeIndex", 0)
            val arr = root.optJSONArray("profiles")
            val list = mutableListOf<RadialProfile>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list.add(RadialProfile.fromJson(arr.getJSONObject(i)))
                }
            }
            if (list.isEmpty()) list.add(RadialProfile())
            activeIdx.coerceIn(0, list.size - 1) to list
        } catch (e: Exception) {
            0 to mutableListOf(RadialProfile())
        }
    }

    fun saveAll(context: Context, activeIndex: Int, profiles: List<RadialProfile>) {
        val root = JSONObject()
        root.put("activeIndex", activeIndex.coerceIn(0, (profiles.size - 1).coerceAtLeast(0)))
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        root.put("profiles", arr)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DATA, root.toString()).apply()
    }
}
