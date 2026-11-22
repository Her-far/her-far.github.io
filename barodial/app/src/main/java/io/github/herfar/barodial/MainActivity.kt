package io.github.herfar.barodial

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.herfar.barodial.ui.BaroDialApp
import io.github.herfar.barodial.ui.MainViewModel
import io.github.herfar.barodial.ui.theme.BaroDialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShortcut(intent)

        val launcher = (application as BaroDialApplication).container.dialerLauncher
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            BaroDialTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                BaroDialApp(viewModel = viewModel, dialerLauncher = launcher)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        when (intent?.action) {
            ACTION_SEARCH -> viewModel.startSearch()
            ACTION_KEYPAD -> viewModel.openKeypad()
        }
    }

    companion object {
        const val ACTION_SEARCH = "io.github.herfar.barodial.action.SEARCH"
        const val ACTION_KEYPAD = "io.github.herfar.barodial.action.KEYPAD"
    }
}
