package com.gaurav.grab.presentation.ui

import android.annotation.SuppressLint
import android.os.Environment
import android.os.StatFs
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
import com.gaurav.grab.presentation.viewmodel.GrabUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabAppScreen(
    uiState: GrabUiState,
    downloads: List<DownloadRecord>,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onSelectFormat: (FormatOption) -> Unit,
    onDismissSheet: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onSaveCookies: (String) -> Unit
) {
    var currentNav by remember { mutableStateOf("HOME") }
    var showLoginWebView by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Grab", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { currentNav = "SETTINGS" },
                        label = { Text(if (uiState.hasValidCookies) "Cookies Active" else "No Cookies") }
                    )
                    Spacer(Modifier.width(12.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentNav == "HOME",
                    onClick = { currentNav = "HOME" },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentNav == "DOWNLOADS",
                    onClick = { currentNav = "DOWNLOADS" },
                    icon = { Icon(Icons.Outlined.Download, contentDescription = "Downloads") },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = currentNav == "HISTORY",
                    onClick = { currentNav = "HISTORY" },
                    icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = currentNav == "SETTINGS",
                    onClick = { currentNav = "SETTINGS" },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            if (currentNav == "SETTINGS") {
                SettingsAndCookiesSection(
                    hasCookies = uiState.hasValidCookies,
                    showWebView = showLoginWebView,
                    onToggleWebView = { showLoginWebView = it },
                    onSaveCookies = onSaveCookies
                )
            } else {
                // Step 1: Minimalist Paste Link Card (Seal + VidGrab Layout)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Paste Video or Social Link", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = onUrlChange,
                            placeholder = { Text("https://youtube.com/... or instagram.com/...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let { onUrlChange(it) }
                                }) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "IG • TikTok • YouTube • FB • X",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Button(
                                onClick = onAnalyze,
                                enabled = !uiState.isAnalyzing && uiState.urlInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyzing...")
                                } else {
                                    Text("DOWNLOAD")
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.errorBanner != null, enter = fadeIn(), exit = fadeOut()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = uiState.errorBanner ?: "",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Downloads Header + Filter Chips + Storage Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.filterTab == "ALL",
                            onClick = { onFilterChange("ALL") },
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = uiState.filterTab == "RUNNING",
                            onClick = { onFilterChange("RUNNING") },
                            label = { Text("Downloading") }
                        )
                        FilterChip(
                            selected = uiState.filterTab == "SUCCEEDED",
                            onClick = { onFilterChange("SUCCEEDED") },
                            label = { Text("Completed") }
                        )
                    }
                    StorageSummaryBadge(activeCount = downloads.count { it.status == "RUNNING" })
                }

                Spacer(Modifier.height(12.dp))

                val filteredList = downloads.filter {
                    when (uiState.filterTab) {
                        "RUNNING" -> it.status == "RUNNING"
                        "SUCCEEDED" -> it.status == "SUCCEEDED"
                        else -> true
                    }
                }

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Paste a link above to inspect formats and start downloading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { it.workId }) { item ->
                            DownloadRowItem(item = item, onCancel = { onCancelDownload(item.workId) })
                        }
                    }
                }
            }
        }

        // Step 2 & 3: Contextual SnapTube/Seal Bottom Sheet for Format & Resolution Selection
        if (uiState.analyzedMedia != null) {
            FormatSelectionBottomSheet(
                media = uiState.analyzedMedia,
                onSelectFormat = onSelectFormat,
                onDismiss = onDismissSheet
            )
        }
    }
}

@Composable
fun StorageSummaryBadge(activeCount: Int) {
    val stat = remember { StatFs(Environment.getDataDirectory().path) }
    val freeGb = remember { (stat.availableBytes / (1024.0 * 1024.0 * 1024.0)).toInt() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "${freeGb}GB Free • $activeCount active",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun DownloadRowItem(item: DownloadRecord, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp, 46.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${item.platform} • ${item.formatLabel} • ${item.sizeMb} MB",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("${item.progress}%", style = MaterialTheme.typography.labelLarge)
            if (item.status == "RUNNING") {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Pause, contentDescription = "Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionBottomSheet(
    media: MediaInfo,
    onSelectFormat: (FormatOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = media.thumbnail,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp, 68.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = media.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${media.platform} • ${media.uploader}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Select Format & Quality", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))

            media.formats.forEach { fmt ->
                OutlinedCard(
                    onClick = { onSelectFormat(fmt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(fmt.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(fmt.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("~${fmt.sizeMb} MB", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SettingsAndCookiesSection(
    hasCookies: Boolean,
    showWebView: Boolean,
    onToggleWebView: (Boolean) -> Unit,
    onSaveCookies: (String) -> Unit
) {
    var manualCookies by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Anti-Bot & Session Authentication", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import Netscape cookies.txt or sign in directly via the integrated browser to unlock age-restricted or private Instagram/YouTube media.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onToggleWebView(!showWebView) }) {
                Text(if (showWebView) "Close Login Browser" else "Open Integrated Login Browser")
            }
        }

        if (showWebView) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val rawCookie = CookieManager.getInstance().getCookie(url ?: "") ?: ""
                                    if (rawCookie.contains("sessionid") || rawCookie.contains("SAPISID")) {
                                        val netscape = buildString {
                                            appendLine("# Netscape HTTP Cookie File")
                                            rawCookie.split(";").forEach { pair ->
                                                val parts = pair.trim().split("=", limit = 2)
                                                if (parts.size == 2) {
                                                    appendLine(".instagram.com\tTRUE\t/\tTRUE\t2147483647\t${parts[0]}\t${parts[1]}")
                                                    appendLine(".youtube.com\tTRUE\t/\tTRUE\t2147483647\t${parts[0]}\t${parts[1]}")
                                                }
                                            }
                                        }
                                        onSaveCookies(netscape)
                                    }
                                }
                            }
                            loadUrl("https://www.instagram.com/accounts/login/")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = manualCookies,
                onValueChange = { manualCookies = it },
                label = { Text("Paste Netscape cookies.txt content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onSaveCookies(manualCookies) },
                enabled = manualCookies.isNotBlank()
            ) {
                Text("Save cookies.txt to Secure App Storage")
            }
        }
    }
}
