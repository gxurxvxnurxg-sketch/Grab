package com.gaurav.grab.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaurav.grab.domain.model.GrabSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabSettingsSheet(visible: Boolean, settings: GrabSettings, notifGranted: Boolean, onDismiss: () -> Unit, onTheme: (String) -> Unit, onNotifications: (Boolean) -> Unit, onTurboThreads: (Int) -> Unit, onWifiOnly: (Boolean) -> Unit, onAutoUpdate: (Boolean) -> Unit, onAdShield: () -> Unit, onUpdateExtractors: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Theme"); Row(Modifier.fillMaxWidth()) {
                listOf("LIGHT" to "Light", "DARK" to "Dark", "SYSTEM" to "System").forEach { (k, l) ->
                    TextButton(onClick = { onTheme(k) }, Modifier.weight(1f)) { Text(l, color = if (settings.themeMode == k) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.6f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Download Notifications"); Text(if (notifGranted) "Show live %" else "Permission denied", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }; Switch(settings.notificationsEnabled, onNotifications) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Wi-Fi Only"); Text("Pause on mobile data", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }; Switch(settings.wifiOnly, onWifiOnly) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Auto Update Extractors"); Text("Keeps engines working", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }; Switch(settings.autoWeeklyUpdate, onAutoUpdate) }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Ad Shield"); Text("Block ads in browser", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }; Switch(settings.adShield, { onAdShield() }) }
            Button(onClick = onUpdateExtractors, Modifier.fillMaxWidth()) { Text("Update Extractors Now") }
        }
    }
}
