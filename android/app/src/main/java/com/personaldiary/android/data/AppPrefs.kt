package com.personaldiary.android.data

import android.content.Context

/** App-level preferences (font, etc.). Sync credentials stay in SyncPrefs. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("diary_app", Context.MODE_PRIVATE)

    /** Editor body font size in sp, roughly 14–24. */
    var editorFontSp: Float
        get() = sp.getFloat("editor_font_sp", 17f).coerceIn(14f, 24f)
        set(v) = sp.edit().putFloat("editor_font_sp", v.coerceIn(14f, 24f)).apply()
}
