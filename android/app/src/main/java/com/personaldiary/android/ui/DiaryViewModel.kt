package com.personaldiary.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaldiary.android.DiaryApplication
import com.personaldiary.android.data.AppPrefs
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.DiaryRepository
import com.personaldiary.android.data.TimelineDay
import com.personaldiary.android.sync.SyncClient
import com.personaldiary.android.sync.SyncPrefs
import com.personaldiary.android.weather.LocationHelper
import com.personaldiary.android.weather.PlaceResolver
import com.personaldiary.android.weather.WeatherService
import kotlinx.coroutines.Dispatchers
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
    val dayNotes: List<DiaryEntry> = emptyList(),
    val timeline: List<TimelineDay> = emptyList(),
    val selectedDate: String = DiaryDates.today(),
    val saving: Boolean = false,
    val weatherLoading: Boolean = false,
    val syncing: Boolean = false,
    val status: String = "",
    val needLocationPermission: Boolean = false,
    val syncEndpoint: String = "",
    val syncToken: String = "",
    val editorFontSp: Float = 17f,
)

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: DiaryRepository = (app as DiaryApplication).repository
    private val locationHelper = LocationHelper(app)
    private val placeResolver = PlaceResolver(app)
    private val weatherService = WeatherService()
    private val syncPrefs = SyncPrefs(app)
    private val appPrefs = AppPrefs(app)
    private val syncClient = SyncClient(repo, syncPrefs)

    private val _state = MutableStateFlow(
        DiaryUiState(
            syncEndpoint = syncPrefs.endpoint,
            syncToken = syncPrefs.token,
            editorFontSp = appPrefs.editorFontSp,
        )
    )
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        refreshTimeline()
    }

    fun refreshTimeline() {
        _state.update { it.copy(timeline = repo.listTimeline()) }
    }

    fun openDay(entryDate: String) {
        val context = repo.getDayContext(entryDate)
        val notes = repo.listForDate(entryDate)
        _state.update {
            it.copy(
                selectedDate = entryDate,
                dayContext = context,
                dayNotes = notes,
                status = "已打开 $entryDate",
                needLocationPermission = false,
            )
        }
        if (entryDate == DiaryDates.today() && !context.hasContext) {
            captureContextOnce(entryDate)
        }
    }

    fun openNote(entryId: String) {
        val entry = repo.getById(entryId) ?: return
        val context = repo.getDayContext(entry.entryDate)
        _state.update {
            it.copy(
                entry = entry,
                selectedDate = entry.entryDate,
                dayContext = context,
                dayNotes = repo.listForDate(entry.entryDate),
                status = "编辑中",
            )
        }
    }

    fun createNote(entryDate: String): String {
        val entry = repo.createNote(entryDate)
        openDay(entryDate)
        _state.update { it.copy(entry = entry, status = "新笔记") }
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

    fun saveNow() {
        val current = _state.value.entry ?: return
        val saved = repo.save(current)
        val context = repo.getDayContext(current.entryDate)
        _state.update {
            it.copy(
                entry = saved,
                dayNotes = repo.listForDate(current.entryDate),
                dayContext = context,
                timeline = repo.listTimeline(),
                saving = false,
                status = "已保存",
            )
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

    fun syncNow(entryDate: String? = null) {
        viewModelScope.launch {
            saveNow()
            val date = entryDate ?: _state.value.selectedDate
            _state.update { it.copy(syncing = true, status = "正在同步…") }
            val result = withContext(Dispatchers.IO) { syncClient.sync(date) }
            openDay(date)
            _state.update {
                it.copy(
                    syncing = false,
                    status = result.message,
                    timeline = repo.listTimeline(),
                    dayNotes = repo.listForDate(date),
                    dayContext = repo.getDayContext(date),
                )
            }
            _state.value.entry?.let { openNote(it.id) }
        }
    }

    fun addImage(uri: Uri, onInserted: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val entry = _state.value.entry ?: return@launch
                repo.save(entry)
                val rel = repo.saveImage(getApplication(), entry.id, entry.entryDate, uri)
                onInserted(rel)
                val saved = repo.getById(entry.id) ?: entry
                _state.update {
                    it.copy(
                        entry = saved,
                        dayNotes = repo.listForDate(entry.entryDate),
                        timeline = repo.listTimeline(),
                        status = "图片已保存（暂不预览）",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(status = "插图失败：${e.message}") }
            }
        }
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
            if (loc == null) {
                _state.update {
                    it.copy(weatherLoading = false, status = "无法获取定位，请检查系统定位开关")
                }
                return@launch
            }
            if (entryDate != DiaryDates.today()) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val label = placeResolver.labelFor(loc.latitude, loc.longitude)
            val snap = weatherService.fetch(loc.latitude, loc.longitude, label)
            if (snap == null) {
                _state.update { it.copy(weatherLoading = false, status = "天气获取失败") }
                return@launch
            }
            if (repo.getDayContext(entryDate).hasContext) {
                _state.update { it.copy(weatherLoading = false) }
                return@launch
            }
            val ctx = repo.saveContext(entryDate, snap)
            _state.update {
                it.copy(
                    dayContext = ctx,
                    weatherLoading = false,
                    status = "已记录 · ${ctx.contextLine()}",
                    timeline = repo.listTimeline(),
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            val date = _state.value.selectedDate
            if (date == DiaryDates.today() && !_state.value.dayContext.hasContext) {
                captureContextOnce(date)
            }
        } else {
            _state.update { it.copy(needLocationPermission = false, status = "未授予定位权限") }
        }
    }
}
