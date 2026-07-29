package com.personaldiary.android.data

import android.content.Context

/** App-level preferences. Sync credentials stay in SyncPrefs. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("diary_app", Context.MODE_PRIVATE)

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

    /** Personal Obsidian/COS todo bridge — off for most users. */
    var obsidianTodosEnabled: Boolean
        get() = sp.getBoolean("obsidian_todos_enabled", false)
        set(v) = sp.edit().putBoolean("obsidian_todos_enabled", v).apply()

    var aiEnabled: Boolean
        get() = sp.getBoolean("ai_enabled", false)
        set(v) = sp.edit().putBoolean("ai_enabled", v).apply()

    var s3Endpoint: String
        get() = sp.getString("s3_endpoint", "").orEmpty()
        set(v) = sp.edit().putString("s3_endpoint", v.trim()).apply()

    var s3Region: String
        get() = sp.getString("s3_region", "us-east-1").orEmpty()
        set(v) = sp.edit().putString("s3_region", v.trim().ifBlank { "us-east-1" }).apply()

    var s3Bucket: String
        get() = sp.getString("s3_bucket", "").orEmpty()
        set(v) = sp.edit().putString("s3_bucket", v.trim()).apply()

    var s3AccessKey: String
        get() = sp.getString("s3_access_key", "").orEmpty()
        set(v) = sp.edit().putString("s3_access_key", v.trim()).apply()

    var s3SecretKey: String
        get() = sp.getString("s3_secret_key", "").orEmpty()
        set(v) = sp.edit().putString("s3_secret_key", v.trim()).apply()

    var s3Prefix: String
        get() = sp.getString("s3_prefix", "").orEmpty()
        set(v) = sp.edit().putString("s3_prefix", v.trim()).apply()

    /** Vault-relative diary folder, e.g. 日记 */
    var obsidianDiaryFolder: String
        get() = sp.getString("obsidian_diary_folder", "日记").orEmpty()
        set(v) = sp.edit().putString("obsidian_diary_folder", v.trim()).apply()

    var tagOpen: String
        get() = sp.getString("tag_open", "【").orEmpty().ifBlank { "【" }
        set(v) = sp.edit().putString("tag_open", v).apply()

    var tagClose: String
        get() = sp.getString("tag_close", "】").orEmpty().ifBlank { "】" }
        set(v) = sp.edit().putString("tag_close", v).apply()

    var completedLabel: String
        get() = sp.getString("completed_label", "已完成").orEmpty().ifBlank { "已完成" }
        set(v) = sp.edit().putString("completed_label", v.trim()).apply()

    /** local | sync_server | cloud */
    var storageTarget: String
        get() = sp.getString("storage_target", "local").orEmpty().ifBlank { "local" }
        set(v) {
            val n = when (v.trim().lowercase()) {
                "sync_server", "cloud" -> v.trim().lowercase()
                else -> "local"
            }
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
}
