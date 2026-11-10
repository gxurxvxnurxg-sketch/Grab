package com.gaurav.grab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaurav.grab.presentation.theme.GrabTheme
import com.gaurav.grab.presentation.ui.GrabAppScreen
import com.gaurav.grab.presentation.viewmodel.GrabViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        setContent {
            val viewModel: GrabViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val downloads by viewModel.downloads.collectAsStateWithLifecycle()

            val useDark = when (uiState.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            GrabTheme(darkTheme = useDark) {
                LaunchedEffect(sharedUrl) {
                    if (!sharedUrl.isNullOrBlank()) {
                        viewModel.analyzeUrl(sharedUrl, false)
                    }
                }

                GrabAppScreen(
                    uiState = uiState,
                    downloads = downloads,
                    onUrlChange = viewModel::onUrlChanged,
                    onAnalyze = viewModel::analyzeUrl,
                    onSelectFormat = viewModel::startDownload,
                    onDismissSheet = viewModel::dismissSheet,
                    onCancelDownload = viewModel::cancelDownload,
                    onRemoveHistory = viewModel::removeHistoryItem,
                    onClearAllHistory = viewModel::clearAllHistory,
                    onFilterChange = viewModel::onFilterChanged,
                    onThemeChange = viewModel::setThemeMode,
                    onToggleBraveShield = viewModel::toggleBraveShield,
                    onTogglePlaylistDialog = viewModel::togglePlaylistDialog,
                    onToggleAllPlatforms = viewModel::toggleAllPlatformsSheet,
                    onSelectPlatformHub = viewModel::selectPlatformHub,
                    onOpenBrowser = viewModel::openInAppBrowser,
                    onUpdateExtractors = viewModel::triggerExtractorUpdate,
                    onToast = viewModel::showToastMessage
                )
            }
        }
    }
}
