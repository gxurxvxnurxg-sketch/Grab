package com.gaurav.grab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            GrabTheme {
                val viewModel: GrabViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val downloads by viewModel.downloads.collectAsStateWithLifecycle()

                LaunchedEffect(sharedUrl) {
                    if (!sharedUrl.isNullOrBlank()) {
                        viewModel.onUrlChanged(sharedUrl)
                        viewModel.analyzeCurrentUrl()
                    }
                }

                GrabAppScreen(
                    uiState = uiState,
                    downloads = downloads,
                    onUrlChange = viewModel::onUrlChanged,
                    onAnalyze = viewModel::analyzeCurrentUrl,
                    onSelectFormat = viewModel::startDownload,
                    onDismissSheet = viewModel::dismissSheet,
                    onCancelDownload = viewModel::cancelDownload,
                    onFilterChange = viewModel::onFilterChanged,
                    onSaveCookies = viewModel::saveCookies
                )
            }
        }
    }
}
