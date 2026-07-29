package com.personaldiary.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbCloudy
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.personaldiary.android.data.DayContext
import com.personaldiary.android.data.DeviceLabels
import com.personaldiary.android.data.DiaryDates
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.data.MarkdownImages
import com.personaldiary.android.data.NativeTodo
import com.personaldiary.android.ui.theme.AppFontFamily
import com.personaldiary.android.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private object Routes {
    const val Home = "home"
    const val Todos = "todos"
    const val TodoDetail = "todos/detail/{id}"
    const val Settings = "settings"
    const val SettingsAppearance = "settings/appearance"
    const val SettingsSync = "settings/sync"
    const val SettingsStorage = "settings/sync/storage"
    const val SettingsStorageCloud = "settings/sync/storage/cloud"
    const val SettingsStorageWebDav = "settings/sync/storage/webdav"
    const val SettingsStorageStub = "settings/sync/storage/stub"
    const val SettingsAi = "settings/ai"
    const val SettingsAbout = "settings/about"
    const val Stats = "stats"
    const val Read = "read/{id}"
    const val Edit = "edit/{id}"
    fun read(id: String) = "read/$id"
    fun edit(id: String) = "edit/$id"
    fun todoDetail(id: String) = "todos/detail/$id"
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = LocalAppColors.current.field.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
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
                        label = { Text("事项") },
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
        Box(
            Modifier
                .fillMaxSize()
                .background(LocalAppColors.current.brush())
                .padding(padding),
        ) {
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
                    fontSp = state.editorFontSp,
                    onToggleNative = viewModel::setNativeTodoDone,
                    onOpenNative = { id -> navController.navigate(Routes.todoDetail(id)) },
                    onNew = {
                        viewModel.createNativeTodo { id ->
                            navController.navigate(Routes.todoDetail(id))
                        }
                    },
                )
            }
            composable(
                route = Routes.TodoDetail,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id").orEmpty()
                val todo = state.nativeTodos.firstOrNull { it.id == id }
                    ?: viewModel.getNativeTodo(id)
                if (todo == null) {
                    LaunchedEffect(id) { navController.popBackStack() }
                } else {
                    TodoDetailScreen(
                        initial = todo,
                        onBack = { navController.popBackStack() },
                        onSave = { saved ->
                            viewModel.upsertNativeTodo(saved)
                            navController.popBackStack()
                        },
                        onDelete = {
                            viewModel.deleteNativeTodo(todo.id)
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(Routes.Settings) {
                SettingsHubScreen(
                    onOpenAppearance = { navController.navigate(Routes.SettingsAppearance) },
                    onOpenSync = { navController.navigate(Routes.SettingsSync) },
                    onOpenAi = { navController.navigate(Routes.SettingsAi) },
                    onOpenAbout = { navController.navigate(Routes.SettingsAbout) },
                )
            }
            composable(Routes.SettingsAppearance) {
                SettingsAppearanceScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onFontChange = viewModel::setEditorFontSp,
                    onThemeMode = viewModel::setThemeMode,
                    onThemePalette = viewModel::setThemePalette,
                )
            }
            composable(Routes.SettingsSync) {
                SettingsSyncHubScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenStorage = { navController.navigate(Routes.SettingsStorage) },
                )
            }
            composable(Routes.SettingsStorage) {
                SettingsStorageScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSelectTarget = viewModel::setStorageTarget,
                    onOpenCloud = { navController.navigate(Routes.SettingsStorageCloud) },
                    onSyncNow = { viewModel.syncNow(DiaryDates.today()) },
                )
            }
            composable(Routes.SettingsStorageCloud) {
                SettingsCloudProviderScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSelectProvider = viewModel::setCloudProvider,
                    onOpenWebDav = { navController.navigate(Routes.SettingsStorageWebDav) },
                    onOpenStub = { navController.navigate(Routes.SettingsStorageStub) },
                )
            }
            composable(Routes.SettingsStorageWebDav) {
                SettingsWebDavScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = viewModel::saveWebDavSettings,
                    onSyncNow = { viewModel.syncNow(DiaryDates.today()) },
                )
            }
            composable(Routes.SettingsStorageStub) {
                SettingsCloudStubScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = viewModel::saveCloudStubSettings,
                )
            }
            composable(Routes.SettingsAi) {
                SettingsAiScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAiEnabled = viewModel::setAiEnabled,
                    onAiPreview = viewModel::runAiDigestPreview,
                )
            }
            composable(Routes.SettingsAbout) {
                SettingsAboutScreen(onBack = { navController.popBackStack() })
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
private fun MainTabTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun TabActionIcon(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            content()
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            MainTabTopBar(
                title = "灵感匣",
                actions = {
                    TabActionIcon(onClick = onOpenStats) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "统计", modifier = Modifier.size(24.dp))
                    }
                    TabActionIcon(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Outlined.CloudSync, contentDescription = "同步", modifier = Modifier.size(24.dp))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.scale(fabScale),
                interactionSource = fabInteraction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                placeholder = { Text("搜索") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        filterQuery.isNotBlank() || filterTag.isNotBlank() -> "无匹配结果"
                        else -> "暂无灵感"
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
                        InspirationCard(
                            note = note,
                            resolveAsset = resolveAsset,
                            onOpen = { onOpenRead(note.id) },
                        )
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
    val color = MaterialTheme.colorScheme.onSurface
    val annotated = remember(markdown, color) {
        DisplayMarkdown.toAnnotated(markdown, color)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = AppFontFamily,
            fontSize = fontSp.sp,
            color = color,
            lineHeight = (fontSp * 1.55f).sp,
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
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TabActionIcon(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    TabActionIcon(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    TabActionIcon(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
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
                ReadContextBlock(
                    context = context,
                    weatherLoading = weatherLoading,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ReadContextBlock(
    context: DayContext,
    weatherLoading: Boolean,
) {
    val place = DeviceLabels.shorten(context.location, 22)
        .ifBlank { if (weatherLoading) "定位中…" else "" }
    val device = DeviceLabels.shorten(context.device, 18)
    val weather = DeviceLabels.shorten(context.weatherLine(), 20)

    val rows = listOfNotNull(
        place.takeIf { it.isNotBlank() }?.let { Icons.Outlined.LocationOn to it },
        device.takeIf { it.isNotBlank() }?.let { Icons.Outlined.PhoneAndroid to it },
        weather.takeIf { it.isNotBlank() }?.let { Icons.Outlined.WbCloudy to it },
    )
    if (rows.isEmpty() && !weatherLoading) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (icon, text) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (rows.isEmpty() && weatherLoading) {
            Text(
                "正在获取地点与天气…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

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
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TabActionIcon(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.primary,
            )
            Text("灵感卡片", style = MaterialTheme.typography.titleMedium)
            Text(
                "${tagFreq.size} 个标签",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("高频标签", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (tagFreq.isEmpty()) {
                Text("暂无标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun todoKindLabel(kind: String): String = when (kind) {
    "note" -> "备忘"
    "errand" -> "外出"
    "other" -> "其他"
    else -> "事项"
}

private fun levelLabel(level: Int): String = when (level) {
    1 -> "低"
    2 -> "中"
    3 -> "高"
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodosScreen(
    nativeTodos: List<NativeTodo>,
    fontSp: Float,
    onToggleNative: (String, Boolean) -> Unit,
    onOpenNative: (String) -> Unit,
    onNew: () -> Unit,
) {
    val openCount = nativeTodos.count { !it.done }
    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "fabScale",
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { MainTabTopBar(title = "事项") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                modifier = Modifier.scale(fabScale),
                interactionSource = fabInteraction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建事项")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (openCount > 0) "未完成 $openCount" else "全部",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (nativeTodos.isEmpty()) {
                    item {
                        Text(
                            "暂无事项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(nativeTodos, key = { it.id }) { todo ->
                    val tags = buildList {
                        if (todo.kind != "task") add(todoKindLabel(todo.kind))
                        if (todo.priority > 0) add("优${levelLabel(todo.priority)}")
                        if (todo.urgency > 0) add("急${levelLabel(todo.urgency)}")
                        if (todo.dueAt.isNotBlank()) add(todo.dueAt.take(10))
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenNative(todo.id) },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onToggleNative(todo.id, !todo.done) }) {
                                Icon(
                                    if (todo.done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    todo.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = fontSp.sp,
                                        lineHeight = (fontSp * 1.45f).sp,
                                        color = if (todo.done) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (tags.isNotEmpty()) {
                                    Text(
                                        tags.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDetailScreen(
    initial: NativeTodo,
    onBack: () -> Unit,
    onSave: (NativeTodo) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(initial.id) { mutableStateOf(initial.text) }
    var detail by remember(initial.id) { mutableStateOf(initial.detail) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind.ifBlank { "task" }) }
    var dueAt by remember(initial.id) { mutableStateOf(initial.dueAt) }
    var priority by remember(initial.id) { mutableStateOf(initial.priority) }
    var urgency by remember(initial.id) { mutableStateOf(initial.urgency) }
    var done by remember(initial.id) { mutableStateOf(initial.done) }
    val isBlankDraft = text.isBlank() && detail.isBlank()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "事项",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TabActionIcon(
                        onClick = {
                            if (isBlankDraft) onDelete() else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDelete) { Text("删除") }
                    TextButton(
                        onClick = {
                            if (text.isBlank()) return@TextButton
                            onSave(
                                initial.copy(
                                    text = text.trim(),
                                    detail = detail.trim(),
                                    kind = kind,
                                    dueAt = dueAt.trim(),
                                    priority = priority,
                                    urgency = urgency,
                                    done = done,
                                ),
                            )
                        },
                    ) { Text("保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text("详情") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Text("类型", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("task" to "事项", "note" to "备忘", "errand" to "外出", "other" to "其他").forEach { (id, label) ->
                    Surface(
                        onClick = { kind = id },
                        shape = RoundedCornerShape(10.dp),
                        color = if (kind == id) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = dueAt,
                onValueChange = { dueAt = it },
                label = { Text("到期") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Text("优先级", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            LevelPicker(value = priority, onChange = { priority = it })
            Text("紧急程度", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            LevelPicker(value = urgency, onChange = { urgency = it })
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { done = !done }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (done) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (done) "已完成" else "未完成",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun LevelPicker(value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(0 to "无", 1 to "低", 2 to "中", 3 to "高").forEach { (level, label) ->
            Surface(
                onClick = { onChange(level) },
                shape = RoundedCornerShape(10.dp),
                color = if (value == level) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    entry: DiaryEntry,
    syncing: Boolean,
    fontSp: Float,
    onBack: () -> Unit,
    onBodyChange: (String) -> Unit,
    onPickImage: (android.net.Uri, (String) -> Unit) -> Unit,
    onSync: () -> Unit,
) {
    var draft by remember(entry.id) {
        mutableStateOf(MarkdownImages.stripForDisplay(entry.body))
    }
    var lastSavedBody by remember(entry.id) { mutableStateOf(entry.body) }
    val liveTags = remember(draft) { MarkdownImages.extractHashTags(draft) }

    LaunchedEffect(entry.id, entry.body) {
        if (entry.body != lastSavedBody) {
            draft = MarkdownImages.stripForDisplay(entry.body)
            lastSavedBody = entry.body
        }
    }

    LaunchedEffect(draft, entry.id) {
        delay(450)
        val merged = MarkdownImages.mergeEditorText(draft, entry.body)
        if (merged != entry.body) {
            lastSavedBody = merged
            onBodyChange(merged)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            onPickImage(uri) { rel ->
                val withImg = draft.trimEnd() + "\n\n![image]($rel)\n"
                draft = MarkdownImages.stripForDisplay(withImg)
                onBodyChange(MarkdownImages.mergeEditorText(draft, entry.body))
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        formatHeading(entry.entryDate),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TabActionIcon(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    TabActionIcon(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "插入图片",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    TabActionIcon(onClick = onSync, enabled = !syncing) {
                        if (syncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.CloudSync,
                                contentDescription = "同步",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = AppFontFamily,
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * 1.6f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxSize()) {
                                if (draft.isBlank()) {
                                    Text(
                                        "正文",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = fontSp.sp,
                                            lineHeight = (fontSp * 1.6f).sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        ),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (liveTags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        TagChipRow(liveTags, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
