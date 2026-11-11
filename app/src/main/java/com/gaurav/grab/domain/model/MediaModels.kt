package com.gaurav.grab.domain.model

data class FormatOption(
    val formatId: String,
    val label: String,
    val quality: String = "",
    val type: String, // MAIN_VIDEO, MAIN_AUDIO, MAIN_IMAGE, VIDEO, AUDIO, IMAGE, BATCH_ALL
    val ext: String,
    val sizeMb: Double
)

data class PlaylistEntryItem(
    val index: Int,
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String,
    val durationSec: Int,
    val isVideo: Boolean = true
)

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val platform: String,
    val thumbnail: String,
    val durationSec: Int,
    val isPlaylist: Boolean = false,
    val itemCount: Int = 1,
    val formats: List<FormatOption>,
    val entries: List<PlaylistEntryItem> = emptyList()
)

data class MusicTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnail: String,
    val url: String,
    val audioSpecs: String = "Dolby Atmos • 24-Bit Lossless / 320kbps MP3"
)

data class DownloadRecord(
    val id: Long = 0,
    val workId: String,
    val url: String,
    val title: String,
    val platform: String,
    val thumbnail: String,
    val formatId: String,
    val formatLabel: String,
    val mediaType: String,
    val sizeMb: Double,
    val downloadedMb: Double = 0.0,
    val speedText: String = "",
    val etaText: String = "",
    val progress: Int,
    val status: String, // RUNNING, PAUSED, SUCCEEDED, FAILED
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PlatformSpec(
    val name: String,
    val shortTag: String,
    val badgeColorHex: Long,
    val homeUrl: String,
    val supportedMedia: List<String>,
    val description: String
)
