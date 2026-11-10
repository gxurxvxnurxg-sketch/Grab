package com.gaurav.grab.domain.model

data class FormatOption(
    val formatId: String,
    val label: String,
    val type: String, // VIDEO, AUDIO, IMAGE
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
    val status: String, // RUNNING, SUCCEEDED, FAILED, CANCELLED
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
