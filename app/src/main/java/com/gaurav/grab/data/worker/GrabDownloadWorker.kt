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

@Keep
class PythonProgressBridge(
    private val workId: String,
    private val dao: DownloadDao,
    private val onUiUpdate: (Int) -> Unit
) {
    @Keep
    fun onProgress(pct: Int) {
        val safePct = pct.coerceIn(1, 100)
        onUiUpdate(safePct)
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateState(workId, safePct, "RUNNING")
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

        val db = Room.databaseBuilder(appContext, GrabDatabase::class.java, "grab.db").build()
        val dao = db.downloadDao()

        try {
            setForeground(createForegroundInfo(title, 5))
        } catch (_: Exception) {}
        dao.updateState(id.toString(), 5, "RUNNING")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        val py = Python.getInstance()
        val module = py.getModule("downloader")

        // Unrestricted App Sandbox Directory (Never fails on Android 13/14 Scoped Storage)
        val sandboxDir = File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir,
            "staging"
        ).apply { mkdirs() }

        val bridge = PythonProgressBridge(id.toString(), dao) { pct ->
            setProgressAsync(workDataOf("progress" to pct))
            try {
                notificationManager.notify(id.hashCode(), createForegroundInfo(title, pct).notification)
            } catch (_: Exception) {}
        }

        val rawResult = module.callAttr(
            "download_media",
            url,
            formatId,
            mediaType,
            sandboxDir.absolutePath,
            "",
            PyObject.fromJava(bridge)
        ).toString()

        val json = JSONObject(rawResult)
        return if (json.optString("status") == "SUCCESS") {
            val stagedPath = json.optString("filePath")
            val stagedFile = File(stagedPath)
            val finalPublicPath = exportToPublicDownloads(stagedFile, title, mediaType)
            dao.updateState(id.toString(), 100, "SUCCEEDED", finalPublicPath)
            showCompleteNotification(title, true)
            Result.success(workDataOf("filePath" to finalPublicPath))
        } else {
            dao.updateState(id.toString(), 0, "FAILED")
            showCompleteNotification(title, false)
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

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val channelId = "grab_downloads"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Grab Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val cancelIntent = WorkManager.getInstance(appContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setContentText("Downloading • $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun showCompleteNotification(title: String, success: Boolean) {
        try {
            val notification = NotificationCompat.Builder(appContext, "grab_downloads")
                .setContentTitle(if (success) "Download Complete • Saved to Grab" else "Download Failed")
                .setContentText(title)
                .setSmallIcon(
                    if (success) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(id.hashCode() + 1, notification)
        } catch (_: Exception) {}
    }
}
