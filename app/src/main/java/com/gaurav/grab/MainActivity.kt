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
                        viewModel.analyzeUrl(sharedUrl, sharedUrl.contains("playlist") || sharedUrl.contains("/p/"))
                    }
                }

                GrabAppScreen(
                    uiState = uiState,
                    downloads = downloads,
                    onUrlChange = viewModel::onUrlChanged,
                    onAnalyze = viewModel::analyzeUrl,
                    onSelectFormat = viewModel::startDownload,
                    onDownloadPlaylistEntry = viewModel::downloadSinglePlaylistEntry,
                    onDismissSheet = viewModel::dismissSheet,
                    onPauseDownload = viewModel::pauseDownload,
                    onResumeDownload = viewModel::resumeDownload,
                    onRemoveHistory = viewModel::removeHistoryItem,
                    onClearAllHistory = viewModel::clearAllHistory,
                    onFilterChange = viewModel::onFilterChanged,
                    onThemeChange = viewModel::setThemeMode,
                    onSetThreads = viewModel::setTurboThreads,
                    onToggleNotifications = viewModel::toggleNotifications,
                    onTogglePlaylistDialog = viewModel::togglePlaylistDialog,
                    onToggleMusicStudio = viewModel::toggleMusicStudioDialog,
                    onToggleAllPlatforms = viewModel::toggleAllPlatformsSheet,
                    onSelectPlatformHub = viewModel::selectPlatformHub,
                    onOpenSettingSheet = viewModel::openSettingSheet,
                    onUpdateExtractors = viewModel::triggerExtractorUpdate,
                    onToast = viewModel::showToastMessage
                )
            }
        }
    }
}
