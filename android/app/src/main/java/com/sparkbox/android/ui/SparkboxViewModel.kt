package com.sparkbox.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sparkbox.android.SparkboxApplication
import com.sparkbox.android.ai.AiHooks
import com.sparkbox.android.ai.NoOpAiHooks
import com.sparkbox.android.data.DayContext
import com.sparkbox.android.data.DemoSeed
import com.sparkbox.android.data.SparkDates
import com.sparkbox.android.data.SparkEntry
import com.sparkbox.android.data.SparkboxRepository
import com.sparkbox.android.data.NativeTodo
import com.sparkbox.android.data.NativeTodoStore
import com.sparkbox.android.data.AppPrefs
import com.sparkbox.android.sync.VaultMirror
import com.sparkbox.android.sync.WebDavClient
import com.sparkbox.android.sync.WebDavConfig
import com.sparkbox.android.weather.LocationHelper
import com.sparkbox.android.weather.PlaceResolver
import com.sparkbox.android.weather.WeatherService
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SparkboxUiState(
    val entry: SparkEntry? = null,
    val dayContext: DayContext = DayContext(date = SparkDates.today()),
    val notes: List<SparkEntry> = emptyList(),
    val filteredNotes: List<SparkEntry> = emptyList(),
    val allTags: List<String> = emptyList(),
    val filterQuery: String = "",
    val filterDate: String = "",
    val filterTag: String = "",
    val nativeTodos: List<NativeTodo> = emptyList(),
    val selectedDate: String = SparkDates.today(),
    val weatherLoading: Boolean = false,
    val syncing: Boolean = false,
    val status: String = "",
    val needLocationPermission: Boolean = false,
    val editorFontSp: Float = 17f,
    /** system | light | dark */
    val themeMode: String = "system",
    /** slip | moss | spark | paper */
    val themePalette: String = "slip",
    val storageTarget: String = "local",
    val cloudProvider: String = "webdav",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPass: String = "",
    val webdavRoot: String = "/sparkbox",
    val cloudEndpoint: String = "",
    val cloudAppKey: String = "",
    val cloudToken: String = "",
    val aiEnabled: Boolean = false,
    val aiPreview: String = "",
)

class SparkboxViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app as SparkboxApplication
    private val repo: SparkboxRepository = appCtx.repository
    private val todoStore: NativeTodoStore = appCtx.todoStore
    private val appPrefs = AppPrefs(app)
    private val locationHelper = LocationHelper(app)
    private val placeResolver = PlaceResolver(app)
    private val weatherService = WeatherService()

    private val _state = MutableStateFlow(loadPrefsState())
    val state: StateFlow<SparkboxUiState> = _state.asStateFlow()

    private var autosaveJob: Job? = null

    fun resolveAsset(rel: String): File? {
        val f = repo.absoluteAsset(rel)
        return f.takeIf { it.isFile }
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DemoSeed.ensure(repo, todoStore, appPrefs)
            }
            refreshCards()
            refreshNativeTodos()
        }
    }

    private fun loadPrefsState(): SparkboxUiState =
        SparkboxUiState(
            editorFontSp = appPrefs.editorFontSp,
            themeMode = appPrefs.themeMode,
            themePalette = appPrefs.themePalette,
            storageTarget = appPrefs.storageTarget,
            cloudProvider = appPrefs.cloudProvider,
            webdavUrl = appPrefs.webdavUrl,
            webdavUser = appPrefs.webdavUser,
            webdavPass = appPrefs.webdavPass,
            webdavRoot = appPrefs.webdavRoot,
            cloudEndpoint = appPrefs.cloudEndpoint,
            cloudAppKey = appPrefs.cloudAppKey,
            cloudToken = appPrefs.cloudToken,
            aiEnabled = appPrefs.aiEnabled,
        )

    private fun ai(): AiHooks = NoOpAiHooks(_state.value.aiEnabled)

    fun refreshCards() {
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) { repo.listAllNotes() }
            _state.update {
                it.copy(
                    notes = notes,
                    allTags = notes.flatMap { n -> n.tags }.distinct().sorted(),
                    filteredNotes = applyFilters(notes, it.filterQuery, it.filterDate, it.filterTag),
                )
            }
        }
    }

    fun refreshNotes() = refreshCards()

    fun refreshNativeTodos() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { todoStore.list() }
            _state.update { it.copy(nativeTodos = list) }
        }
    }

    fun setFilterQuery(q: String) {
        _state.update {
            it.copy(
                filterQuery = q,
                filteredNotes = applyFilters(it.notes, q, it.filterDate, it.filterTag),
            )
        }
    }

    fun setFilterDate(date: String) {
        _state.update {
            it.copy(
                filterDate = date,
                filteredNotes = applyFilters(it.notes, it.filterQuery, date, it.filterTag),
            )
        }
    }

    fun setFilterTag(tag: String) {
        _state.update {
            it.copy(
                filterTag = tag,
                filteredNotes = applyFilters(it.notes, it.filterQuery, it.filterDate, tag),
            )
        }
    }

    private fun applyFilters(
        notes: List<SparkEntry>,
        query: String,
        date: String,
        tag: String,
    ): List<SparkEntry> {
        val q = query.trim()
        return notes.filter { n ->
            (date.isBlank() || n.entryDate == date) &&
                (tag.isBlank() || tag in n.tags) &&
                (
                    q.isBlank() ||
                        n.title.contains(q, ignoreCase = true) ||
                        n.body.contains(q, ignoreCase = true) ||
                        n.tags.any { it.contains(q, ignoreCase = true) }
                    )
        }
    }

    fun openNote(entryId: String) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val entry = repo.getById(entryId) ?: return@withContext null
                val context = repo.getDayContext(entry.entryDate)
                entry to context
            } ?: return@launch
            val (entry, context) = loaded
            _state.update {
                it.copy(
                    entry = entry,
                    selectedDate = entry.entryDate,
                    dayContext = context,
                    status = "",
                )
            }
            if (entry.entryDate == SparkDates.today() && !context.hasContext) {
                captureContextOnce(entry.entryDate)
            }
        }
    }

    fun createNote(entryDate: String): String {
        val entry = repo.createNote(entryDate)
        openNote(entry.id)
        refreshCards()
        return entry.id
    }

    fun onBodyChange(text: String) {
        val current = _state.value.entry ?: return
        _state.update { it.copy(entry = current.copy(body = text)) }
        scheduleAutosave()
    }

    fun onTitleChange(title: String) {
        val current = _state.value.entry ?: return
        _state.update { it.copy(entry = current.copy(title = title.trim())) }
        scheduleAutosave()
    }

    fun onTagsChange(tagsCsv: String) {
        val current = _state.value.entry ?: return
        val tags = tagsCsv.split(',', '，', ' ')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct()
        _state.update { it.copy(entry = current.copy(tags = tags)) }
        scheduleAutosave()
    }

    fun toggleTag(tag: String) {
        val current = _state.value.entry ?: return
        val t = tag.trim().removePrefix("#")
        if (t.isEmpty()) return
        val next = if (t in current.tags) current.tags - t else (current.tags + t).distinct()
        _state.update { it.copy(entry = current.copy(tags = next)) }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            saveNow()
        }
    }

    fun saveNow() {
        viewModelScope.launch {
            val current = _state.value.entry ?: return@launch
            val saved = withContext(Dispatchers.IO) { repo.save(current) }
            _state.update { it.copy(entry = saved, status = "") }
            refreshCards()
        }
    }

    fun setEditorFontSp(sp: Float) {
        appPrefs.editorFontSp = sp
        _state.update { it.copy(editorFontSp = appPrefs.editorFontSp) }
    }

    fun setThemeMode(mode: String) {
        appPrefs.themeMode = mode
        _state.update { it.copy(themeMode = appPrefs.themeMode) }
    }

    fun setThemePalette(palette: String) {
        appPrefs.themePalette = palette
        _state.update { it.copy(themePalette = appPrefs.themePalette) }
    }

    fun setAiEnabled(enabled: Boolean) {
        appPrefs.aiEnabled = enabled
        _state.update { it.copy(aiEnabled = enabled, aiPreview = "") }
    }

    fun runAiDigestPreview() {
        val today = SparkDates.today()
        val cards = _state.value.notes.filter { it.entryDate == today }
        _state.update { it.copy(aiPreview = ai().dailyDigest(today, cards)) }
    }

    fun setStorageTarget(target: String) {
        appPrefs.storageTarget = target
        _state.update { it.copy(storageTarget = appPrefs.storageTarget) }
    }

    fun setCloudProvider(provider: String) {
        appPrefs.cloudProvider = provider
        _state.update { it.copy(cloudProvider = appPrefs.cloudProvider) }
    }

    fun saveWebDavSettings(url: String, user: String, pass: String, root: String) {
        appPrefs.webdavUrl = url
        appPrefs.webdavUser = user
        appPrefs.webdavPass = pass
        appPrefs.webdavRoot = root
        _state.update {
            it.copy(
                webdavUrl = appPrefs.webdavUrl,
                webdavUser = appPrefs.webdavUser,
                webdavPass = appPrefs.webdavPass,
                webdavRoot = appPrefs.webdavRoot,
                status = "WebDAV 设置已保存",
            )
        }
    }

    fun saveCloudStubSettings(endpoint: String, appKey: String, token: String) {
        appPrefs.cloudEndpoint = endpoint
        appPrefs.cloudAppKey = appKey
        appPrefs.cloudToken = token
        _state.update {
            it.copy(
                cloudEndpoint = appPrefs.cloudEndpoint,
                cloudAppKey = appPrefs.cloudAppKey,
                cloudToken = appPrefs.cloudToken,
                status = "云盘配置已保存（登录尚未开通）",
            )
        }
    }

    fun syncNow(entryDate: String? = null) {
        viewModelScope.launch {
            saveNow()
            val date = entryDate ?: _state.value.selectedDate
            val openId = _state.value.entry?.id
            val target = appPrefs.storageTarget
            _state.update { it.copy(syncing = true, status = "正在同步…") }
            val message = withContext(Dispatchers.IO) {
                when (target) {
                    "local" -> "当前为仅本地，无需上行同步"
                    "cloud" -> when (appPrefs.cloudProvider) {
                        "webdav" -> {
                            val cfg = WebDavConfig(
                                baseUrl = appPrefs.webdavUrl,
                                username = appPrefs.webdavUser,
                                password = appPrefs.webdavPass,
                                rootPath = appPrefs.webdavRoot,
                            )
                            if (!cfg.enabled) "请先填写 WebDAV 地址与账号"
                            else VaultMirror(appCtx.repository.dataRoot, WebDavClient(cfg)).sync().message
                        }
                        else -> "${appPrefs.cloudProvider} 尚未接入"
                    }
                    else -> "未知存放目标"
                }
            }
            _state.update { it.copy(syncing = false, status = message) }
            refreshCards()
            refreshNativeTodos()
            openId?.let { openNote(it) }
        }
    }

    fun upsertNativeTodo(todo: NativeTodo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { todoStore.upsert(todo) }
            refreshNativeTodos()
        }
    }

    fun getNativeTodo(id: String): NativeTodo? =
        _state.value.nativeTodos.firstOrNull { it.id == id }
            ?: todoStore.list().firstOrNull { it.id == id }

    fun addImage(uri: Uri, appendToBody: Boolean = true, onInserted: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val entry = _state.value.entry ?: return@launch
                repo.save(entry)
                val rel = repo.saveImage(getApplication(), entry.id, entry.entryDate, uri)
                val saved = if (appendToBody) {
                    repo.save(entry.copy(body = entry.body.trimEnd() + "\n\n![image]($rel)\n"))
                } else {
                    repo.getById(entry.id) ?: entry
                }
                onInserted(rel)
                _state.update { it.copy(entry = saved, status = "") }
                refreshCards()
            } catch (e: Exception) {
                _state.update { it.copy(status = "插图失败：${e.message}") }
            }
        }
    }

    fun createNativeTodo(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val todo = withContext(Dispatchers.IO) { todoStore.add("") }
            refreshNativeTodos()
            onCreated(todo.id)
        }
    }

    fun setNativeTodoDone(id: String, done: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { todoStore.setDone(id, done) }
            refreshNativeTodos()
        }
    }

    fun deleteNativeTodo(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { todoStore.delete(id) }
            refreshNativeTodos()
        }
    }

    private fun captureContextOnce(entryDate: String) {
        viewModelScope.launch {
            val existing = repo.getDayContext(entryDate)
            // Demo / empty seeds must not block a real phone fix.
            val lockedByPhone = existing.contextSource == "phone" && existing.hasContext
            if (lockedByPhone) return@launch
            if (!locationHelper.hasPermission()) {
                _state.update { it.copy(needLocationPermission = true) }
                return@launch
            }
            _state.update { it.copy(weatherLoading = true, needLocationPermission = false) }
            val loc = locationHelper.currentLocation()
            if (loc == null || entryDate != SparkDates.today()) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val label = placeResolver.labelFor(loc.latitude, loc.longitude)
            val snap = weatherService.fetch(loc.latitude, loc.longitude, label)
            if (snap == null) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val ctx = repo.saveContext(entryDate, snap, force = existing.contextSource != "phone")
            _state.update { it.copy(dayContext = ctx, weatherLoading = false) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            val date = _state.value.selectedDate
            if (date == SparkDates.today() && !_state.value.dayContext.hasContext) {
                captureContextOnce(date)
            } else {
                _state.update { it.copy(needLocationPermission = false) }
            }
        } else {
            _state.update { it.copy(needLocationPermission = false) }
        }
    }
}
