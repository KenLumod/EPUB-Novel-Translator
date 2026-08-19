package com.example.epubnoveltranslator.data

import android.content.Context

/** Small persistent preferences that do not belong to an individual novel. */
class UserPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "user_preferences",
        Context.MODE_PRIVATE
    )

    var meaningSearchEnabled: Boolean
        get() = preferences.getBoolean(KEY_MEANING_SEARCH_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_MEANING_SEARCH_ENABLED, value).apply()

    var readerFontSizeSp: Float
        get() = preferences.getFloat(KEY_READER_FONT_SIZE, 16f)
        set(value) = preferences.edit().putFloat(KEY_READER_FONT_SIZE, value).apply()

    var readerFontFamily: String
        get() = preferences.getString(KEY_READER_FONT_FAMILY, "serif") ?: "serif"
        set(value) = preferences.edit().putString(KEY_READER_FONT_FAMILY, value).apply()

    var readerFontColor: Int
        get() = preferences.getInt(KEY_READER_FONT_COLOR, 0xFFBDE8F5.toInt())
        set(value) = preferences.edit().putInt(KEY_READER_FONT_COLOR, value).apply()

    private companion object {
        const val KEY_MEANING_SEARCH_ENABLED = "meaning_search_enabled"
        const val KEY_READER_FONT_SIZE = "reader_font_size"
        const val KEY_READER_FONT_FAMILY = "reader_font_family"
        const val KEY_READER_FONT_COLOR = "reader_font_color"
    }
}
