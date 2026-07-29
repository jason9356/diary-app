package com.personaldiary.android

import android.app.Application
import com.personaldiary.android.data.DiaryRepository
import com.personaldiary.android.data.NativeTodoStore
import java.io.File

class DiaryApplication : Application() {
    lateinit var repository: DiaryRepository
        private set
    lateinit var todoStore: NativeTodoStore
        private set

    override fun onCreate() {
        super.onCreate()
        val root = File(filesDir, "diary_data")
        repository = DiaryRepository(root)
        todoStore = NativeTodoStore(root)
    }
}
