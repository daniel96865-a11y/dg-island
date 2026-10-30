package de.dgstudios.island

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)
    var enabled get() = sp.getBoolean("enabled", true); set(v) = sp.edit().putBoolean("enabled", v).apply()
    var offsetX get() = sp.getInt("offsetX", 0); set(v) = sp.edit().putInt("offsetX", v).apply()
    var offsetY get() = sp.getInt("offsetY", 8); set(v) = sp.edit().putInt("offsetY", v).apply()
    var width get() = sp.getInt("width", 120); set(v) = sp.edit().putInt("width", v).apply()
    var height get() = sp.getInt("height", 34); set(v) = sp.edit().putInt("height", v).apply()
    var radius get() = sp.getInt("radius", 17); set(v) = sp.edit().putInt("radius", v).apply()
    var showIdle get() = sp.getBoolean("showIdle", true); set(v) = sp.edit().putBoolean("showIdle", v).apply()
    var hideLandscape get() = sp.getBoolean("hideLandscape", true); set(v) = sp.edit().putBoolean("hideLandscape", v).apply()
    var hideFullscreen get() = sp.getBoolean("hideFullscreen", true); set(v) = sp.edit().putBoolean("hideFullscreen", v).apply()
    var autostart get() = sp.getBoolean("autostart", true); set(v) = sp.edit().putBoolean("autostart", v).apply()
    var showMedia get() = sp.getBoolean("showMedia", true); set(v) = sp.edit().putBoolean("showMedia", v).apply()
    var showCharging get() = sp.getBoolean("showCharging", true); set(v) = sp.edit().putBoolean("showCharging", v).apply()
    var onboarded get() = sp.getBoolean("onboarded", false); set(v) = sp.edit().putBoolean("onboarded", v).apply()
    var lastSeenVersion get() = sp.getString("lastSeenVersion", "") ?: ""; set(v) = sp.edit().putString("lastSeenVersion", v).apply()
    var blocked: Set<String>
        get() = sp.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("blocked", v).apply()
}
