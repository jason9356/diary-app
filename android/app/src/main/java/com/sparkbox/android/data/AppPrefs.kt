package com.sparkbox.android.data

import android.content.Context

/** App-level preferences (theme, vault storage target, cloud credentials). */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { prefs ->
        migrateFromLegacyIfNeeded(context, prefs)
    }

    var editorFontSp: Float
        get() = sp.getFloat("editor_font_sp", 17f).coerceIn(14f, 24f)
        set(v) = sp.edit().putFloat("editor_font_sp", v.coerceIn(14f, 24f)).apply()

    /** system | light | dark */
    var themeMode: String
        get() = sp.getString("theme_mode", "system").orEmpty().ifBlank { "system" }
        set(v) {
            val normalized = when (v.trim().lowercase()) {
                "light", "dark" -> v.trim().lowercase()
                else -> "system"
            }
            sp.edit().putString("theme_mode", normalized).apply()
        }

    /** slip | moss | spark | paper */
    var themePalette: String
        get() = sp.getString("theme_palette", "slip").orEmpty().ifBlank { "slip" }
        set(v) = sp.edit().putString("theme_palette", v.trim().ifBlank { "slip" }).apply()

    var demoSeeded: Boolean
        get() = sp.getBoolean("demo_seeded_v1", false)
        set(v) = sp.edit().putBoolean("demo_seeded_v1", v).apply()

    var aiEnabled: Boolean
        get() = sp.getBoolean("ai_enabled", false)
        set(v) = sp.edit().putBoolean("ai_enabled", v).apply()

    /** local | cloud */
    var storageTarget: String
        get() {
            val raw = sp.getString("storage_target", "local").orEmpty()
            return when (raw.trim().lowercase()) {
                "cloud" -> "cloud"
                else -> "local" // migrates legacy sync_server → local
            }
        }
        set(v) {
            val n = if (v.trim().lowercase() == "cloud") "cloud" else "local"
            sp.edit().putString("storage_target", n).apply()
        }

    /** webdav | baidu | onedrive | google_drive | aliyun_drive */
    var cloudProvider: String
        get() = sp.getString("cloud_provider", "webdav").orEmpty().ifBlank { "webdav" }
        set(v) = sp.edit().putString("cloud_provider", v.trim().ifBlank { "webdav" }).apply()

    var webdavUrl: String
        get() = sp.getString("webdav_url", "").orEmpty()
        set(v) = sp.edit().putString("webdav_url", v.trim()).apply()

    var webdavUser: String
        get() = sp.getString("webdav_user", "").orEmpty()
        set(v) = sp.edit().putString("webdav_user", v.trim()).apply()

    var webdavPass: String
        get() = sp.getString("webdav_pass", "").orEmpty()
        set(v) = sp.edit().putString("webdav_pass", v).apply()

    var webdavRoot: String
        get() = sp.getString("webdav_root", "/sparkbox").orEmpty().ifBlank { "/sparkbox" }
        set(v) = sp.edit().putString("webdav_root", v.trim().ifBlank { "/sparkbox" }).apply()

    /** Reserved slots for future OAuth clouds */
    var cloudEndpoint: String
        get() = sp.getString("cloud_endpoint", "").orEmpty()
        set(v) = sp.edit().putString("cloud_endpoint", v.trim()).apply()

    var cloudAppKey: String
        get() = sp.getString("cloud_app_key", "").orEmpty()
        set(v) = sp.edit().putString("cloud_app_key", v.trim()).apply()

    var cloudToken: String
        get() = sp.getString("cloud_token", "").orEmpty()
        set(v) = sp.edit().putString("cloud_token", v.trim()).apply()

    companion object {
        private const val PREFS_NAME = "sparkbox_prefs"
        private const val LEGACY_PREFS = "diary_app"

        private fun migrateFromLegacyIfNeeded(context: Context, prefs: android.content.SharedPreferences) {
            if (prefs.getBoolean("prefs_migrated_v1", false)) return
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            val ed = prefs.edit()
            if (legacy.all.isNotEmpty()) {
                for ((k, v) in legacy.all) {
                    when (v) {
                        is String -> ed.putString(k, v)
                        is Boolean -> ed.putBoolean(k, v)
                        is Float -> ed.putFloat(k, v)
                        is Int -> ed.putInt(k, v)
                        is Long -> ed.putLong(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            ed.putStringSet(k, v as Set<String>)
                        }
                    }
                }
            }
            ed.putBoolean("prefs_migrated_v1", true).apply()
        }
    }
}
