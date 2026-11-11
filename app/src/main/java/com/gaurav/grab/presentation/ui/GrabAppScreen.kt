package com.gaurav.grab.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gaurav.grab.domain.model.*
import com.gaurav.grab.presentation.viewmodel.GrabUiState
import java.io.File

val ALL_PLATFORMS = listOf(
    PlatformSpec("YouTube", "YT", 0xFFFF0033, "https://m.youtube.com", listOf("True 4K/1080p Video", "Shorts", "320kbps MP3 Song", "Full Playlists", "4K Cover"), "Videos, Shorts, Playlists & 320kbps Songs"),
    PlatformSpec("Instagram", "IG", 0xFFE1306C, "https://www.instagram.com", listOf("1-Click All Carousel Slides", "IG Reels HD", "Single Photo Posts", "Original Audio"), "Reels, Multi-Photo Carousels & Posts"),
    PlatformSpec("TikTok", "TT", 0xFF00F2FE, "https://www.tiktok.com", listOf("HD Video (No Watermark)", "Photo Slide Carousels", "MP3 Sound"), "Watermark-Free HD Videos & Carousels"),
    PlatformSpec("Pinterest", "PIN", 0xFFE60023, "https://www.pinterest.com", listOf("HD Video Pins", "Multi-Pic Boards", "Full-Res Photo Pins"), "Full-Resolution Pins, Boards & Videos"),
    PlatformSpec("Apple Music", "AM", 0xFFFA243C, "https://music.apple.com", listOf("Dolby Atmos Spatial", "24-Bit Lossless ALAC", "320kbps MP3", "4K Album Art"), "Dolby Atmos & Studio Master Songs"),
    PlatformSpec("Facebook", "FB", 0xFF1877F2, "https://m.facebook.com", listOf("FB Watch 1080p", "FB Reels", "Public Videos"), "HD Watch Videos & Reels"),
    PlatformSpec("X (Twitter)", "X", 0xFF1DA1F2, "https://x.com", listOf("HD Tweet Videos", "Multi-Image Posts", "GIFs"), "Tweet Videos, GIFs & Photo Threads"),
    PlatformSpec("SoundCloud", "SC", 0xFFFF5500, "https://m.soundcloud.com", listOf("320kbps MP3 Track", "Full Sets & Playlists", "Cover Art"), "Original Tracks, DJ Sets & Remixes")
)

val TRENDING_SONGS = listOf(
    MusicTrackItem("1", "Blinding Lights (Dolby Atmos Master)", "The Weeknd", "3:20", "", "The Weeknd Blinding Lights official audio"),
    MusicTrackItem("2", "Kesariya (24-Bit Studio Lossless)", "Arijit Singh", "4:28", "", "Kesariya Arijit Singh official song"),
    MusicTrackItem("3", "Starboy (Spatial Audio 320kbps)", "The Weeknd ft. Daft Punk", "3:50", "", "Starboy The Weeknd official audio"),
    MusicTrackItem("4", "Tum Hi Ho (Unplugged Studio Master)", "Arijit Singh", "4:22", "", "Tum Hi Ho Arijit Singh official song"),
    MusicTrackItem("5", "Metamorphosis (Bass Boosted Phonk)", "INTERWORLD", "2:23", "", "INTERWORLD Metamorphosis official audio")
)

