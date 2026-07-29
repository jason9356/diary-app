package com.personaldiary.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personaldiary.android.ui.theme.AppFontFamily
import com.personaldiary.android.ui.theme.ThemePalette

@Composable
private fun themedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsHubScreen(
    onOpenAppearance: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsNavRow(
                icon = Icons.Outlined.Palette,
                title = "外观与书写",
                subtitle = "主题、正文字号",
                onClick = onOpenAppearance,
            )
            SettingsNavRow(
                icon = Icons.Outlined.CloudSync,
                title = "同步",
                subtitle = "数据存放 · 自建服务",
                onClick = onOpenSync,
            )
            SettingsNavRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "洞察 / AI",
                subtitle = "日报钩子",
                onClick = onOpenAi,
            )
            SettingsNavRow(
                icon = Icons.Outlined.Info,
                title = "关于",
                subtitle = "灵感匣",
                onClick = onOpenAbout,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun SettingsAppearanceScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onFontChange: (Float) -> Unit,
    onThemeMode: (String) -> Unit,
    onThemePalette: (String) -> Unit,
) {
    var font by remember(state.editorFontSp) { mutableStateOf(state.editorFontSp) }
    SettingsSubScaffold(title = "外观与书写", onBack = onBack) {
        SettingsSectionLabel("主题")
        ThemePalette.values().forEach { palette ->
            ThemePaletteOption(
                selected = state.themePalette == palette.id,
                title = palette.label,
                subtitle = palette.hint,
                onClick = { onThemePalette(palette.id) },
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("深浅")
        ThemeModeOption(
            selected = state.themeMode == "system",
            title = "跟随系统",
            onClick = { onThemeMode("system") },
        )
        ThemeModeOption(
            selected = state.themeMode == "light",
            title = "浅色",
            onClick = { onThemeMode("light") },
        )
        ThemeModeOption(
            selected = state.themeMode == "dark",
            title = "深色",
            onClick = { onThemeMode("dark") },
        )
        Spacer(Modifier.height(8.dp))
        SettingsSectionLabel("书写")
        Text(
            "正文字号 ${font.toInt()} sp",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = font,
            onValueChange = {
                font = it
                onFontChange(it)
            },
            valueRange = 14f..24f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
internal fun SettingsSyncHubScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenCards: () -> Unit,
) {
    val targetLabel = when (state.storageTarget) {
        "sync_server" -> "自建同步服务"
        "cloud" -> when (state.cloudProvider) {
            "webdav" -> "云盘 · WebDAV"
            "baidu" -> "云盘 · 百度"
            "onedrive" -> "云盘 · OneDrive"
            "google_drive" -> "云盘 · Google Drive"
            "aliyun_drive" -> "云盘 · 阿里云盘"
            else -> "云盘"
        }
        else -> "仅本机"
    }
    SettingsSubScaffold(title = "同步", onBack = onBack) {
        SettingsNavRow(
            icon = Icons.Outlined.Storage,
            title = "数据存放",
            subtitle = targetLabel,
            onClick = onOpenStorage,
        )
        SettingsNavRow(
            icon = Icons.Outlined.CloudSync,
            title = "自建服务凭证",
            subtitle = if (state.syncEndpoint.isBlank()) "未配置" else state.syncEndpoint,
            onClick = onOpenCards,
        )
    }
}

@Composable
internal fun SettingsStorageScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onSelectTarget: (String) -> Unit,
    onOpenServer: () -> Unit,
    onOpenCloud: () -> Unit,
    onSyncNow: () -> Unit,
) {
    SettingsSubScaffold(title = "数据存放", onBack = onBack) {
        StorageTargetOption(
            selected = state.storageTarget == "local",
            title = "仅本机",
            subtitle = "不自动上行",
            onClick = { onSelectTarget("local") },
        )
        StorageTargetOption(
            selected = state.storageTarget == "sync_server",
            title = "自建同步服务",
            subtitle = "endpoint + Token",
            onClick = { onSelectTarget("sync_server") },
        )
        StorageTargetOption(
            selected = state.storageTarget == "cloud",
            title = "云盘",
            subtitle = "WebDAV / 其它",
            onClick = { onSelectTarget("cloud") },
        )
        when (state.storageTarget) {
            "sync_server" -> {
                SettingsNavRow(
                    icon = Icons.Outlined.CloudSync,
                    title = "服务地址与 Token",
                    subtitle = state.syncEndpoint.ifBlank { "尚未填写" },
                    onClick = onOpenServer,
                )
            }
            "cloud" -> {
                SettingsNavRow(
                    icon = Icons.Outlined.Storage,
                    title = "云盘厂商与凭证",
                    subtitle = state.cloudProvider,
                    onClick = onOpenCloud,
                )
            }
        }
        if (state.storageTarget != "local") {
            TextButton(onClick = onSyncNow, enabled = !state.syncing) {
                Text(if (state.syncing) "同步中…" else "立即同步")
            }
        }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun SettingsCloudProviderScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenStub: () -> Unit,
) {
    SettingsSubScaffold(title = "云盘", onBack = onBack) {
        listOf(
            "webdav" to ("WebDAV" to "坚果云 / Nextcloud"),
            "baidu" to ("百度网盘" to "待接入"),
            "onedrive" to ("OneDrive" to "待接入"),
            "google_drive" to ("Google Drive" to "待接入"),
            "aliyun_drive" to ("阿里云盘" to "待接入"),
        ).forEach { (id, pair) ->
            StorageTargetOption(
                selected = state.cloudProvider == id,
                title = pair.first,
                subtitle = pair.second,
                onClick = { onSelectProvider(id) },
            )
        }
        if (state.cloudProvider == "webdav") {
            SettingsNavRow(
                icon = Icons.Outlined.CloudSync,
                title = "WebDAV 设置",
                subtitle = state.webdavUrl.ifBlank { "填写地址与账号" },
                onClick = onOpenWebDav,
            )
        } else {
            SettingsNavRow(
                icon = Icons.Outlined.Storage,
                title = "厂商配置",
                subtitle = "endpoint / appKey / token",
                onClick = onOpenStub,
            )
        }
    }
}

@Composable
internal fun SettingsWebDavScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    onSyncNow: () -> Unit,
) {
    var url by remember(state.webdavUrl) { mutableStateOf(state.webdavUrl) }
    var user by remember(state.webdavUser) { mutableStateOf(state.webdavUser) }
    var pass by remember(state.webdavPass) { mutableStateOf(state.webdavPass) }
    var root by remember(state.webdavRoot) { mutableStateOf(state.webdavRoot) }
    SettingsSubScaffold(title = "WebDAV", onBack = onBack) {
        val fields = themedFieldColors()
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("密码 / 应用密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        OutlinedTextField(
            value = root,
            onValueChange = { root = it },
            label = { Text("根路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSave(url, user, pass, root) }) { Text("保存") }
            TextButton(onClick = onSyncNow, enabled = !state.syncing) {
                Text(if (state.syncing) "同步中…" else "立即同步")
            }
        }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun SettingsCloudStubScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var endpoint by remember(state.cloudEndpoint) { mutableStateOf(state.cloudEndpoint) }
    var appKey by remember(state.cloudAppKey) { mutableStateOf(state.cloudAppKey) }
    var token by remember(state.cloudToken) { mutableStateOf(state.cloudToken) }
    SettingsSubScaffold(title = "厂商配置", onBack = onBack) {
        val fields = themedFieldColors()
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Endpoint") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        OutlinedTextField(
            value = appKey,
            onValueChange = { appKey = it },
            label = { Text("App Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fields,
        )
        TextButton(onClick = { onSave(endpoint, appKey, token) }) { Text("保存") }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StorageTargetOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSyncCardsScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onSaveSync: (String, String) -> Unit,
    onSyncNow: () -> Unit,
) {
    var ep by remember(state.syncEndpoint) { mutableStateOf(state.syncEndpoint) }
    var tok by remember(state.syncToken) { mutableStateOf(state.syncToken) }
    SettingsSubScaffold(title = "灵感卡片同步", onBack = onBack) {
        OutlinedTextField(
            value = ep,
            onValueChange = { ep = it },
            label = { Text("服务地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = themedFieldColors(),
        )
        OutlinedTextField(
            value = tok,
            onValueChange = { tok = it },
            label = { Text("Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = themedFieldColors(),
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
    }
}

@Composable
internal fun SettingsSyncObsidianScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSaveObsidian: (
        String, String, String, String, String, String, String, String, String, String,
    ) -> Unit,
) {
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

    SettingsSubScaffold(title = "Obsidian 待办", onBack = onBack) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("使用 Obsidian 待办", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = state.obsidianTodosEnabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }

        if (!state.obsidianTodosEnabled) {
            return@SettingsSubScaffold
        }

        SettingsSectionLabel("连接")
        val fields = themedFieldColors()
        OutlinedTextField(value = s3Ep, onValueChange = { s3Ep = it }, label = { Text("S3 Endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        OutlinedTextField(value = s3Region, onValueChange = { s3Region = it }, label = { Text("Region") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        OutlinedTextField(value = s3Bucket, onValueChange = { s3Bucket = it }, label = { Text("Bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        OutlinedTextField(value = s3Ak, onValueChange = { s3Ak = it }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        OutlinedTextField(value = s3Sk, onValueChange = { s3Sk = it }, label = { Text("Secret Key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        OutlinedTextField(value = s3Prefix, onValueChange = { s3Prefix = it }, label = { Text("Prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)

        SettingsSectionLabel("日记规则")
        OutlinedTextField(value = diaryFolder, onValueChange = { diaryFolder = it }, label = { Text("日记文件夹") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fields)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = tagOpen, onValueChange = { tagOpen = it }, label = { Text("开") }, modifier = Modifier.weight(1f), colors = fields)
            OutlinedTextField(value = tagClose, onValueChange = { tagClose = it }, label = { Text("闭") }, modifier = Modifier.weight(1f), colors = fields)
            OutlinedTextField(value = completed, onValueChange = { completed = it }, label = { Text("完成文案") }, modifier = Modifier.weight(1.4f), colors = fields)
        }
        TextButton(onClick = {
            onSaveObsidian(s3Ep, s3Region, s3Bucket, s3Ak, s3Sk, s3Prefix, diaryFolder, tagOpen, tagClose, completed)
        }) { Text("保存") }
        if (state.status.isNotBlank()) {
            Text(state.status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun SettingsAiScreen(
    state: DiaryUiState,
    onBack: () -> Unit,
    onAiEnabled: (Boolean) -> Unit,
    onAiPreview: () -> Unit,
) {
    SettingsSubScaffold(title = "洞察 / AI", onBack = onBack) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("启用 AI 接口", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = state.aiEnabled,
                onCheckedChange = onAiEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
        TextButton(onClick = onAiPreview) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("预览今日日报钩子")
        }
        if (state.aiPreview.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    state.aiPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsAboutScreen(onBack: () -> Unit) {
    SettingsSubScaffold(title = "关于", onBack = onBack) {
        Text(
            "灵感匣",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ThemePaletteOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
