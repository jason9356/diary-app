package com.personaldiary.android.data

import android.content.Context

/** App-level preferences. Sync credentials stay in SyncPrefs. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("diary_app", Context.MODE_PRIVATE)

    var editorFontSp: Float
        get() = sp.getFloat("editor_font_sp", 17f).coerceIn(14f, 24f)
        set(v) = sp.edit().putFloat("editor_font_sp", v.coerceIn(14f, 24f)).apply()

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
}
