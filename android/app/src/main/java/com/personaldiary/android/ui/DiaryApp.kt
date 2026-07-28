package com.personaldiary.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.MarkdownImages
import com.personaldiary.android.data.TimelineDay
import com.personaldiary.android.ui.theme.InkAccent
import com.personaldiary.android.ui.theme.AppFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private object Routes {
    const val Timeline = "timeline"
    const val Day = "day/{date}"
    const val Note = "note/{id}"
    fun day(date: String) = "day/$date"
    fun note(id: String) = "note/$id"
}

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var showSyncSettings by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(state.needLocationPermission) {
        if (state.needLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    if (showSyncSettings) {
        SyncSettingsDialog(
            endpoint = state.syncEndpoint,
            token = state.syncToken,
            onDismiss = { showSyncSettings = false },
            onSave = { ep, tok ->
                viewModel.saveSyncSettings(ep, tok)
                showSyncSettings = false
            },
        )
    }

    NavHost(navController = navController, startDestination = Routes.Timeline) {
        composable(Routes.Timeline) {
            TimelineScreen(
                days = state.timeline,
                syncing = state.syncing,
                onOpenDay = { date ->
                    viewModel.openDay(date)
                    navController.navigate(Routes.day(date))
                },
                onSync = {
                    if (state.syncEndpoint.isBlank() || state.syncToken.isBlank()) {
                        showSyncSettings = true
                    } else {
                        viewModel.syncNow(DiaryDates.today())
                    }
                },
                onSyncSettings = { showSyncSettings = true },
            )
        }
        composable(
            route = Routes.Day,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { backStack ->
            val date = backStack.arguments?.getString("date") ?: DiaryDates.today()
            LaunchedEffect(date) { viewModel.openDay(date) }
            DayListScreen(
                date = date,
                context = state.dayContext,
                notes = state.dayNotes,
                weatherLoading = state.weatherLoading,
                onBack = { navController.popBackStack() },
                onOpenNote = { id ->
                    viewModel.openNote(id)
                    navController.navigate(Routes.note(id))
                },
                onNewNote = {
                    val id = viewModel.createNote(date)
                    navController.navigate(Routes.note(id))
                },
            )
        }
        composable(
            route = Routes.Note,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.openNote(id) }
            val entry = state.entry
            if (entry != null) {
                NoteEditorScreen(
                    entry = entry,
                    context = state.dayContext,
                    status = state.status,
                    syncing = state.syncing,
                    onBack = { navController.popBackStack() },
                    onBodyChange = viewModel::onBodyChange,
                    onPickImage = { uri, insert ->
                        viewModel.addImage(uri, insert)
                    },
                    onSync = {
                        if (state.syncEndpoint.isBlank() || state.syncToken.isBlank()) {
                            showSyncSettings = true
                        } else {
                            viewModel.syncNow(entry.entryDate)
                        }
                    },
                    onSyncSettings = { showSyncSettings = true },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineScreen(
    days: List<TimelineDay>,
    syncing: Boolean,
    onOpenDay: (String) -> Unit,
    onSync: () -> Unit,
    onSyncSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("时间线") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onSyncSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "同步设置")
                    }
                    IconButton(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CloudSync, contentDescription = "同步")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp),
        ) {
            if (days.isEmpty()) {
                Text(
                    "还没有日记",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(days, key = { it.date }) { day ->
                        TimelineDayRow(day = day, onClick = { onOpenDay(day.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDayRow(day: TimelineDay, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Text(
            day.date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (day.context.contextLine().isNotBlank()) {
            Text(
                day.context.contextLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${day.notes.size} 篇笔记",
            style = MaterialTheme.typography.bodyMedium,
        )
        day.notes.firstOrNull()?.let { first ->
            Text(
                first.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayListScreen(
    date: String,
    context: DayContext,
    notes: List<DiaryEntry>,
    weatherLoading: Boolean,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(formatHeading(date)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewNote) {
                Icon(Icons.Filled.Add, contentDescription = "新建笔记")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp),
        ) {
            DayContextRow(context = context, weatherLoading = weatherLoading)
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(
                    "今天还没有笔记，点 + 开始写",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNote(note.id) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                notePreview(note.body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteEditorScreen(
    entry: DiaryEntry,
    context: DayContext,
    status: String,
    syncing: Boolean,
    onBack: () -> Unit,
    onBodyChange: (String) -> Unit,
    onPickImage: (android.net.Uri, (String) -> Unit) -> Unit,
    onSync: () -> Unit,
    onSyncSettings: () -> Unit,
) {
    val richTextState = rememberRichTextState()
    var loadedEntryId by remember { mutableStateOf<String?>(null) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(entry.id) {
        richTextState.setMarkdown(MarkdownImages.stripForDisplay(entry.body))
        loadedEntryId = entry.id
    }

    LaunchedEffect(richTextState, loadedEntryId, entry.id, entry.body) {
        snapshotFlow { richTextState.toMarkdown() }
            .distinctUntilChanged()
            .collect { md ->
                if (loadedEntryId == entry.id) {
                    val merged = MarkdownImages.mergeEditorText(md, entry.body)
                    if (merged != entry.body) {
                        delay(500)
                        onBodyChange(merged)
                    }
                }
            }
    }

    LaunchedEffect(richTextState.selection, loadedEntryId, entry.id) {
        if (loadedEntryId == entry.id) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onPickImage(uri) { rel ->
                val text = MarkdownImages.mergeEditorText(richTextState.toMarkdown(), entry.body)
                val withImg = text.trimEnd() + "\n\n![image]($rel)\n"
                onBodyChange(withImg)
                richTextState.setMarkdown(MarkdownImages.stripForDisplay(withImg))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(entry.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(Icons.Outlined.Image, contentDescription = "插入图片")
                    }
                    IconButton(onClick = onSyncSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "同步设置")
                    }
                    IconButton(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CloudSync, contentDescription = "同步")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                formatHeading(entry.entryDate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            DayContextRow(context = context, weatherLoading = false)
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                RichTextEditor(
                    state = richTextState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    colors = com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults.richTextEditorColors(
                        cursorColor = InkAccent,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DayContextRow(context: DayContext, weatherLoading: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val line = context.contextLine().ifBlank {
            if (weatherLoading) "正在记录地点与天气…" else ""
        }
        if (line.isNotBlank()) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (weatherLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun SyncSettingsDialog(
    endpoint: String,
    token: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var ep by remember { mutableStateOf(endpoint) }
    var tok by remember { mutableStateOf(token) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ep,
                    onValueChange = { ep = it },
                    label = { Text("服务地址") },
                    placeholder = { Text("https://your-vps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tok,
                    onValueChange = { tok = it },
                    label = { Text("Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ep, tok) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatHeading(entryDate: String): String {
    val d = LocalDate.parse(entryDate)
    val week = d.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
    return "${d.year}年${d.monthValue.toString().padStart(2, '0')}月" +
        "${d.dayOfMonth.toString().padStart(2, '0')}日  $week"
}

private fun notePreview(body: String): String {
    return body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("![") }
        .firstOrNull()
        ?.removePrefix("#")
        ?.trim()
        ?.take(120)
        ?: "（空）"
}
