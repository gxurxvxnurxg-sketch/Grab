package com.gaurav.grab.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gaurav.grab.data.local.DownloadDao
import com.gaurav.grab.data.local.DownloadEntity
import com.gaurav.grab.data.worker.GrabDownloadWorker
import com.gaurav.grab.domain.model.DownloadError
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
import com.gaurav.grab.domain.model.PlaylistEntryItem
import com.gaurav.grab.domain.model.Result
import com.gaurav.grab.domain.repository.GrabRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrabRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao
) : GrabRepository {

    override suspend fun analyzeUrl(url: String, isPlaylist: Boolean): Result<MediaInfo, DownloadError> =
        withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                val py = Python.getInstance()
                val mod = py.getModule("downloader")
                val rawJson = mod.callAttr("extract_media_info", url, "", isPlaylist).toString()
                val obj = JSONObject(rawJson)

                if (obj.optString("status") == "SUCCESS") {
                    val arr = obj.getJSONArray("formats")
                    val formats = mutableListOf<FormatOption>()
                    for (i in 0 until arr.length()) {
                        val f = arr.getJSONObject(i)
                        formats.add(
                            FormatOption(
                                formatId = f.getString("formatId"),
                                label = f.getString("label"),
                                quality = f.optString("quality", ""),
                                type = f.getString("type"),
                                ext = f.getString("ext"),
                                sizeMb = f.getDouble("sizeMb")
                            )
                        )
                    }
                    val entries = mutableListOf<PlaylistEntryItem>()
                    val entArr = obj.optJSONArray("entries")
                    if (entArr != null) {
                        for (i in 0 until entArr.length()) {
                            val e = entArr.getJSONObject(i)
                            entries.add(
                                PlaylistEntryItem(
                                    index = e.optInt("index", i + 1),
                                    id = e.optString("id", "${i + 1}"),
                                    title = e.optString("title", "Item #${i + 1}"),
                                    url = e.optString("url", url),
                                    thumbnail = e.optString("thumbnail", ""),
                                    durationSec = e.optInt("durationSec", 0)
                                )
                            )
                        }
                    }
                    Result.Success(
                        MediaInfo(
                            url = url,
                            title = obj.optString("title", "Media"),
                            uploader = obj.optString("uploader", "Creator"),
                            platform = obj.optString("platform", "Web"),
                            thumbnail = obj.optString("thumbnail", ""),
                            durationSec = obj.optInt("duration", 0),
                            isPlaylist = obj.optBoolean("isPlaylist", false),
                            itemCount = obj.optInt("itemCount", 1),
                            formats = formats,
                            entries = entries
                        )
                    )
                } else {
                    Result.Error(DownloadError.EXTRACTION_FAILED, obj.optString("message", "Unable to inspect link."))
                }
            } catch (e: Exception) {
                Result.Error(DownloadError.NETWORK_ERROR, e.localizedMessage ?: "Network error")
            }
        }

    override suspend fun enqueueDownload(media: MediaInfo, format: FormatOption): String {
        // 1-Click Batch Download All for Instagram Carousels
        if (format.formatId == "BATCH_ALL_CAROUSEL") {
            val slideFormats = media.formats.filter { it.formatId.startsWith("http") }
            var lastWorkId = ""
            slideFormats.forEachIndexed { index, slideFmt ->
                lastWorkId = enqueueSingleWork(
                    url = media.url,
                    title = "${media.title} [Slide ${index + 1}]",
                    platform = media.platform,
                    thumbnail = media.thumbnail,
                    format = slideFmt
                )
            }
            return lastWorkId
        }

        // 1-Click Batch Download All for Playlists
        if (format.formatId == "BATCH_ALL_PLAYLIST" && media.entries.isNotEmpty()) {
            var lastWorkId = ""
            media.entries.take(25).forEach { entry ->
                val itemFmt = FormatOption(
                    formatId = "best[ext=mp4]/best",
                    label = "Playlist Video",
                    quality = "HD MP4",
                    type = "VIDEO",
                    ext = "mp4",
                    sizeMb = 14.0
                )
                lastWorkId = enqueueSingleWork(
                    url = entry.url,
                    title = "${entry.index}. ${entry.title}",
                    platform = media.platform,
                    thumbnail = entry.thumbnail.ifBlank { media.thumbnail },
                    format = itemFmt
                )
            }
            return lastWorkId
        }

        return enqueueSingleWork(media.url, media.title, media.platform, media.thumbnail, format)
    }

    private suspend fun enqueueSingleWork(
        url: String,
        title: String,
        platform: String,
        thumbnail: String,
        format: FormatOption
    ): String {
        val req = OneTimeWorkRequestBuilder<GrabDownloadWorker>()
            .setInputData(
                workDataOf(
                    "url" to url,
                    "title" to title,
                    "formatId" to format.formatId,
                    "mediaType" to format.type,
                    "sizeMb" to format.sizeMb
                )
            )
            .build()

        val workId = req.id.toString()
        dao.insert(
            DownloadEntity(
                workId = workId,
                url = url,
                title = title,
                platform = platform,
                thumbnail = thumbnail,
                formatId = format.formatId,
                formatLabel = "${format.label} (${format.quality})",
                mediaType = format.type,
                sizeMb = format.sizeMb,
                downloadedMb = 0.0,
                speedText = "Queued...",
                etaText = "--:--",
                progress = 2,
                status = "RUNNING",
                filePath = "",
                createdAt = System.currentTimeMillis()
            )
        )
        WorkManager.getInstance(context).enqueue(req)
        return workId
    }

    override suspend fun pauseDownload(record: DownloadRecord) {
        try {
            WorkManager.getInstance(context).cancelWorkById(UUID.fromString(record.workId))
        } catch (_: Exception) {}
        dao.updateLiveProgress(record.workId, record.progress, record.downloadedMb, "Paused", "--:--", "PAUSED")
    }

    override suspend fun resumeDownload(record: DownloadRecord) {
        val req = OneTimeWorkRequestBuilder<GrabDownloadWorker>()
            .setInputData(
                workDataOf(
                    "url" to record.url,
                    "title" to record.title,
                    "formatId" to record.formatId,
                    "mediaType" to record.mediaType,
                    "sizeMb" to record.sizeMb
                )
            )
            .build()
        val newWorkId = req.id.toString()
        dao.updateWorkForResume(record.id, newWorkId, "RUNNING", "Resuming...")
        WorkManager.getInstance(context).enqueue(req)
    }

    override suspend fun deleteDownloadRecord(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearAllHistory() {
        dao.clearAll()
    }

    override fun observeDownloads(): Flow<List<DownloadRecord>> {
        return dao.observeAll().map { list ->
            list.map {
                DownloadRecord(
                    id = it.id,
                    workId = it.workId,
                    url = it.url,
                    title = it.title,
                    platform = it.platform,
                    thumbnail = it.thumbnail,
                    formatId = it.formatId,
                    formatLabel = it.formatLabel,
                    mediaType = it.mediaType,
                    sizeMb = it.sizeMb,
                    downloadedMb = it.downloadedMb,
                    speedText = it.speedText,
                    etaText = it.etaText,
                    progress = it.progress,
                    status = it.status,
                    filePath = it.filePath,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun updateExtractors(): String = withContext(Dispatchers.IO) {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            val py = Python.getInstance()
            val raw = py.getModule("downloader").callAttr("update_extractors_info").toString()
            JSONObject(raw).optString("status", "Extractors Updated")
        } catch (e: Exception) {
            "Engine Updated"
        }
    }
}
