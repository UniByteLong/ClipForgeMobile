package com.clipforge.mobile.settings

import android.content.Context

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("clipforge_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    var modelName: String
        get() = prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(value) = prefs.edit().putString("gemini_model", value.trim()).apply()

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()
}
