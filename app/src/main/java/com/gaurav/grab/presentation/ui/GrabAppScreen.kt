package com.gaurav.grab.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.presentation.viewmodel.GrabUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabAppScreen(
    uiState: GrabUiState, downloads: List<DownloadRecord>,
    onUrlChange: (String) -> Unit, onAnalyze: () -> Unit, onSelectFormat: (FormatOption) -> Unit,
    onDismissSheet: () -> Unit, onCancelDownload: (String) -> Unit, onRemoveHistory: (Long) -> Unit,
    onClearAllHistory: () -> Unit, onFilterChange: (String) -> Unit, onThemeChange: (String) -> Unit,
    onToggleBraveShield: () -> Unit, onTogglePlaylistDialog: (Boolean) -> Unit, onToggleAllPlatforms: (Boolean) -> Unit,
    onSelectPlatformHub: (Any?) -> Unit, onOpenBrowser: (String?) -> Unit, onUpdateExtractors: () -> Unit, onToast: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit, onTurboThreadsChange: (Int) -> Unit, onWifiOnlyChange: (Boolean) -> Unit,
    onAutoWeeklyUpdateChange: (Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Grab", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(value = uiState.urlInput, onValueChange = onUrlChange, modifier = Modifier.fillMaxWidth(), label = { Text("Paste video/audio link") }, singleLine = true, trailingIcon = { if (uiState.urlInput.isNotEmpty()) IconButton(onClick = { onUrlChange("") }) { Icon(Icons.Filled.Clear, "Clear") } })
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val c = clipboard.getText()?.text ?: ""; if (c.startsWith("http")) onUrlChange(c) }, modifier = Modifier.weight(1f)) { Text("Paste") }
                Button(onClick = onAnalyze, modifier = Modifier.weight(1f), enabled = uiState.urlInput.isNotBlank() && !uiState.isAnalyzing) { Text("Analyze") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (downloads.isEmpty()) Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Text("No downloads yet.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                items(downloads, key = { it.id }) { record -> GrabLiveDownloadCard(record = record, onCancel = onCancelDownload, onDelete = onRemoveHistory) }
            }
        }
    }

    if (uiState.isAnalyzing) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(0.7f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

    if (uiState.analyzedMedia != null) {
        ModalBottomSheet(onDismissRequest = onDismissSheet) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Format", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(uiState.analyzedMedia!!.title, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn { items(uiState.analyzedMedia!!.formats) { f -> ListItem(headlineContent = { Text(f.label) }, supportingContent = { Text("${f.quality} • ${f.sizeMb} MB") }, modifier = Modifier.clickable { onSelectFormat(f); onDismissSheet() }); HorizontalDivider() } }
            }
        }
    }

    GrabSettingsSheet(visible = showSettings, settings = uiState.settings, notifGranted = uiState.hasNotifPermission,
        onDismiss = { showSettings = false }, onTheme = onThemeChange, onNotifications = onNotificationsChange,
        onTurboThreads = onTurboThreadsChange, onWifiOnly = onWifiOnlyChange, onAutoUpdate = onAutoWeeklyUpdateChange,
        onAdShield = onToggleBraveShield, onUpdateExtractors = onUpdateExtractors)

    uiState.statusMessage?.let { msg ->
        LaunchedEffect(msg) { delay(3000); onToast() }
        Snackbar(modifier = Modifier.padding(16.dp), action = { TextButton(onClick = onToast) { Text("Dismiss") } }) { Text(msg) }
    }
}
