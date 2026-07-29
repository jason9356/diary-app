package com.personaldiary.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local-first native todos (Vault schema v1).
 * File: todos/todos.json → { updated_at, items: [...] }
 * Legacy bare array is migrated on read.
 */
class NativeTodoStore(dataRoot: File) {
    private val file = File(dataRoot, "todos/todos.json")

    fun list(): List<NativeTodo> =
        readDoc().items.sortedWith(compareBy<NativeTodo> { it.done }.thenByDescending { it.updatedAt })

    fun documentUpdatedAt(): String = readDoc().updatedAt

    fun add(text: String): NativeTodo {
        val now = DiaryDates.nowIso()
        val todo = NativeTodo(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            createdAt = now,
            updatedAt = now,
        )
        val doc = readDoc()
        writeDoc(TodoDocument(updatedAt = now, items = doc.items + todo))
        return todo
    }

    fun upsert(todo: NativeTodo): NativeTodo {
        val now = DiaryDates.nowIso()
        val saved = todo.copy(updatedAt = now)
        val doc = readDoc()
        val items = doc.items.toMutableList()
        val idx = items.indexOfFirst { it.id == saved.id }
        if (idx >= 0) items[idx] = saved else items += saved
        writeDoc(TodoDocument(updatedAt = now, items = items))
        return saved
    }

    fun setDone(id: String, done: Boolean): NativeTodo? {
        val doc = readDoc()
        val idx = doc.items.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = doc.items[idx].copy(done = done, updatedAt = DiaryDates.nowIso())
        val items = doc.items.toMutableList().also { it[idx] = updated }
        writeDoc(TodoDocument(updatedAt = updated.updatedAt, items = items))
        return updated
    }

    fun delete(id: String) {
        val doc = readDoc()
        writeDoc(TodoDocument(updatedAt = DiaryDates.nowIso(), items = doc.items.filterNot { it.id == id }))
    }

    fun replaceAll(items: List<NativeTodo>) {
        writeDoc(
            TodoDocument(
                updatedAt = items.maxOfOrNull { it.updatedAt }.orEmpty().ifBlank { DiaryDates.nowIso() },
                items = items,
            ),
        )
    }

    fun readRaw(): String {
        val doc = readDoc()
        if (!file.isFile || file.readText(Charsets.UTF_8).trimStart().startsWith("[")) {
            writeDoc(doc)
        }
        return if (file.isFile) file.readText(Charsets.UTF_8) else encodeDoc(TodoDocument("", emptyList()))
    }

    fun writeRaw(json: String) {
        writeDoc(parseDoc(json))
    }

    /** Apply remote todos payload (`json` field) with server document timestamp. */
    fun applyRemote(jsonField: String, documentUpdatedAt: String) {
        val parsed = parseDoc(jsonField)
        writeDoc(
            TodoDocument(
                updatedAt = documentUpdatedAt.ifBlank { parsed.updatedAt },
                items = parsed.items,
            ),
        )
    }

    private data class TodoDocument(
        val updatedAt: String,
        val items: List<NativeTodo>,
    )

    private fun readDoc(): TodoDocument {
        if (!file.isFile) return TodoDocument(updatedAt = "", items = emptyList())
        return try {
            parseDoc(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            TodoDocument(updatedAt = "", items = emptyList())
        }
    }

    private fun writeDoc(doc: TodoDocument) {
        file.parentFile?.mkdirs()
        file.writeText(encodeDoc(doc), Charsets.UTF_8)
    }

    companion object {
        private fun parseDoc(raw: String): TodoDocument {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return TodoDocument("", emptyList())
            val root = when {
                trimmed.startsWith("[") ->
                    JSONObject()
                        .put("updated_at", "")
                        .put("items", JSONArray(trimmed))
                else -> JSONObject(trimmed)
            }
            if (root.has("json") && !root.has("items")) {
                val inner = root.opt("json")
                val innerObj = when (inner) {
                    is JSONObject -> inner
                    is String -> {
                        val s = inner.trim()
                        when {
                            s.startsWith("[") ->
                                JSONObject()
                                    .put("updated_at", root.optString("updated_at"))
                                    .put("items", JSONArray(s))
                            s.startsWith("{") -> JSONObject(s)
                            else -> JSONObject().put("items", JSONArray())
                        }
                    }
                    else -> JSONObject().put("items", JSONArray())
                }
                if (innerObj.has("items")) {
                    val items = parseItems(innerObj.optJSONArray("items") ?: JSONArray())
                    val updated = root.optString("updated_at")
                        .ifBlank { innerObj.optString("updated_at") }
                        .ifBlank { items.maxOfOrNull { it.updatedAt }.orEmpty() }
                    return TodoDocument(updated, items)
                }
            }
            val items = parseItems(root.optJSONArray("items") ?: JSONArray())
            val updated = root.optString("updated_at").ifBlank {
                items.maxOfOrNull { it.updatedAt }.orEmpty()
            }
            return TodoDocument(updated, items)
        }

        private fun parseItems(arr: JSONArray): List<NativeTodo> =
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val due = o.opt("due_at")
                    val dueAt = when {
                        due == null || due == JSONObject.NULL -> ""
                        else -> due.toString().trim().let { if (it == "null") "" else it }
                    }
                    add(
                        NativeTodo(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = o.optString("text"),
                            detail = o.optString("detail"),
                            done = o.optBoolean("done", false),
                            kind = o.optString("kind", "task").ifBlank { "task" },
                            dueAt = dueAt,
                            priority = o.optInt("priority", 0).coerceIn(0, 3),
                            urgency = o.optInt("urgency", 0).coerceIn(0, 3),
                            createdAt = o.optString("created_at"),
                            updatedAt = o.optString("updated_at"),
                        ),
                    )
                }
            }
    }

    private fun encodeDoc(doc: TodoDocument): String {
        val arr = JSONArray()
        for (t in doc.items) {
            val o = JSONObject()
                .put("id", t.id)
                .put("text", t.text)
                .put("detail", t.detail)
                .put("done", t.done)
                .put("kind", t.kind.ifBlank { "task" })
                .put("priority", t.priority.coerceIn(0, 3))
                .put("urgency", t.urgency.coerceIn(0, 3))
                .put("created_at", t.createdAt)
                .put("updated_at", t.updatedAt)
            if (t.dueAt.isBlank()) o.put("due_at", JSONObject.NULL) else o.put("due_at", t.dueAt)
            arr.put(o)
        }
        return JSONObject()
            .put("updated_at", doc.updatedAt)
            .put("items", arr)
            .toString(2)
    }

    private fun parseDoc(raw: String): TodoDocument = Companion.parseDoc(raw)
}
