package com.gaurav.grab.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gaurav.grab.data.local.DownloadDao
import com.gaurav.grab.data.local.GrabDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

@Keep
class PythonProgressBridge(
    private val workId: String,
    private val totalSizeMb: Double,
    private val dao: DownloadDao,
    private val onUiUpdate: (Int, String, String) -> Unit
) {
    @Keep
    fun onProgress(pct: Int, speed: String, eta: String) {
        val safePct = pct.coerceIn(1, 100)
        val dlMb = ((totalSizeMb * safePct) / 100.0 * 10.0).roundToInt() / 10.0
        onUiUpdate(safePct, speed, eta)
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateLiveProgress(workId, safePct, dlMb, speed, eta, "RUNNING")
        }
    }
}

class GrabDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Grab Media"
        val formatId = inputData.getString("formatId") ?: "best"
        val mediaType = inputData.getString("mediaType") ?: "VIDEO"
        val totalSizeMb = inputData.getDouble("sizeMb", 15.0)

        val db = Room.databaseBuilder(appContext, GrabDatabase::class.java, "grab.db")
            .fallbackToDestructiveMigration()
            .build()
        val dao = db.downloadDao()

        try {
            setForeground(createForegroundInfo(title, 2, "Starting...", "--:--"))
        } catch (_: Exception) {}
        dao.updateLiveProgress(id.toString(), 2, 0.2, "Starting...", "--:--", "RUNNING")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        val py = Python.getInstance()
        val module = py.getModule("downloader")

        val sandboxDir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "staging"
        ).apply { mkdirs() }

        val bridge = PythonProgressBridge(id.toString(), totalSizeMb, dao) { pct, speed, eta ->
            setProgressAsync(workDataOf("progress" to pct, "speed" to speed, "eta" to eta))
            try {
                notificationManager.notify(id.hashCode(), createForegroundInfo(title, pct, speed, eta).notification)
            } catch (_: Exception) {}
        }

        val targetUrl = if (!url.startsWith("http")) "ytsearch1:$url official audio" else url

        val rawResult = module.callAttr(
            "download_media",
            targetUrl,
            formatId,
            mediaType,
            sandboxDir.absolutePath,
            "",
            PyObject.fromJava(bridge)
        ).toString()

        val json = JSONObject(rawResult)
        return if (json.optString("status") == "SUCCESS") {
            val stagedPath = json.optString("filePath")
            val finalPublicPath = exportToPublicDownloads(File(stagedPath), title, mediaType)
            dao.updateLiveProgress(id.toString(), 100, totalSizeMb, "Saved", "00:00", "SUCCEEDED", finalPublicPath)
            showCompleteNotification(title, finalPublicPath, true)
            Result.success(workDataOf("filePath" to finalPublicPath))
        } else {
            if (isStopped) {
                dao.updateLiveProgress(id.toString(), 0, 0.0, "Paused", "--:--", "PAUSED")
            } else {
                dao.updateLiveProgress(id.toString(), 0, 0.0, "Failed", "--:--", "FAILED")
                showCompleteNotification(title, "", false)
            }
            Result.failure()
        }
    }

    private fun exportToPublicDownloads(stagedFile: File, rawTitle: String, mediaType: String): String {
        if (!stagedFile.exists()) return ""
        val cleanName = rawTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "").take(60).ifBlank { "Grab_${System.currentTimeMillis()}" }
        val ext = when {
            "AUDIO" in mediaType -> "mp3"
            "IMAGE" in mediaType -> "jpg"
            else -> stagedFile.extension.ifBlank { "mp4" }
        }
        val fileName = "$cleanName.$ext"
        val mime = when (ext) {
            "mp3", "m4a", "wav" -> "audio/mpeg"
            "jpg", "png" -> "image/jpeg"
            else -> "video/mp4"
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Grab")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(stagedFile).use { inStream ->
                            inStream.copyTo(outStream, 262144)
                        }
                    }
                    stagedFile.delete()
                    return "/storage/emulated/0/Download/Grab/$fileName"
                }
            }
            val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grab").apply { mkdirs() }
            val destFile = File(pubDir, fileName)
            stagedFile.copyTo(destFile, overwrite = true)
            stagedFile.delete()
            MediaScannerConnection.scanFile(appContext, arrayOf(destFile.absolutePath), null, null)
            destFile.absolutePath
        } catch (_: Exception) {
            stagedFile.absolutePath
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, speed: String, eta: String): ForegroundInfo {
        val channelId = "grab_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Grab Live Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val cancelIntent = WorkManager.getInstance(appContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setContentText("$progress% • $speed • ETA $eta")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_media_pause, "Pause", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun showCompleteNotification(title: String, savedPath: String, success: Boolean) {
        try {
            val notification = NotificationCompat.Builder(appContext, "grab_downloads")
                .setContentTitle(if (success) "Saved to /Download/Grab" else "Download Interrupted")
                .setContentText(if (success) "$title ($savedPath)" else title)
                .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(id.hashCode() + 1, notification)
        } catch (_: Exception) {}
    }
}
