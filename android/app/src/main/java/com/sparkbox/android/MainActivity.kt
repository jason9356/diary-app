package com.sparkbox.android

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sparkbox.android.ui.SparkboxApp
import com.sparkbox.android.ui.SparkboxViewModel
import com.sparkbox.android.ui.theme.SparkboxTheme
import com.sparkbox.android.ui.theme.LocalAppColors

class MainActivity : ComponentActivity() {
    private val viewModel: SparkboxViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SparkboxViewModel(application) as T
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
            SparkboxTheme(paletteId = state.themePalette, darkTheme = dark) {
                val view = LocalView.current
                val field = LocalAppColors.current.field
                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = AndroidColor.TRANSPARENT
                    window.navigationBarColor = AndroidColor.TRANSPARENT
                    window.decorView.setBackgroundColor(field.toArgb())
                    val insets = WindowCompat.getInsetsController(window, view)
                    insets.isAppearanceLightStatusBars = !dark
                    insets.isAppearanceLightNavigationBars = !dark
                }
                SparkboxApp(viewModel)
            }
        }
    }
}
