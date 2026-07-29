package com.personaldiary.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personaldiary.android.ui.DiaryApp
import com.personaldiary.android.ui.DiaryViewModel
import com.personaldiary.android.ui.theme.DiaryTheme
import com.personaldiary.android.ui.theme.LocalAppColors

class MainActivity : ComponentActivity() {
    private val viewModel: DiaryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiaryViewModel(application) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            DiaryTheme(paletteId = state.themePalette, darkTheme = dark) {
                val view = LocalView.current
                val field = LocalAppColors.current.field
                SideEffect {
                    val window = window
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = field.copy(alpha = 0.94f).toArgb()
                    val insets = WindowCompat.getInsetsController(window, view)
                    insets.isAppearanceLightStatusBars = !dark
                    insets.isAppearanceLightNavigationBars = !dark
                }
                DiaryApp(viewModel)
            }
        }
    }
}