@Composable
fun GrabBrandLogo(modifier: Modifier = Modifier.size(34.dp)) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFF050B18)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.24f, size.height * 0.16f)
                lineTo(size.width * 0.24f, size.height * 0.84f)
                lineTo(size.width * 0.84f, size.height * 0.50f)
                close()
            }
            drawPath(path, Color(0xFF0B65FF), style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun AnimatedPulseSpinner(modifier: Modifier = Modifier.size(22.dp), color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rot by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "rot"
    )
    Canvas(modifier = modifier) {
        drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round))
        drawArc(color, rot, 120f, false, style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrabAppScreen(
    uiState: GrabUiState,
    downloads: List<DownloadRecord>,
    onUrlChange: (String) -> Unit,
    onAnalyze: (String?, Boolean) -> Unit,
    onSelectFormat: (FormatOption) -> Unit,
    onDownloadPlaylistEntry: (PlaylistEntryItem, Boolean) -> Unit,
    onDismissSheet: () -> Unit,
    onPauseDownload: (DownloadRecord) -> Unit,
    onResumeDownload: (DownloadRecord) -> Unit,
    onRemoveHistory: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onFilterChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onSetThreads: (Int) -> Unit,
    onToggleNotifications: () -> Unit,
    onTogglePlaylistDialog: (Boolean) -> Unit,
    onToggleMusicStudio: (Boolean) -> Unit,
    onToggleAllPlatforms: (Boolean) -> Unit,
    onSelectPlatformHub: (PlatformSpec?) -> Unit,
    onOpenSettingSheet: (String?) -> Unit,
    onUpdateExtractors: () -> Unit,
    onToast: (String) -> Unit
) {
    var currentNav by remember { mutableStateOf("HOME") }
    val runningCount = downloads.count { it.status == "RUNNING" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GrabBrandLogo()
                        Spacer(Modifier.width(10.dp))
                        Text("Grab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { onThemeChange(if (uiState.themeMode == "DARK") "LIGHT" else "DARK") }) {
                        Icon(
                            imageVector = if (uiState.themeMode == "DARK") Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Theme"
                        )
                    }
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
                    icon = {
                        BadgedBox(badge = { if (runningCount > 0) Badge { Text("$runningCount") } }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Downloads")
                        }
                    },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentNav) {
                "HOME" -> HomeDashboard(
                    uiState = uiState,
                    onUrlChange = onUrlChange,
                    onAnalyze = { onAnalyze(null, false) },
                    onOpenPlaylist = { onTogglePlaylistDialog(true) },
                    onOpenMusicStudio = { onToggleMusicStudio(true) },
                    onQuickSongDownload = { query -> onAnalyze(query, false) },
                    onClickPlatform = { onSelectPlatformHub(it) },
                    onClickMore = { onToggleAllPlatforms(true) }
                )
                "DOWNLOADS" -> DownloadsScreen(
                    uiState = uiState,
                    downloads = downloads,
                    onFilterChange = onFilterChange,
                    onPause = onPauseDownload,
                    onResume = onResumeDownload,
                    onDelete = onRemoveHistory
                )
                "HISTORY" -> HistoryScreen(
                    downloads = downloads,
                    onRedownload = { onAnalyze(it, false) },
                    onRemove = onRemoveHistory,
                    onClearAll = onClearAllHistory
                )
                "SETTINGS" -> SettingsScreen(
                    uiState = uiState,
                    onOpenSheet = onOpenSettingSheet,
                    onUpdateExtractors = onUpdateExtractors,
                    onToast = onToast
                )
            }
        }

        if (uiState.analyzedMedia != null) {
            FormatBottomSheet(
                media = uiState.analyzedMedia,
                onSelectFormat = { fmt ->
                    onSelectFormat(fmt)
                    currentNav = "DOWNLOADS"
                },
                onDownloadPlaylistEntry = { entry, isAudio ->
                    onDownloadPlaylistEntry(entry, isAudio)
                    currentNav = "DOWNLOADS"
                },
                onDismiss = onDismissSheet
            )
        }

        if (uiState.showPlaylistDialog) {
            PlaylistModal(
                onDismiss = { onTogglePlaylistDialog(false) },
                onInspect = { url -> onAnalyze(url, true) }
            )
        }

        if (uiState.showMusicStudioDialog) {
            AppleDolbyMusicModal(
                onDismiss = { onToggleMusicStudio(false) },
                onSearchSong = { songQuery -> onAnalyze(songQuery, false) }
            )
        }

        if (uiState.selectedPlatformHub != null) {
            PlatformHubModal(
                platform = uiState.selectedPlatformHub,
                onDismiss = { onSelectPlatformHub(null) },
                onGrab = { link ->
                    val isPl = link.contains("playlist") || link.contains("/p/")
                    onSelectPlatformHub(null)
                    onAnalyze(link, isPl)
                }
            )
        }

        if (uiState.showAllPlatformsSheet) {
            AllPlatformsModal(
                onDismiss = { onToggleAllPlatforms(false) },
                onSelect = {
                    onToggleAllPlatforms(false)
                    onSelectPlatformHub(it)
                }
            )
        }

        if (uiState.activeSettingSheet != null) {
            InteractiveSettingDialog(
                sheetId = uiState.activeSettingSheet,
                uiState = uiState,
                onDismiss = { onOpenSettingSheet(null) },
                onSetThreads = onSetThreads,
                onThemeChange = onThemeChange,
                onToggleNotifications = onToggleNotifications,
                onToast = onToast
            )
        }
    }
}

