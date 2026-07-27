package com.personaldiary.android.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.BitmapFactory
import com.personaldiary.android.DiaryApplication
import com.personaldiary.android.data.DiaryEntry
import com.personaldiary.android.ui.theme.InkAccent
import com.personaldiary.android.ui.theme.WenKaiFamily
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

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

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.addImage(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    label = { Text("今天") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = {
                        tab = 1
                        viewModel.refreshTimeline()
                    },
                    icon = { Icon(Icons.Outlined.ListAlt, contentDescription = null) },
                    label = { Text("时间线") },
                    colors = colors,
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> EditorPane(
                state = state,
                padding = padding,
                onBodyChange = viewModel::onBodyChange,
                onPickImage = { imageLauncher.launch("image/*") },
            )
            else -> TimelinePane(
                entries = state.timeline,
                padding = padding,
                onOpen = { date ->
                    viewModel.openDate(date)
                    tab = 0
                },
            )
        }
    }
}

@Composable
private fun EditorPane(
    state: DiaryUiState,
    padding: PaddingValues,
    onBodyChange: (String) -> Unit,
    onPickImage: () -> Unit,
) {
    val entry = state.entry
    val app = LocalContext.current.applicationContext as DiaryApplication

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Text(
            text = "日记",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = formatHeading(entry.entryDate),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val contextLine = entry.contextLine().ifBlank {
                if (state.weatherLoading) "正在记录地点与天气…" else ""
            }
            if (contextLine.isNotBlank()) {
                Text(
                    text = contextLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (state.weatherLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPickImage) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = "插入图片",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (entry.imageRels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entry.imageRels.forEach { rel ->
                    val file = app.repository.absoluteAsset(rel)
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val bmp = remember(file.path, file.lastModified()) {
                            if (file.exists()) {
                                BitmapFactory.Options().run {
                                    inSampleSize = 4
                                    BitmapFactory.decodeFile(file.absolutePath, this)
                                }
                            } else null
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = state.status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            BasicTextField(
                value = entry.body,
                onValueChange = onBodyChange,
                textStyle = TextStyle(
                    fontFamily = WenKaiFamily,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(InkAccent),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    if (entry.body.isEmpty()) {
                        Text(
                            "写点什么…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun TimelinePane(
    entries: List<DiaryEntry>,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Text("时间线", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Text(
                "还没有日记",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(entries, key = { it.entryDate }) { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry.entryDate) }
                            .padding(vertical = 14.dp)
                    ) {
                        Text(
                            entry.entryDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (entry.contextLine().isNotBlank()) {
                            Text(
                                entry.contextLine(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            }
        }
    }
}

private fun formatHeading(entryDate: String): String {
    val d = LocalDate.parse(entryDate)
    val week = d.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.CHINA)
    return "${d.year}年${d.monthValue.toString().padStart(2, '0')}月" +
        "${d.dayOfMonth.toString().padStart(2, '0')}日  $week"
}
