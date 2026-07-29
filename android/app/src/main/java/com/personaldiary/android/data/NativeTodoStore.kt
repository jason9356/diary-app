package com.personaldiary.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local-first native todos (灵感匣自建待办). */
class NativeTodoStore(dataRoot: File) {
    private val file = File(dataRoot, "todos/todos.json")

    fun list(): List<NativeTodo> {
        if (!file.isFile) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NativeTodo(
                            id = o.optString("id"),
                            text = o.optString("text"),
                            done = o.optBoolean("done", false),
                            createdAt = o.optString("created_at"),
                            updatedAt = o.optString("updated_at"),
                        )
                    )
                }
            }.sortedWith(compareBy<NativeTodo> { it.done }.thenByDescending { it.updatedAt })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(text: String): NativeTodo {
        val now = DiaryDates.nowIso()
        val todo = NativeTodo(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            done = false,
            createdAt = now,
            updatedAt = now,
        )
        saveAll(list() + todo)
        return todo
    }

    fun setDone(id: String, done: Boolean): NativeTodo? {
        val items = list().toMutableList()
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = items[idx].copy(done = done, updatedAt = DiaryDates.nowIso())
        items[idx] = updated
        saveAll(items)
        return updated
    }

    fun delete(id: String) {
        saveAll(list().filterNot { it.id == id })
    }

    fun replaceAll(items: List<NativeTodo>) {
        saveAll(items)
    }

    fun readRaw(): String =
        if (file.isFile) file.readText(Charsets.UTF_8) else "[]"

    fun writeRaw(json: String) {
        file.parentFile?.mkdirs()
        file.writeText(json, Charsets.UTF_8)
    }

    private fun saveAll(items: List<NativeTodo>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        for (t in items) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("text", t.text)
                    .put("done", t.done)
                    .put("created_at", t.createdAt)
                    .put("updated_at", t.updatedAt),
            )
        }
        file.writeText(arr.toString(2), Charsets.UTF_8)
    }
}
