package com.gaurav.grab.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gaurav.grab.data.local.*
import com.gaurav.grab.data.worker.GrabDownloadWorker
import com.gaurav.grab.domain.model.*
import com.gaurav.grab.domain.repository.GrabRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrabRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context, private val dao: DownloadDao, private val db: androidx.room.RoomDatabase, private val settingsStore: GrabSettingsStore) : GrabRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { runCatching { dao.markOrphansFailed() } } }
    private fun python() = synchronized(this) { if (!Python.isStarted()) Python.start(AndroidPlatform(context)); Python.getInstance() }

    override suspend fun analyzeUrl(url: String, isPlaylist: Boolean): Result<MediaInfo, DownloadError> = withContext(Dispatchers.IO) {
        try {
            val rawJson = python().getModule("downloader").callAttr("extract_media_info", url, "", isPlaylist).toString()
            val obj = JSONObject(rawJson)
            if (obj.optString("status") != "SUCCESS") return@withContext Result.Error(DownloadError.EXTRACTION_FAILED, obj.optString("message", "Unable to inspect link."))
            val arr = obj.getJSONArray("formats")
            val formats = ArrayList<FormatOption>(arr.length())
            for (i in 0 until arr.length()) { val f = arr.getJSONObject(i); formats += FormatOption(f.getString("formatId"), f.getString("label"), f.optString("quality", ""), f.getString("type"), f.optString("ext", "mp4"), f.optDouble("sizeMb", 0.0)) }
            Result.Success(MediaInfo(obj.optString("url", url), obj.optString("title", "Media"), obj.optString("uploader", "Creator"), obj.optString("platform", "Web"), obj.optString("thumbnail", ""), obj.optInt("duration", 0), obj.optBoolean("isPlaylist", false), obj.optInt("itemCount", 1), formats))
        } catch (e: Exception) { Result.Error(DownloadError.NETWORK_ERROR, e.localizedMessage ?: "Network timeout") }
    }

    override suspend fun enqueueDownload(media: MediaInfo, format: FormatOption): String {
        val req = OneTimeWorkRequestBuilder<GrabDownloadWorker>().setInputData(workDataOf("url" to media.url, "title" to media.title, "formatId" to format.formatId, "mediaType" to format.type)).build()
        val workId = req.id.toString()
        dao.insert(DownloadEntity(workId = workId, url = media.url, title = media.title, platform = media.platform, thumbnail = media.thumbnail, formatLabel = format.label, sizeMb = format.sizeMb, progress = 1, status = "PENDING", filePath = "", createdAt = System.currentTimeMillis()))
        WorkManager.getInstance(context).enqueue(req); return workId
    }

    override suspend fun cancelDownload(workId: String) { runCatching { python().getModule("downloader").callAttr("set_cancelled", workId, true) }; WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(workId)); dao.updateState(workId, 0, "CANCELLED") }
    override suspend fun deleteDownloadRecord(id: Long) = dao.deleteById(id)
    override suspend fun clearAllHistory() = dao.clearAll()
    override fun observeDownloads(): Flow<List<DownloadRecord>> = dao.observeAll().map { list -> list.map { DownloadRecord(it.id, it.workId, it.url, it.title, it.platform, it.thumbnail, it.formatLabel, it.sizeMb, it.progress, it.status, it.filePath, it.createdAt, it.speedKbps, it.etaSec) } }
    override fun observeSettings(): Flow<GrabSettings> = settingsStore.settings
    override suspend fun saveSettings(transform: GrabSettings.() -> GrabSettings) = settingsStore.update(transform)
    override suspend fun updateExtractors(): String = withContext(Dispatchers.IO) { try { JSONObject(python().getModule("downloader").callAttr("update_extractors_info").toString()).optString("status", "Synced") } catch (e: Exception) { "Update failed" } }
}