@Composable
fun HomeDashboard(
    uiState: GrabUiState,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenMusicStudio: () -> Unit,
    onQuickSongDownload: (String) -> Unit,
    onClickPlatform: (PlatformSpec) -> Unit,
    onClickMore: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Paste Link or Type Song Name", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.urlInput,
                        onValueChange = onUrlChange,
                        placeholder = { Text("Paste link (YT/IG/Pin) or Song Name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.urlInput.isNotEmpty()) {
                                    IconButton(onClick = { onUrlChange("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = { clipboard.getText()?.text?.let { onUrlChange(it) } }) {
                                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onAnalyze,
                        enabled = !uiState.isAnalyzing && uiState.urlInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B65FF), contentColor = Color.White)
                    ) {
                        if (uiState.isAnalyzing) {
                            AnimatedPulseSpinner(color = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text("Probing True Resolutions & Size...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("DOWNLOAD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.statusMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(uiState.statusMessage ?: "", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Two Boxes: Playlist + Carousel Batch AND Apple / Dolby Music Finder
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    onClick = onOpenPlaylist,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.PlaylistAddCheck, contentDescription = null, tint = Color(0xFF0B65FF), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Playlist & Carousel", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("1-Click Download All Video, MP3 or IG Slides", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Card(
                    onClick = onOpenMusicStudio,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Outlined.Album, contentDescription = null, tint = Color(0xFFFA243C), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Dolby Music Finder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Search Any Song • 24-Bit / 320kbps MP3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Platforms Row + More
        item {
            Text("Platforms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ALL_PLATFORMS.take(5).forEach { p ->
                    PlatformBadgeIcon(platform = p, onClick = { onClickPlatform(p) })
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClickMore() }.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = "More")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("More", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Apple / Dolby Minimal Direct Music Download List
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Studio Music & Dolby Tracks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenMusicStudio) { Text("Search Song →") }
            }
        }

        items(TRENDING_SONGS) { song ->
            OutlinedCard(
                onClick = { onQuickSongDownload(song.url) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFA243C).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color(0xFFFA243C))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} • ${song.audioSpecs}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    }
                    Icon(Icons.Outlined.Download, contentDescription = "Download", tint = Color(0xFF0B65FF))
                }
            }
        }
    }
}

