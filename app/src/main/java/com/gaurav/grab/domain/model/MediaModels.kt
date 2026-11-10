package com.gaurav.grab.domain.model

data class FormatOption(
    val formatId: String,
    val label: String,
    val quality: String = "",
    val type: String,
    val ext: String,
    val sizeMb: Double
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
    val formats: List<FormatOption>
)

data class DownloadRecord(
    val id: Long = 0,
    val workId: String,
    val url: String,
    val title: String,
    val platform: String,
    val thumbnail: String,
    val formatLabel: String,
    val sizeMb: Double,
    val progress: Int,
    val status: String,
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
