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
import com.gaurav.grab.domain.model.Result
import com.gaurav.grab.domain.repository.GrabRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrabRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao
) : GrabRepository {

    private val cookiesFile: File
        get() = File(context.filesDir, "cookies.txt")

    override suspend fun analyzeUrl(url: String): Result<MediaInfo, DownloadError> =
        withContext(Dispatchers.IO) {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                val py = Python.getInstance()
                val mod = py.getModule("downloader")
                val rawJson = mod.callAttr("extract_media_info", url, cookiesFile.absolutePath).toString()
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
                                type = f.getString("type"),
                                ext = f.getString("ext"),
                                sizeMb = f.getDouble("sizeMb")
                            )
                        )
                    }
                    Result.Success(
                        MediaInfo(
                            url = url,
                            title = obj.optString("title", "Media"),
                            uploader = obj.optString("uploader", "Creator"),
                            platform = obj.optString("platform", "Web"),
                            thumbnail = obj.optString("thumbnail", ""),
                            durationSec = obj.optInt("duration", 0),
                            formats = formats
                        )
                    )
                } else {
                    val code = obj.optString("code")
                    val err = if (code == "AUTH_REQUIRED") DownloadError.AUTH_REQUIRED else DownloadError.EXTRACTION_FAILED
                    Result.Error(err, obj.optString("message", "Extraction failed"))
                }
            } catch (e: Exception) {
                Result.Error(DownloadError.NETWORK_ERROR, e.localizedMessage ?: "Unknown error")
            }
        }

    override suspend fun enqueueDownload(media: MediaInfo, format: FormatOption): String {
        val req = OneTimeWorkRequestBuilder<GrabDownloadWorker>()
            .setInputData(
                workDataOf(
                    "url" to media.url,
                    "title" to media.title,
                    "formatId" to format.formatId,
                    "mediaType" to format.type,
                    "cookiesPath" to cookiesFile.absolutePath
                )
            )
            .build()

        val workId = req.id.toString()
        dao.insert(
            DownloadEntity(
                workId = workId,
                url = media.url,
                title = media.title,
                platform = media.platform,
                thumbnail = media.thumbnail,
                formatLabel = format.label,
                sizeMb = format.sizeMb,
                progress = 0,
                status = "RUNNING",
                filePath = "",
                createdAt = System.currentTimeMillis()
            )
        )
        WorkManager.getInstance(context).enqueue(req)
        return workId
    }

    override suspend fun cancelDownload(workId: String) {
        WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(workId))
        dao.updateState(workId, 0, "CANCELLED")
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
                    formatLabel = it.formatLabel,
                    sizeMb = it.sizeMb,
                    progress = it.progress,
                    status = it.status,
                    filePath = it.filePath,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun saveCookies(netscapeCookies: String) = withContext(Dispatchers.IO) {
        cookiesFile.writeText(netscapeCookies)
    }

    override fun hasValidCookies(): Boolean = cookiesFile.exists() && cookiesFile.length() > 20
}
