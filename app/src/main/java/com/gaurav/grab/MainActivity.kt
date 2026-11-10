package com.gaurav.grab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("https?://\\S+").find(it)?.value } else null

        setContent {
            val viewModel: GrabViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val downloads by viewModel.downloads.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> viewModel.setNotifPerm(g) }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    viewModel.setNotifPerm(granted)
                    if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else viewModel.setNotifPerm(true)
            }

            val useDark = when (uiState.settings.themeMode) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
            GrabTheme(darkTheme = useDark, dynamicColor = false) {
                LaunchedEffect(sharedUrl) { if (!sharedUrl.isNullOrBlank()) { viewModel.onUrlChanged(sharedUrl); viewModel.analyzeUrl() } }
                GrabAppScreen(
                    uiState = uiState, downloads = downloads,
                    onUrlChange = viewModel::onUrlChanged, onAnalyze = viewModel::analyzeUrl,
                    onSelectFormat = viewModel::startDownload, onDismissSheet = viewModel::dismissSheet,
                    onCancelDownload = viewModel::cancelDownload, onRemoveHistory = viewModel::removeHistory,
                    onClearAllHistory = viewModel::clearAll, onFilterChange = {}, onThemeChange = viewModel::setTheme,
                    onToggleBraveShield = viewModel::toggleBraveShield, onTogglePlaylistDialog = {}, onToggleAllPlatforms = {},
                    onSelectPlatformHub = {}, onOpenBrowser = {}, onUpdateExtractors = viewModel::updateExtractors, onToast = viewModel::clearToast,
                    onNotificationsChange = viewModel::setNotifications, onTurboThreadsChange = viewModel::setTurboThreads,
                    onWifiOnlyChange = viewModel::setWifiOnly, onAutoWeeklyUpdateChange = viewModel::setAutoWeeklyUpdate
                )
            }
        }
    }
}
