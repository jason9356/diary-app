package com.sparkbox.android

import android.app.Application
import com.sparkbox.android.data.NativeTodoStore
import com.sparkbox.android.data.SparkboxRepository
import java.io.File

class SparkboxApplication : Application() {
    lateinit var repository: SparkboxRepository
        private set
    lateinit var todoStore: NativeTodoStore
        private set

    override fun onCreate() {
        super.onCreate()
        val root = resolveVaultRoot()
        repository = SparkboxRepository(root)
        todoStore = NativeTodoStore(root)
    }

    /** Prefer `vault/`; migrate legacy `diary_data/` once if present. */
    private fun resolveVaultRoot(): File {
        val vault = File(filesDir, "vault")
        val legacy = File(filesDir, "diary_data")
        if (!vault.exists() && legacy.isDirectory) {
            if (!legacy.renameTo(vault)) {
                // Fallback: keep using legacy path if rename fails
                return legacy.also { it.mkdirs() }
            }
        }
        return vault.also { it.mkdirs() }
    }
}
