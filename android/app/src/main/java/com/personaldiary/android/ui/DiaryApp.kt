package com.personaldiary.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import coil.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.MarkdownImages
import com.personaldiary.android.data.NativeTodo
import com.personaldiary.android.data.ObsidianTodo
import com.personaldiary.android.ui.theme.AppFontFamily
import com.personaldiary.android.ui.theme.DisplayFontFamily
import com.personaldiary.android.ui.theme.SlipTeal
import com.personaldiary.android.ui.theme.SparkFieldBrush
import com.personaldiary.android.ui.theme.SlipField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private object Routes {
    const val Home = "home"
    const val Todos = "todos"
    const val Settings = "settings"
    const val Stats = "stats"
    const val Read = "read/{id}"
    const val Edit = "edit/{id}"
    fun read(id: String) = "read/$id"
    fun edit(id: String) = "edit/$id"
}

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBottomBar = route in setOf(Routes.Home, Routes.Todos, Routes.Settings)

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
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SlipField.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SlipTeal,
                        selectedTextColor = SlipTeal,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NavigationBarItem(
                        selected = route == Routes.Home,
                        onClick = {
                            navController.navigate(Routes.Home) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
                        label = { Text("灵感") },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = route == Routes.Todos,
                        onClick = {
                            navController.navigate(Routes.Todos) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            viewModel.refreshNativeTodos()
                        },
                        icon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                        label = { Text("待办") },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = route == Routes.Settings,
                        onClick = {
                            navController.navigate(Routes.Settings) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("设置") },
                        colors = navColors,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(SparkFieldBrush)) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
            composable(Routes.Home) {
                HomeScreen(
                    notes = state.filteredNotes,
                    filterQuery = state.filterQuery,
                    filterDate = state.filterDate,
                    filterTag = state.filterTag,
                    syncing = state.syncing,
                    resolveAsset = viewModel::resolveAsset,
                    onQuery = viewModel::setFilterQuery,
                    onDate = viewModel::setFilterDate,
                    onClearTag = { viewModel.setFilterTag("") },
                    onOpenStats = { navController.navigate(Routes.Stats) },
                    onOpenRead = { id ->
                        viewModel.openNote(id)
                        navController.navigate(Routes.read(id))
                    },
                    onNew = {
                        val id = viewModel.createNote(DiaryDates.today())
                        navController.navigate(Routes.edit(id))
                    },
                    onSync = { viewModel.syncNow(DiaryDates.today()) },
                )
            }
            composable(Routes.Stats) {
                StatsScreen(
                    notes = state.notes,
                    filterTag = state.filterTag,
                    onBack = { navController.popBackStack() },
                    onSelectTag = { tag ->
                        viewModel.setFilterTag(tag)
                        navController.popBackStack()
                    },
                    onClearTag = {
                        viewModel.setFilterTag("")
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.Todos) {
                TodosScreen(
                    nativeTodos = state.nativeTodos,
                    obsidianTodos = state.obsidianTodos,
                    loading = state.todosLoading,
                    status = state.todoStatus,
                    onAdd = viewModel::addNativeTodo,
                    onToggleNative = viewModel::setNativeTodoDone,
                    onDeleteNative = viewModel::deleteNativeTodo,
                    onRefreshObsidian = viewModel::refreshObsidianTodos,
                    onCompleteObsidian = viewModel::completeObsidianTodo,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    state = state,
                    onSaveSync = viewModel::saveSyncSettings,
                    onFontChange = viewModel::setEditorFontSp,
                    onSyncNow = { viewModel.syncNow(DiaryDates.today()) },
                    onSaveObsidian = viewModel::saveObsidianSettings,
                    onAiEnabled = viewModel::setAiEnabled,
                    onAiPreview = viewModel::runAiDigestPreview,
                )
            }
            composable(
                route = Routes.Read,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) { viewModel.openNote(id) }
                val entry = state.entry
                if (entry != null && entry.id == id) {
                    NoteReadScreen(
                        entry = entry,
                        context = state.dayContext,
                        weatherLoading = state.weatherLoading,
                        fontSp = state.editorFontSp,
                        resolveAsset = viewModel::resolveAsset,
                        onBack = {
                            viewModel.refreshCards()
                            navController.popBackStack()
                        },
                        onEdit = { navController.navigate(Routes.edit(entry.id)) },
                        onPickImage = { uri -> viewModel.addImage(uri) { } },
                    )
                }
            }
            composable(
                route = Routes.Edit,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) { viewModel.openNote(id) }
                val entry = state.entry
                if (entry != null && entry.id == id) {
                    NoteEditorScreen(
                        entry = entry,
                        syncing = state.syncing,
                        fontSp = state.editorFontSp,
                        onBack = {
                            viewModel.refreshCards()
                            navController.popBackStack()
                        },
                        onBodyChange = viewModel::onBodyChange,
                        onTagsChange = viewModel::onTagsChange,
                        onPickImage = { uri, insert ->
                            viewModel.addImage(uri, appendToBody = false, onInserted = insert)
                        },
                        onSync = { viewModel.syncNow(entry.entryDate) },
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    notes: List<DiaryEntry>,
    filterQuery: String,
    filterDate: String,
    filterTag: String,
    syncing: Boolean,
    resolveAsset: (String) -> File?,
    onQuery: (String) -> Unit,
    onDate: (String) -> Unit,
    onClearTag: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenRead: (String) -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
) {
    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "fabScale",
    )
    val density = LocalDensity.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "灵感匣",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "统计")
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
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.scale(fabScale),
                interactionSource = fabInteraction,
                containerColor = SlipTeal,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建灵感")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = onQuery,
                placeholder = { Text("搜索灵感") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = SlipTeal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (filterTag.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TagChip(text = filterTag, selected = true, onClick = onClearTag)
            }
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(
                    when {
                        filterQuery.isNotBlank() || filterTag.isNotBlank() ->
                            "没有符合条件的灵感。"
                        else -> "还没有灵感，点右下角 + 记下第一条。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(notes, key = { it.id }) { note ->
                        val itemVisible = remember { mutableStateOf(false) }
                        LaunchedEffect(note.id) { itemVisible.value = true }
                        AnimatedVisibility(
                            visible = itemVisible.value,
                            enter = fadeIn(tween(220)) + slideInVertically(
                                animationSpec = tween(220),
                                initialOffsetY = { with(density) { 8.dp.roundToPx() } },
                            ),
                        ) {
                            InspirationCard(
                                note = note,
                                resolveAsset = resolveAsset,
                                onOpen = { onOpenRead(note.id) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun TagChipRow(tags: List<String>, compact: Boolean = false) {
    if (tags.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.take(if (compact) 6 else 20).forEach { TagChip(text = it) }
    }
}

@Composable
private fun InspirationCard(
    note: DiaryEntry,
    resolveAsset: (String) -> File?,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
    ) {
        CardMarkdownBody(
            markdown = MarkdownImages.stripForDisplay(note.body),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 220.dp),
        )
        if (note.imageRels.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ImageThumbRow(rels = note.imageRels, resolveAsset = resolveAsset)
        }
        if (note.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            TagChipRow(note.tags, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            formatTinyDate(note.entryDate),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}


@Composable
private fun ImageThumbRow(
    rels: List<String>,
    resolveAsset: (String) -> File?,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rels.take(8).forEach { rel ->
            val file = resolveAsset(rel)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageFullColumn(
    rels: List<String>,
    resolveAsset: (String) -> File?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rels.forEach { rel ->
            val file = resolveAsset(rel)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (file != null) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "图片不可用",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardMarkdownBody(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSp: Float = 16f,
) {
    val state = rememberRichTextState()
    LaunchedEffect(markdown) {
        state.setMarkdown(markdown.ifBlank { "（空）" })
    }
    RichText(
        state = state,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AppFontFamily,
            fontSize = fontSp.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = (fontSp * 1.5f).sp,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteReadScreen(
    entry: DiaryEntry,
    context: DayContext,
    weatherLoading: Boolean,
    fontSp: Float,
    resolveAsset: (String) -> File?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
) {
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onPickImage(uri) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        noteDisplayTitle(entry),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = DisplayFontFamily,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "添加图片")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        var contentVisible by remember(entry.id) { mutableStateOf(false) }
        LaunchedEffect(entry.id) { contentVisible = true }
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(160)),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                if (entry.tags.isNotEmpty()) {
                    TagChipRow(entry.tags)
                    Spacer(Modifier.height(16.dp))
                }
                CardMarkdownBody(
                    markdown = MarkdownImages.stripForDisplay(entry.body),
                    modifier = Modifier.fillMaxWidth(),
                    fontSp = fontSp,
                )
                if (entry.imageRels.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    ImageFullColumn(rels = entry.imageRels, resolveAsset = resolveAsset)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    buildReadMeta(
                        timeText = formatDateTime(entry.createdAt.ifBlank { entry.updatedAt }),
                        placeText = when {
                            weatherLoading && !context.hasContext -> null
                            context.location.isNotBlank() -> context.location
                            else -> null
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private fun buildReadMeta(timeText: String, placeText: String?): String =
    listOfNotNull(timeText.takeIf { it.isNotBlank() && it != "—" }, placeText)
        .joinToString("  ·  ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(
    notes: List<DiaryEntry>,
    filterTag: String,
    onBack: () -> Unit,
    onSelectTag: (String) -> Unit,
    onClearTag: () -> Unit,
) {
    val tagFreq = remember(notes) {
        notes.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "统计",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "${notes.size}",
                style = MaterialTheme.typography.displayLarge,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.ExtraBold,
                color = SlipTeal,
            )
            Text("灵感卡片", style = MaterialTheme.typography.titleMedium)
            Text(
                "${tagFreq.size} 个标签",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "高频标签",
                style = MaterialTheme.typography.titleMedium,
                color = SlipTeal,
            )
            Text(
                "点选标签回到灵感屏筛选；再点可清除。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tagFreq.isEmpty()) {
                Text("还没有标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tagFreq.forEach { (tag, count) ->
                        val selected = filterTag == tag
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    if (selected) onClearTag() else onSelectTag(tag)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TagChip(text = tag)
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodosScreen(
    nativeTodos: List<NativeTodo>,
    obsidianTodos: List<ObsidianTodo>,
    loading: Boolean,
    status: String,
    onAdd: (String) -> Unit,
    onToggleNative: (String, Boolean) -> Unit,
    onDeleteNative: (String) -> Unit,
    onRefreshObsidian: () -> Unit,
    onCompleteObsidian: (ObsidianTodo) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            "待办",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("新待办") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                onAdd(draft)
                draft = ""
            }) { Text("添加") }
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        LazyColumn(Modifier.padding(top = 12.dp)) {
            item {
                Text("本机", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            items(nativeTodos, key = { it.id }) { todo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onToggleNative(todo.id, !todo.done) }) {
                        Icon(
                            if (todo.done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                        )
                    }
                    Text(
                        todo.text,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconButton(onClick = { onDeleteNative(todo.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除")
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Obsidian 日记",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = onRefreshObsidian, enabled = !loading) {
                        if (loading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            items(obsidianTodos, key = { it.key }) { todo ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(todo.content, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${todo.tagInner} · ${todo.filePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onCompleteObsidian(todo) }) { Text("完成") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: DiaryUiState,
    onSaveSync: (String, String) -> Unit,
    onFontChange: (Float) -> Unit,
    onSyncNow: () -> Unit,
    onSaveObsidian: (
        String, String, String, String, String, String, String, String, String, String,
    ) -> Unit,
    onAiEnabled: (Boolean) -> Unit,
    onAiPreview: () -> Unit,
) {
    var ep by remember(state.syncEndpoint) { mutableStateOf(state.syncEndpoint) }
    var tok by remember(state.syncToken) { mutableStateOf(state.syncToken) }
    var font by remember(state.editorFontSp) { mutableStateOf(state.editorFontSp) }
    var s3Ep by remember(state.s3Endpoint) { mutableStateOf(state.s3Endpoint) }
    var s3Region by remember(state.s3Region) { mutableStateOf(state.s3Region) }
    var s3Bucket by remember(state.s3Bucket) { mutableStateOf(state.s3Bucket) }
    var s3Ak by remember(state.s3AccessKey) { mutableStateOf(state.s3AccessKey) }
    var s3Sk by remember(state.s3SecretKey) { mutableStateOf(state.s3SecretKey) }
    var s3Prefix by remember(state.s3Prefix) { mutableStateOf(state.s3Prefix) }
    var diaryFolder by remember(state.obsidianDiaryFolder) { mutableStateOf(state.obsidianDiaryFolder) }
    var tagOpen by remember(state.tagOpen) { mutableStateOf(state.tagOpen) }
    var tagClose by remember(state.tagClose) { mutableStateOf(state.tagClose) }
    var completed by remember(state.completedLabel) { mutableStateOf(state.completedLabel) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
        )

        Text("书写", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("正文字号 ${font.toInt()} sp")
        Slider(
            value = font,
            onValueChange = {
                font = it
                onFontChange(it)
            },
            valueRange = 14f..24f,
            steps = 9,
        )

        Text("同步", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = ep,
            onValueChange = { ep = it },
            label = { Text("服务地址") },
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
            TextButton(onClick = { onSaveSync(ep, tok) }) { Text("保存") }
            TextButton(onClick = onSyncNow, enabled = !state.syncing) {
                Text(if (state.syncing) "同步中…" else "立即同步")
            }
        }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.labelMedium)
        }

        Text("Obsidian / 对象存储", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(value = s3Ep, onValueChange = { s3Ep = it }, label = { Text("S3 Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s3Region, onValueChange = { s3Region = it }, label = { Text("Region") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s3Bucket, onValueChange = { s3Bucket = it }, label = { Text("Bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s3Ak, onValueChange = { s3Ak = it }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s3Sk, onValueChange = { s3Sk = it }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s3Prefix, onValueChange = { s3Prefix = it }, label = { Text("Prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = diaryFolder, onValueChange = { diaryFolder = it }, label = { Text("日记文件夹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = tagOpen, onValueChange = { tagOpen = it }, label = { Text("开") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = tagClose, onValueChange = { tagClose = it }, label = { Text("闭") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = completed, onValueChange = { completed = it }, label = { Text("完成文案") }, modifier = Modifier.weight(1.4f))
        }
        TextButton(onClick = {
            onSaveObsidian(s3Ep, s3Region, s3Bucket, s3Ak, s3Sk, s3Prefix, diaryFolder, tagOpen, tagClose, completed)
        }) { Text("保存 Obsidian 设置") }

        Text("洞察 / AI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("启用 AI 接口（预留）")
            Switch(checked = state.aiEnabled, onCheckedChange = onAiEnabled)
        }
        TextButton(onClick = onAiPreview) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("预览今日日报钩子")
        }
        if (state.aiPreview.isNotBlank()) {
            Text(state.aiPreview, style = MaterialTheme.typography.bodyMedium)
        }

        Text("关于", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "灵感匣 · 本地优先的灵感收集器。\n卡片可同步；待办可自建，也可从 Obsidian 日记经对象存储读写。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteEditorScreen(
    entry: DiaryEntry,
    syncing: Boolean,
    fontSp: Float,
    onBack: () -> Unit,
    onBodyChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onPickImage: (android.net.Uri, (String) -> Unit) -> Unit,
    onSync: () -> Unit,
) {
    val richTextState = rememberRichTextState()
    var loadedEntryId by remember { mutableStateOf<String?>(null) }
    var tagsText by remember(entry.id) { mutableStateOf(entry.tags.joinToString(", ")) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(entry.id) {
        richTextState.setMarkdown(MarkdownImages.stripForDisplay(entry.body))
        loadedEntryId = entry.id
        tagsText = entry.tags.joinToString(", ")
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        noteDisplayTitle(entry),
                        maxLines = 1,
                        fontFamily = DisplayFontFamily,
                    )
                },
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
                    containerColor = Color.Transparent,
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
            OutlinedTextField(
                value = tagsText,
                onValueChange = {
                    tagsText = it
                    onTagsChange(it)
                },
                label = { Text("标签（逗号分隔，也可在正文写 #标签）") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    cursorColor = SlipTeal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            MarkdownToolbar(state = richTextState)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
                        cursorColor = SlipTeal,
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
            val md = state.toMarkdown().trimEnd() + "\n- "
            state.setMarkdown(md)
        }) {
            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "列表")
        }
    }
}

private fun formatHeading(entryDate: String): String {
    val d = LocalDate.parse(entryDate)
    val week = d.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
    return "${d.year}年${d.monthValue.toString().padStart(2, '0')}月" +
        "${d.dayOfMonth.toString().padStart(2, '0')}日  $week"
}

private fun formatTinyDate(entryDate: String): String {
    val d = LocalDate.parse(entryDate)
    return "${d.monthValue}/${d.dayOfMonth}"
}

private fun formatDateTime(iso: String): String {
    if (iso.isBlank()) return "—"
    return try {
        val odt = OffsetDateTime.parse(iso)
        odt.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

private fun formatShortDate(entryDate: String): String {
    val d = LocalDate.parse(entryDate)
    val week = d.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.CHINA)
    return "${d.monthValue}月${d.dayOfMonth}日 · $week"
}

private fun noteDisplayTitle(note: DiaryEntry): String {
    val t = note.title.trim()
    return if (t.isBlank() || t == note.entryDate) {
        notePreview(note.body).takeIf { it != "（空）" } ?: "未命名灵感"
    } else {
        t
    }
}

private fun notePreview(body: String): String {
    return body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("![") && it != "［图片］" && !it.startsWith("#") }
        .firstOrNull()
        ?.removePrefix("#")
        ?.trim()
        ?.take(120)
        ?: "（空）"
}
