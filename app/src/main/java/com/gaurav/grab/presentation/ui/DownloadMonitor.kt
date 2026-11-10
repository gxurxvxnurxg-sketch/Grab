package com.gaurav.grab.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gaurav.grab.domain.model.DownloadRecord
import java.util.Locale

@Composable
fun GrabLiveDownloadCard(record: DownloadRecord, onCancel: (String) -> Unit, onDelete: (Long) -> Unit, modifier: Modifier = Modifier) {
    val tint = when(record.status) { "SUCCEEDED" -> Color(0xFF4CAF7D); "FAILED" -> Color(0xFFE05B5B); "CANCELLED" -> Color(0xFF9AA0A6); else -> MaterialTheme.colorScheme.primary }
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(tint)); Spacer(Modifier.width(8.dp))
                Text(record.platform.uppercase(), style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(record.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Spacer(Modifier.height(6.dp))
            Text(record.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = tint, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${record.progress}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
                Spacer(Modifier.width(10.dp))
                if (record.status == "RUNNING") {
                    val speed = if (record.speedKbps >= 1024) String.format(Locale.US, "%.1f MB/s", record.speedKbps/1024) else "${record.speedKbps.toInt()} KB/s"
                    val eta = if (record.etaSec > 0) " • ${record.etaSec/60}m ${record.etaSec%60}s" else ""
                    Text("$speed$eta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                }
                Spacer(Modifier.weight(1f))
                if (record.status == "RUNNING" || record.status == "PENDING") IconButton(onClick = { onCancel(record.workId) }, Modifier.size(34.dp)) { Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurface.copy(0.7f)) }
                else IconButton(onClick = { onDelete(record.id) }, Modifier.size(34.dp)) { Icon(Icons.Filled.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)) }
            }
        }
    }
}
