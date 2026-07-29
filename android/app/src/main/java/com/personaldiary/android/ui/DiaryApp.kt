package com.personaldiary.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.MarkdownImages
import com.personaldiary.android.data.TimelineDay
import com.personaldiary.android.ui.theme.AppFontFamily
import com.personaldiary.android.ui.theme.InkAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private object Routes {
    const val Home = "home"
    const val Charts = "charts"
    const val Settings = "settings"
    const val Day = "day/{date}"
    const val Note = "note/{id}"
    fun day(date: String) = "day/$date"
    fun note(id: String) = "note/$id"
}

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBottomBar = route in setOf(Routes.Home, Routes.Charts, Routes.Settings)

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.Home,
                        onClick = {
                            navController.navigate(Routes.Home) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                        label = { Text("日记") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.Charts,
                        onClick = {
                            navController.navigate(Routes.Charts) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        label = { Text("图表") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.Settings,
                        onClick = {
                            navController.navigate(Routes.Settings) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Home) {
                HomeScreen(
                    days = state.timeline,
                    syncing = state.syncing,
                    status = state.status,
                    onOpenDay = { date ->
                        viewModel.openDay(date)
                        navController.navigate(Routes.day(date))
                    },
                    onNewToday = {
                        val id = viewModel.createNote(DiaryDates.today())
                        navController.navigate(Routes.note(id))
                    },
                    onSync = {
                        viewModel.syncNow(DiaryDates.today())
                    },
                )
            }
            composable(Routes.Charts) { ChartsPlaceholderScreen() }
            composable(Routes.Settings) {
                SettingsScreen(
                    endpoint = state.syncEndpoint,
                    token = state.syncToken,
                    fontSp = state.editorFontSp,
                    onSaveSync = viewModel::saveSyncSettings,
                    onFontChange = viewModel::setEditorFontSp,
                    onSyncNow = { viewModel.syncNow(DiaryDates.today()) },
                    syncing = state.syncing,
                    status = state.status,
                )
            }
            composable(
                route = Routes.Day,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: DiaryDates.today()
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
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) { viewModel.openNote(id) }
                val entry = state.entry
                if (entry != null) {
                    NoteEditorScreen(
                        entry = entry,
                        context = state.dayContext,
                        status = state.status,
                        syncing = state.syncing,
                        fontSp = state.editorFontSp,
                        onBack = { navController.popBackStack() },
                        onBodyChange = viewModel::onBodyChange,
                        onPickImage = { uri, insert -> viewModel.addImage(uri, insert) },
                        onSync = { viewModel.syncNow(entry.entryDate) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    days: List<TimelineDay>,
    syncing: Boolean,
    status: String,
    onOpenDay: (String) -> Unit,
    onNewToday: () -> Unit,
    onSync: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("日记", fontWeight = FontWeight.Bold)
                        Text(
                            "本地优先 · 可同步",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
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
        floatingActionButton = {
            FloatingActionButton(onClick = onNewToday) {
                Icon(Icons.Filled.Add, contentDescription = "新建今天的笔记")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (days.isEmpty()) {
                Column(Modifier.padding(top = 28.dp)) {
                    Text("还没有日记", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点右下角 +，写今天的第一条。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            .padding(vertical = 14.dp),
    ) {
        Text(
            day.date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${day.notes.size} 篇 · ${day.notes.firstOrNull()?.title ?: "空白日"}",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        day.notes.firstOrNull()?.let { first ->
            Text(
                notePreview(first.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun ChartsPlaceholderScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.BarChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("图表", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "写作统计、心情与天气曲线会放在这里。\n当前先留位，不影响日记与同步。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    endpoint: String,
    token: String,
    fontSp: Float,
    onSaveSync: (String, String) -> Unit,
    onFontChange: (Float) -> Unit,
    onSyncNow: () -> Unit,
    syncing: Boolean,
    status: String,
) {
    var ep by remember(endpoint) { mutableStateOf(endpoint) }
    var tok by remember(token) { mutableStateOf(token) }
    var font by remember(fontSp) { mutableStateOf(fontSp) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("书写", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("正文字号 ${font.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = font,
            onValueChange = {
                font = it
                onFontChange(it)
            },
            valueRange = 14f..24f,
            steps = 9,
        )
        Text(
            "拖动滑块即时生效。示例：今天也值得认真写一句。",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = AppFontFamily,
                fontSize = font.sp,
                lineHeight = (font * 1.55f).sp,
            ),
        )

        Text("同步", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = ep,
            onValueChange = { ep = it },
            label = { Text("服务地址") },
            placeholder = { Text("https://diary.xybkwd.top") },
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSaveSync(ep, tok) }) { Text("保存同步设置") }
            TextButton(onClick = onSyncNow, enabled = !syncing) {
                Text(if (syncing) "同步中…" else "立即同步")
            }
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("关于", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "个人日记 · 本地优先 · 协议 v2\n地点与天气仅在新建当日首次采集。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .padding(horizontal = 20.dp),
        ) {
            ContextMiniCard(context = context, weatherLoading = weatherLoading)
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(
                    "这一天还没有笔记，点 + 新建。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(notes, key = { it.id }) { note ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNote(note.id) }
                                .padding(vertical = 12.dp),
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
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
    fontSp: Float,
    onBack: () -> Unit,
    onBodyChange: (String) -> Unit,
    onPickImage: (android.net.Uri, (String) -> Unit) -> Unit,
    onSync: () -> Unit,
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
        ActivityResultContracts.GetContent(),
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
                    IconButton(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                formatHeading(entry.entryDate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            ContextMiniCard(context = context, weatherLoading = false)
            Spacer(Modifier.height(8.dp))
            MarkdownToolbar(state = richTextState)
            Spacer(Modifier.height(6.dp))
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                RichTextEditor(
                    state = richTextState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.55f).sp,
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
private fun MarkdownToolbar(state: RichTextState) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { state.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) }) {
            Icon(Icons.Outlined.FormatBold, contentDescription = "加粗")
        }
        IconButton(onClick = {
            state.toggleSpanStyle(
                androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            )
        }) {
            Icon(Icons.Outlined.FormatItalic, contentDescription = "斜体")
        }
        IconButton(onClick = {
            val md = state.toMarkdown().trimEnd() + "\n\n## "
            state.setMarkdown(md)
        }) {
            Icon(Icons.Outlined.Title, contentDescription = "标题")
        }
        IconButton(onClick = {
            // Unordered list via markdown insert fallback
            val md = state.toMarkdown().trimEnd() + "\n- "
            state.setMarkdown(md)
        }) {
            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "列表")
        }
    }
}

@Composable
private fun ContextMiniCard(context: DayContext, weatherLoading: Boolean) {
    if (!context.hasContext && !weatherLoading) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (weatherLoading && !context.hasContext) {
                Text(
                    "正在记录地点与天气…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                if (context.location.isNotBlank()) {
                    Text(
                        context.location,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                    )
                }
                val wx = buildString {
                    if (context.weather.isNotBlank()) append(context.weather)
                    context.tempC?.let {
                        if (isNotEmpty()) append(" · ")
                        append(DiaryDates.formatTemp(it))
                    }
                }
                if (wx.isNotBlank()) {
                    Text(
                        wx,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }
        if (weatherLoading) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
    }
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
        .filter { it.isNotEmpty() && !it.startsWith("![") && it != "［图片］" }
        .firstOrNull()
        ?.removePrefix("#")
        ?.trim()
        ?.take(120)
        ?: "（空）"
}
