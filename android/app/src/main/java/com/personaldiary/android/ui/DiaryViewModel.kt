package com.personaldiary.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personaldiary.android.DiaryApplication
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.DiaryRepository
import com.personaldiary.android.weather.LocationHelper
import com.personaldiary.android.weather.PlaceResolver
import com.personaldiary.android.weather.WeatherService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiaryUiState(
    val entry: DiaryEntry = DiaryEntry(entryDate = DiaryEntry.today()),
    val timeline: List<DiaryEntry> = emptyList(),
    val saving: Boolean = false,
    val weatherLoading: Boolean = false,
    val status: String = "",
    val needLocationPermission: Boolean = false,
)

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: DiaryRepository = (app as DiaryApplication).repository
    private val locationHelper = LocationHelper(app)
    private val placeResolver = PlaceResolver(app)
    private val weatherService = WeatherService()

    private val _state = MutableStateFlow(DiaryUiState())
    val state: StateFlow<DiaryUiState> = _state.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        openDate(DiaryEntry.today(), fetchWeather = true)
    }

    fun openDate(entryDate: String, fetchWeather: Boolean = false) {
        val entry = repo.getOrCreate(entryDate)
        _state.update {
            it.copy(
                entry = entry,
                timeline = repo.listEntries(),
                status = "已打开 $entryDate",
                needLocationPermission = false,
            )
        }
        if (fetchWeather && entryDate == DiaryEntry.today()) {
            refreshWeather(auto = true)
        }
    }

    fun onBodyChange(text: String) {
        _state.update { it.copy(entry = it.entry.copy(body = text)) }
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            saveNow()
        }
    }

    fun saveNow() {
        val current = _state.value.entry
        val saved = repo.save(current)
        _state.update {
            it.copy(
                entry = saved,
                timeline = repo.listEntries(),
                saving = false,
                status = "已保存",
            )
        }
    }

    fun refreshTimeline() {
        _state.update { it.copy(timeline = repo.listEntries()) }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val date = _state.value.entry.entryDate
                // Persist current text first.
                repo.save(_state.value.entry)
                val (saved, _) = repo.saveImage(getApplication(), date, uri)
                _state.update {
                    it.copy(
                        entry = saved,
                        timeline = repo.listEntries(),
                        status = "已插入图片",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(status = "插图失败：${e.message}") }
            }
        }
    }

    fun refreshWeather(auto: Boolean = false) {
        viewModelScope.launch {
            if (!locationHelper.hasPermission()) {
                _state.update {
                    it.copy(
                        needLocationPermission = true,
                        status = if (auto) it.status else "需要定位权限以获取天气",
                    )
                }
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
            val label = placeResolver.labelFor(loc.latitude, loc.longitude)
            val snap = weatherService.fetch(loc.latitude, loc.longitude, label)
            if (snap == null) {
                _state.update { it.copy(weatherLoading = false, status = "天气获取失败") }
                return@launch
            }
            val saved = repo.saveContext(_state.value.entry.entryDate, snap)
            // Keep in-progress body.
            val merged = saved.copy(body = _state.value.entry.body)
            repo.save(merged)
            _state.update {
                it.copy(
                    entry = merged.copy(imageRels = repo.getOrCreate(merged.entryDate).imageRels),
                    weatherLoading = false,
                    status = "天气已更新 · ${merged.contextLine()}",
                    timeline = repo.listEntries(),
                )
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            refreshWeather(auto = false)
        } else {
            _state.update { it.copy(needLocationPermission = false, status = "未授予定位权限") }
        }
    }
}
