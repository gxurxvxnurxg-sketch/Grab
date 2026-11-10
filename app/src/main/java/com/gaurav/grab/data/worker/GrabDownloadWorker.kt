package com.gaurav.grab.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gaurav.grab.data.local.GrabDatabase
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class GrabDownloadWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Downloading media..."
        val formatId = inputData.getString("formatId") ?: "best"
        val mediaType = inputData.getString("mediaType") ?: "VIDEO"
        val cookiesPath = inputData.getString("cookiesPath") ?: ""

        val db = Room.databaseBuilder(appContext, GrabDatabase::class.java, "grab.db").build()
        val dao = db.downloadDao()

        setForeground(createForegroundInfo(title, 0))
        dao.updateState(id.toString(), 1, "RUNNING")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(appContext))
        }
        val py = Python.getInstance()
        val module = py.getModule("downloader")

        val outDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Grab"
        ).apply { mkdirs() }

        val callback = PyObject.fromJava(object {
            @Suppress("unused")
            fun invoke(pct: Int) {
                setProgressAsync(workDataOf("progress" to pct))
                notificationManager.notify(id.hashCode(), createForegroundInfo(title, pct).notification)
                CoroutineScope(Dispatchers.IO).launch {
                    dao.updateState(id.toString(), pct, "RUNNING")
                }
            }
        })

        val rawResult = module.callAttr(
            "download_media",
            url,
            formatId,
            mediaType,
            outDir.absolutePath,
            cookiesPath,
            callback.get("invoke")
        ).toString()

        val json = JSONObject(rawResult)
        return if (json.optString("status") == "SUCCESS") {
            val filePath = json.optString("filePath")
            dao.updateState(id.toString(), 100, "SUCCEEDED", filePath)
            showCompleteNotification(title, true)
            Result.success(workDataOf("filePath" to filePath))
        } else {
            dao.updateState(id.toString(), 0, "FAILED")
            showCompleteNotification(title, false)
            Result.failure()
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

        // PendingIntent to cancel the associated WorkRequest directly from Notification
        val cancelIntent = WorkManager.getInstance(appContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("Downloading: $title")
            .setContentText("$progress% completed")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun showCompleteNotification(title: String, success: Boolean) {
        val notification = NotificationCompat.Builder(appContext, "grab_downloads")
            .setContentTitle(if (success) "Download Complete" else "Download Failed")
            .setContentText(title)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id.hashCode() + 1, notification)
    }
}
