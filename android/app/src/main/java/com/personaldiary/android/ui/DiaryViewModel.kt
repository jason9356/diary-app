package com.personaldiary.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaldiary.android.DiaryApplication
import com.personaldiary.android.ai.AiHooks
import com.personaldiary.android.ai.NoOpAiHooks
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DemoSeed
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.DiaryRepository
import com.personaldiary.android.data.NativeTodo
import com.personaldiary.android.data.NativeTodoStore
import com.personaldiary.android.data.ObsidianTodo
import com.personaldiary.android.data.AppPrefs
import com.personaldiary.android.obsidian.ObsidianTodoExtract
import com.personaldiary.android.obsidian.S3Config
import com.personaldiary.android.obsidian.S3ObjectStore
import com.personaldiary.android.obsidian.TagRule
import com.personaldiary.android.sync.SyncClient
import com.personaldiary.android.sync.SyncPrefs
import com.personaldiary.android.sync.VaultMirror
import com.personaldiary.android.sync.WebDavClient
import com.personaldiary.android.sync.WebDavConfig
import com.personaldiary.android.weather.LocationHelper
import com.personaldiary.android.weather.PlaceResolver
import com.personaldiary.android.weather.WeatherService
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

data class DiaryUiState(
    val entry: DiaryEntry? = null,
    val dayContext: DayContext = DayContext(date = DiaryDates.today()),
    val notes: List<DiaryEntry> = emptyList(),
    val filteredNotes: List<DiaryEntry> = emptyList(),
    val allTags: List<String> = emptyList(),
    val filterQuery: String = "",
    val filterDate: String = "",
    val filterTag: String = "",
    val nativeTodos: List<NativeTodo> = emptyList(),
    val obsidianTodos: List<ObsidianTodo> = emptyList(),
    val selectedDate: String = DiaryDates.today(),
    val weatherLoading: Boolean = false,
    val syncing: Boolean = false,
    val todosLoading: Boolean = false,
    val status: String = "",
    val todoStatus: String = "",
    val needLocationPermission: Boolean = false,
    val syncEndpoint: String = "",
    val syncToken: String = "",
    val editorFontSp: Float = 17f,
    /** system | light | dark */
    val themeMode: String = "system",
    /** slip | moss | spark | paper */
    val themePalette: String = "slip",
    val obsidianTodosEnabled: Boolean = false,
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
    val s3Endpoint: String = "",
    val s3Region: String = "us-east-1",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3Prefix: String = "",
    val obsidianDiaryFolder: String = "日记",
    val tagOpen: String = "【",
    val tagClose: String = "】",
    val completedLabel: String = "已完成",
)

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app as DiaryApplication
    private val repo: DiaryRepository = appCtx.repository
    private val todoStore: NativeTodoStore = appCtx.todoStore
    private val syncPrefs = SyncPrefs(app)
    private val appPrefs = AppPrefs(app)
    private val syncClient = SyncClient(repo, syncPrefs)
    private val locationHelper = LocationHelper(app)
    private val placeResolver = PlaceResolver(app)
    private val weatherService = WeatherService()

    private val _state = MutableStateFlow(loadPrefsState())
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var autosaveJob: Job? = null

    fun resolveAsset(rel: String): File? {
        val f = repo.absoluteAsset(rel)
        return f.takeIf { it.isFile }
    }

    init {
        if (appPrefs.obsidianTodosEnabled) {
            appPrefs.obsidianTodosEnabled = false
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DemoSeed.ensure(repo, todoStore, appPrefs)
            }
            refreshCards()
            refreshNativeTodos()
        }
    }

    private fun loadPrefsState(): DiaryUiState =
        DiaryUiState(
            syncEndpoint = syncPrefs.endpoint,
            syncToken = syncPrefs.token,
            editorFontSp = appPrefs.editorFontSp,
            themeMode = appPrefs.themeMode,
            themePalette = appPrefs.themePalette,
            obsidianTodosEnabled = false,
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
            s3Endpoint = appPrefs.s3Endpoint,
            s3Region = appPrefs.s3Region,
            s3Bucket = appPrefs.s3Bucket,
            s3AccessKey = appPrefs.s3AccessKey,
            s3SecretKey = appPrefs.s3SecretKey,
            s3Prefix = appPrefs.s3Prefix,
            obsidianDiaryFolder = appPrefs.obsidianDiaryFolder,
            tagOpen = appPrefs.tagOpen,
            tagClose = appPrefs.tagClose,
            completedLabel = appPrefs.completedLabel,
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
        notes: List<DiaryEntry>,
        query: String,
        date: String,
        tag: String,
    ): List<DiaryEntry> {
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
            if (entry.entryDate == DiaryDates.today() && !context.hasContext) {
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
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            saveNow()
        }
    }

    fun onTagsChange(tagsCsv: String) {
        val current = _state.value.entry ?: return
        val tags = tagsCsv.split(',', '，', ' ')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct()
        _state.update { it.copy(entry = current.copy(tags = tags)) }
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

    fun saveSyncSettings(endpoint: String, token: String) {
        syncPrefs.endpoint = endpoint
        syncPrefs.token = token
        _state.update {
            it.copy(
                syncEndpoint = syncPrefs.endpoint,
                syncToken = syncPrefs.token,
                status = "同步设置已保存",
            )
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

    fun setObsidianTodosEnabled(enabled: Boolean) {
        appPrefs.obsidianTodosEnabled = enabled
        _state.update {
            it.copy(
                obsidianTodosEnabled = enabled,
                obsidianTodos = if (enabled) it.obsidianTodos else emptyList(),
                todoStatus = if (enabled) it.todoStatus else "",
            )
        }
        if (enabled) refreshObsidianTodos()
    }

    fun setAiEnabled(enabled: Boolean) {
        appPrefs.aiEnabled = enabled
        _state.update { it.copy(aiEnabled = enabled, aiPreview = "") }
    }

    fun runAiDigestPreview() {
        val today = DiaryDates.today()
        val cards = _state.value.notes.filter { it.entryDate == today }
        _state.update { it.copy(aiPreview = ai().dailyDigest(today, cards)) }
    }

    fun saveObsidianSettings(
        endpoint: String,
        region: String,
        bucket: String,
        accessKey: String,
        secretKey: String,
        prefix: String,
        diaryFolder: String,
        tagOpen: String,
        tagClose: String,
        completedLabel: String,
    ) {
        appPrefs.s3Endpoint = endpoint
        appPrefs.s3Region = region
        appPrefs.s3Bucket = bucket
        appPrefs.s3AccessKey = accessKey
        appPrefs.s3SecretKey = secretKey
        appPrefs.s3Prefix = prefix
        appPrefs.obsidianDiaryFolder = diaryFolder
        appPrefs.tagOpen = tagOpen
        appPrefs.tagClose = tagClose
        appPrefs.completedLabel = completedLabel
        _state.update {
            it.copy(
                s3Endpoint = appPrefs.s3Endpoint,
                s3Region = appPrefs.s3Region,
                s3Bucket = appPrefs.s3Bucket,
                s3AccessKey = appPrefs.s3AccessKey,
                s3SecretKey = appPrefs.s3SecretKey,
                s3Prefix = appPrefs.s3Prefix,
                obsidianDiaryFolder = appPrefs.obsidianDiaryFolder,
                tagOpen = appPrefs.tagOpen,
                tagClose = appPrefs.tagClose,
                completedLabel = appPrefs.completedLabel,
                status = "Obsidian / 对象存储设置已保存",
            )
        }
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
                    "sync_server" -> {
                        val card = syncClient.sync(date)
                        val todos = syncClient.syncTodos(todoStore)
                        listOf(card.message, todos.message).filter { it.isNotBlank() }.joinToString(" · ")
                    }
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

    fun addNativeTodo(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { todoStore.add(text) }
            refreshNativeTodos()
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

    private fun s3Config(): S3Config =
        S3Config(
            endpoint = appPrefs.s3Endpoint,
            region = appPrefs.s3Region,
            bucket = appPrefs.s3Bucket,
            accessKey = appPrefs.s3AccessKey,
            secretKey = appPrefs.s3SecretKey,
            prefix = appPrefs.s3Prefix,
        )

    private fun tagRule(): TagRule =
        TagRule(
            open = appPrefs.tagOpen,
            close = appPrefs.tagClose,
            completedLabel = appPrefs.completedLabel,
            boldCompleted = true,
        )

    fun refreshObsidianTodos() {
        viewModelScope.launch {
            if (!appPrefs.obsidianTodosEnabled) {
                _state.update {
                    it.copy(
                        obsidianTodos = emptyList(),
                        todosLoading = false,
                        todoStatus = "",
                    )
                }
                return@launch
            }
            val cfg = s3Config()
            if (!cfg.enabled) {
                _state.update {
                    it.copy(
                        todoStatus = "未配置对象存储 · 显示示例待办",
                        obsidianTodos = DemoSeed.sampleObsidianTodos(),
                        todosLoading = false,
                    )
                }
                return@launch
            }
            _state.update { it.copy(todosLoading = true, todoStatus = "") }
            try {
                val todos = withContext(Dispatchers.IO) {
                    val store = S3ObjectStore(cfg)
                    val folder = appPrefs.obsidianDiaryFolder
                    val keys = store.listMarkdownKeys(folder)
                    val rule = tagRule()
                    keys.flatMap { key ->
                        val md = store.getObject(key)
                        val path = key.removePrefix(cfg.prefix.trim('/')).trimStart('/')
                        ObsidianTodoExtract.extract(path.ifBlank { key }, md, rule)
                    }
                }
                _state.update {
                    it.copy(
                        obsidianTodos = todos.ifEmpty { DemoSeed.sampleObsidianTodos() },
                        todosLoading = false,
                        todoStatus = if (todos.isEmpty()) {
                            "COS 暂无未完成项 · 显示示例"
                        } else {
                            "已加载 ${todos.size} 条 Obsidian 待办"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        todosLoading = false,
                        todoStatus = "加载失败：${e.message}",
                        obsidianTodos = emptyList(),
                    )
                }
            }
        }
    }

    fun completeObsidianTodo(todo: ObsidianTodo) {
        viewModelScope.launch {
            if (!appPrefs.obsidianTodosEnabled) return@launch
            val cfg = s3Config()
            if (!cfg.enabled) {
                _state.update {
                    it.copy(
                        obsidianTodos = it.obsidianTodos.filterNot { t -> t.key == todo.key },
                        todoStatus = "已完成（示例）",
                    )
                }
                return@launch
            }
            _state.update { it.copy(todosLoading = true) }
            try {
                withContext(Dispatchers.IO) {
                    val store = S3ObjectStore(cfg)
                    val key = joinKey(cfg.prefix, todo.filePath)
                    val md = store.getObject(key)
                    val lines = md.split("\r\n", "\n").toMutableList()
                    if (todo.lineIndex !in lines.indices) error("行号失效，请重新刷新")
                    if (lines[todo.lineIndex] != todo.originalLine) {
                        error("原文已变更，请重新刷新后再完成")
                    }
                    val completed = ObsidianTodoExtract.toCompletedLine(todo.originalLine, tagRule())
                        ?: error("无法回写完成标记")
                    lines[todo.lineIndex] = completed
                    store.putObject(key, lines.joinToString("\n"))
                }
                _state.update { it.copy(todosLoading = false, todoStatus = "已写回 Obsidian") }
                refreshObsidianTodos()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        todosLoading = false,
                        obsidianTodos = it.obsidianTodos.filterNot { t -> t.key == todo.key },
                        todoStatus = "已完成（本地）",
                    )
                }
            }
        }
    }

    private fun joinKey(prefix: String, path: String): String {
        val p = prefix.trim().trim('/')
        val f = path.trim().trim('/')
        return if (p.isEmpty()) f else "$p/$f"
    }

    private fun captureContextOnce(entryDate: String) {
        viewModelScope.launch {
            if (repo.getDayContext(entryDate).hasContext) return@launch
            if (!locationHelper.hasPermission()) {
                _state.update { it.copy(needLocationPermission = true) }
                return@launch
            }
            _state.update { it.copy(weatherLoading = true, needLocationPermission = false) }
            val loc = locationHelper.currentLocation()
            if (loc == null || entryDate != DiaryDates.today()) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val label = placeResolver.labelFor(loc.latitude, loc.longitude)
            val snap = weatherService.fetch(loc.latitude, loc.longitude, label)
            if (snap == null || repo.getDayContext(entryDate).hasContext) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val ctx = repo.saveContext(entryDate, snap)
            _state.update { it.copy(dayContext = ctx, weatherLoading = false) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            val date = _state.value.selectedDate
            if (date == DiaryDates.today() && !_state.value.dayContext.hasContext) {
                captureContextOnce(date)
            } else {
                _state.update { it.copy(needLocationPermission = false) }
            }
        } else {
            _state.update { it.copy(needLocationPermission = false) }
        }
    }
}