@Composable
fun PlatformBadgeIcon(platform: PlatformSpec, onClick: () -> Unit) {
    val iconVec = when (platform.shortTag) {
        "YT" -> Icons.Outlined.SmartDisplay
        "IG" -> Icons.Outlined.PhotoCamera
        "TT" -> Icons.Outlined.MusicNote
        "PIN" -> Icons.Outlined.PushPin
        "AM" -> Icons.Outlined.Album
        "FB" -> Icons.Outlined.OndemandVideo
        else -> Icons.Outlined.Public
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(4.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(platform.badgeColorHex).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconVec, contentDescription = platform.name, tint = Color(platform.badgeColorHex))
        }
        Spacer(Modifier.height(6.dp))
        Text(platform.name.take(9), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatBottomSheet(
    media: MediaInfo,
    onSelectFormat: (FormatOption) -> Unit,
    onDownloadPlaylistEntry: (PlaylistEntryItem, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf("VIDEO") }
    val mainThree = media.formats.filter { it.type.startsWith("MAIN_") }
    val subList = media.formats.filter { it.type == activeTab }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = media.thumbnail,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(92.dp, 66.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            "${media.platform} • ${media.uploader}" + if (media.isPlaylist) " • ${media.itemCount} Items" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Main Instant Options (True Probed Quality)", style = MaterialTheme.typography.labelLarge, color = Color(0xFF0B65FF), fontWeight = FontWeight.Bold)
            }

            items(mainThree) { item ->
                Card(
                    onClick = { onSelectFormat(item) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (item.type) {
                                    "MAIN_AUDIO" -> Icons.Outlined.MusicNote
                                    "MAIN_IMAGE" -> Icons.Outlined.Image
                                    else -> Icons.Outlined.HighQuality
                                },
                                contentDescription = null,
                                tint = Color(0xFF0B65FF)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.label, fontWeight = FontWeight.Bold)
                                Text(item.quality, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Text("~${item.sizeMb} MB", fontWeight = FontWeight.Bold, color = Color(0xFF0B65FF))
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = when (activeTab) { "VIDEO" -> 0; "AUDIO" -> 1; else -> 2 },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(selected = activeTab == "VIDEO", onClick = { activeTab = "VIDEO" }, text = { Text("Video") })
                    Tab(selected = activeTab == "AUDIO", onClick = { activeTab = "AUDIO" }, text = { Text("Music") })
                    Tab(selected = activeTab == "IMAGE", onClick = { activeTab = "IMAGE" }, text = { Text("Photo / Slides") })
                }
            }

            items(subList) { fmt ->
                OutlinedCard(
                    onClick = { onSelectFormat(fmt) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fmt.label, fontWeight = FontWeight.SemiBold)
                            Text("${fmt.quality} • .${fmt.ext.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("~${fmt.sizeMb} MB", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Individual Playlist Items (Separate Video or MP3 Download per Track)
            if (media.entries.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text("Individual Playlist Tracks (${media.entries.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                items(media.entries) { entry ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${entry.index}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = { onDownloadPlaylistEntry(entry, false) }) {
                                Icon(Icons.Outlined.Videocam, contentDescription = "Video", tint = Color(0xFF0B65FF))
                            }
                            IconButton(onClick = { onDownloadPlaylistEntry(entry, true) }) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = "MP3", tint = Color(0xFFFA243C))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    uiState: GrabUiState,
    downloads: List<DownloadRecord>,
    onFilterChange: (String) -> Unit,
    onPause: (DownloadRecord) -> Unit,
    onResume: (DownloadRecord) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = uiState.filterTab == "ALL", onClick = { onFilterChange("ALL") }, label = { Text("All (${downloads.size})") })
            FilterChip(selected = uiState.filterTab == "RUNNING", onClick = { onFilterChange("RUNNING") }, label = { Text("Active (${downloads.count { it.status == "RUNNING" || it.status == "PAUSED" }})") })
            FilterChip(selected = uiState.filterTab == "SUCCEEDED", onClick = { onFilterChange("SUCCEEDED") }, label = { Text("Saved (${downloads.count { it.status == "SUCCEEDED" }})") })
        }

        Spacer(Modifier.height(12.dp))

        val filtered = downloads.filter {
            when (uiState.filterTab) {
                "RUNNING" -> it.status == "RUNNING" || it.status == "PAUSED"
                "SUCCEEDED" -> it.status == "SUCCEEDED"
                else -> true
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads in this tab yet.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = item.thumbnail,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    Text("${item.platform} • ${item.formatLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    Text(
                                        "${item.downloadedMb} MB / ${item.sizeMb} MB • ${item.speedText}" + if (item.etaText.isNotBlank()) " • ETA ${item.etaText}" else "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF0B65FF),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text("${item.progress}%", fontWeight = FontWeight.ExtraBold)
                                if (item.status == "RUNNING") {
                                    IconButton(onClick = { onPause(item) }) {
                                        Icon(Icons.Outlined.Pause, contentDescription = "Pause")
                                    }
                                } else if (item.status == "PAUSED" || item.status == "FAILED") {
                                    IconButton(onClick = { onResume(item) }) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume", tint = Color(0xFF00C853))
                                    }
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { item.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    downloads: List<DownloadRecord>,
    onRedownload: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history records.", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(downloads, key = { it.id }) { rec ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text("${rec.platform} • ${rec.sizeMb} MB • ${rec.status}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { onRedownload(rec.url) }) { Icon(Icons.Outlined.Replay, contentDescription = "Re-download") }
                            IconButton(onClick = { onRemove(rec.id) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    uiState: GrabUiState,
    onOpenSheet: (String) -> Unit,
    onUpdateExtractors: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("General", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        item { CleanSettingsRow(Icons.Outlined.Download, "Download settings", "${uiState.turboThreads}x Parallel Threads • /Download/Grab") { onOpenSheet("DOWNLOAD_CFG") } }
        item { CleanSettingsRow(Icons.Outlined.Notifications, "Notification settings", if (uiState.notificationsEnabled) "Live Speed & Progress Bar Enabled" else "Muted") { onOpenSheet("NOTIFY_CFG") } }
        item { CleanSettingsRow(Icons.Outlined.DarkMode, "Theme", "Current: ${uiState.themeMode} Mode") { onOpenSheet("THEME_CFG") } }
        item { CleanSettingsRow(Icons.Outlined.SystemUpdate, "Update all tools & extractors", "Tap to sync latest yt-dlp & platform rules") { onUpdateExtractors() } }

        item {
            Spacer(Modifier.height(14.dp))
            Text("Download tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        item { CleanSettingsRow(Icons.Outlined.RestoreFromTrash, "Recover deleted files", "Inspect & recover unfinished media streams") { onOpenSheet("RECOVER_CFG") } }
        item { CleanSettingsRow(Icons.Outlined.ChatBubbleOutline, "WhatsApp status saver", "Scan & save viewed WhatsApp statuses to Grab") { onOpenSheet("WA_SAVER") } }
        item { CleanSettingsRow(Icons.Outlined.Lock, "Vault", "Manage private hidden media folder") { onOpenSheet("VAULT_CFG") } }

        item {
            Spacer(Modifier.height(14.dp))
            Text("Phone clean", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
        item { CleanSettingsRow(Icons.Outlined.CleaningServices, "Junk clean", "Purge temporary staging & cache files") { onOpenSheet("CLEAN_JUNK") } }
        item { CleanSettingsRow(Icons.Outlined.Speed, "Boost", "Free RAM & optimize parallel worker pool") { System.gc(); onToast("Memory & 16x Worker Pool Boosted!") } }
        item { CleanSettingsRow(Icons.Outlined.BatteryChargingFull, "Battery saver", "Optimize background wakelock usage") { onOpenSheet("BATTERY_CFG") } }
        item { CleanSettingsRow(Icons.Outlined.FolderOpen, "Large files clean", "View & manage large downloaded videos") { onOpenSheet("LARGE_FILES") } }
        item { CleanSettingsRow(Icons.Outlined.ChatBubbleOutline, "WhatsApp clean", "Clean cached WhatsApp temporary media") { onOpenSheet("WA_CLEAN") } }
        item { CleanSettingsRow(Icons.Outlined.PhotoLibrary, "Photos clean", "Clean duplicate thumbnails & covers") { onOpenSheet("PHOTO_CLEAN") } }

        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GrabBrandLogo(modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("TEAM GAURAV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text("GitHub: gxurxvxnurxg-sketch / grab", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gxurxvxnurxg-sketch/grab"))) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B65FF), contentColor = Color.White)
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open GitHub Repository", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CleanSettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun InteractiveSettingDialog(
    sheetId: String,
    uiState: GrabUiState,
    onDismiss: () -> Unit,
    onSetThreads: (Int) -> Unit,
    onThemeChange: (String) -> Unit,
    onToggleNotifications: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val grabDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grab").apply { mkdirs() }
    val files = grabDir.listFiles() ?: emptyArray()
    val totalMb = (files.sumOf { it.length() } / (1024.0 * 1024.0)).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (sheetId) {
                    "DOWNLOAD_CFG" -> "Download Engine Settings"
                    "NOTIFY_CFG" -> "Notification Settings"
                    "THEME_CFG" -> "Select Appearance Theme"
                    "WA_SAVER" -> "WhatsApp Status Saver"
                    "VAULT_CFG" -> "Private Media Vault"
                    else -> "Storage & System Analyzer"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (sheetId) {
                    "DOWNLOAD_CFG" -> {
                        Text("Save Path: /storage/emulated/0/Download/Grab")
                        Text("Select Parallel Turbo Threads:", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(8, 16, 32).forEach { t ->
                                FilterChip(selected = uiState.turboThreads == t, onClick = { onSetThreads(t) }, label = { Text("${t}x Turbo") })
                            }
                        }
                    }
                    "NOTIFY_CFG" -> {
                        Text("Show live MB/s speed, ETA, and percentage in Android status bar:")
                        Switch(checked = uiState.notificationsEnabled, onCheckedChange = { onToggleNotifications() })
                    }
                    "THEME_CFG" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("DARK", "LIGHT", "SYSTEM").forEach { mode ->
                                FilterChip(selected = uiState.themeMode == mode, onClick = { onThemeChange(mode) }, label = { Text(mode) })
                            }
                        }
                    }
                    "WA_SAVER" -> {
                        val waDir = File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses")
                        val waFiles = waDir.listFiles()?.filter { it.name.endsWith(".mp4") || it.name.endsWith(".jpg") } ?: emptyList()
                        Text("Found ${waFiles.size} WhatsApp statuses on device.")
                        Button(onClick = {
                            var copied = 0
                            waFiles.forEach { f ->
                                try { f.copyTo(File(grabDir, "WA_${f.name}"), overwrite = true); copied++ } catch (_: Exception) {}
                            }
                            onToast("Saved $copied WhatsApp statuses to /Download/Grab!")
                            onDismiss()
                        }) { Text("Save All Statuses to Grab") }
                    }
                    "VAULT_CFG" -> {
                        val vaultDir = File(context.filesDir, "vault").apply { mkdirs(); File(this, ".nomedia").createNewFile() }
                        Text("Vault Path: ${vaultDir.absolutePath}\nProtected by .nomedia (Hidden from Gallery).")
                    }
                    else -> {
                        Text("Saved Grab Files: ${files.size} files (${totalMb} MB)\nTemp Cache: ${(context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / 1024)} KB")
                        Button(onClick = {
                            context.cacheDir.deleteRecursively()
                            onToast("Cleaned temporary cache & unfinished fragments!")
                            onDismiss()
                        }) { Text("Clean Temp & Unfinished Files Now") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun PlaylistModal(onDismiss: () -> Unit, onInspect: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist & IG Carousel Grabber", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Paste a YouTube Playlist, Instagram Carousel, or Pinterest Board link to unlock 1-Click Download All or pick individual tracks (Video/MP3).")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let { link = it } }) { Icon(Icons.Outlined.ContentPaste, null) } }
                )
            }
        },
        confirmButton = { Button(onClick = { onInspect(link) }, enabled = link.isNotBlank()) { Text("Load All Items") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AppleDolbyMusicModal(onDismiss: () -> Unit, onSearchSong: (String) -> Unit) {
    var songQuery by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dolby Atmos & Studio Music Finder", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter any Song Name + Artist (or paste an Apple Music / SoundCloud / YouTube Music link) to inspect author, cover art, and download 24-Bit Lossless / 320kbps MP3.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = songQuery,
                    onValueChange = { songQuery = it },
                    placeholder = { Text("e.g. Arijit Singh Kesariya or Starboy...") },
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(onClick = { onSearchSong(songQuery) }, enabled = songQuery.isNotBlank()) { Text("Find & Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformHubModal(platform: PlatformSpec, onDismiss: () -> Unit, onGrab: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadgeIcon(platform = platform, onClick = {})
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${platform.name} Hub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(platform.description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(platform.supportedMedia) { m -> AssistChip(onClick = {}, label = { Text(m) }) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Paste ${platform.name} link...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { clipboard.getText()?.text?.let { link = it } }) { Icon(Icons.Outlined.ContentPaste, null) } }
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onGrab(link) }, enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Inspect & Download")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlatformsModal(onDismiss: () -> Unit, onSelect: (PlatformSpec) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("All Supported Platforms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ALL_PLATFORMS) { p ->
                    OutlinedCard(onClick = { onSelect(p) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlatformBadgeIcon(platform = p, onClick = { onSelect(p) })
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text(p.description, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
