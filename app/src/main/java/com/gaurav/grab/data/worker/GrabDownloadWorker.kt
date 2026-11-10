package com.gaurav.grab.data.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.Keep
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.gaurav.grab.MainActivity
import com.gaurav.grab.R
import com.gaurav.grab.data.local.DownloadDao
import com.gaurav.grab.data.local.GrabSettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicLong

@Keep
class PythonProgressBridge(private val workId: String, private val dao: DownloadDao, private val scope: CoroutineScope, private val onUiUpdate: (Int, Double, Int) -> Unit) {
    private val lastWrite = AtomicLong(0L)
    @Keep fun onProgress(pct: Int) = onProgressEx(pct, 0L, 0L, 0.0)
    @Keep fun onProgressEx(pct: Int, downloaded: Long, total: Long, speedBps: Double) {
        val kb = speedBps / 1024.0
        val eta = if (speedBps > 1024 && total > downloaded) ((total - downloaded) / speedBps).toInt() else -1
        onUiUpdate(pct, kb, eta)
        val now = System.currentTimeMillis()
        if (now - lastWrite.get() < 320 && pct < 100) return
        lastWrite.set(now)
        scope.launch { dao.updateState(workId, pct, "RUNNING", speedKbps = kb, etaSec = eta) }
    }
    @Keep fun isCancelled(): Boolean = false
}

@Keep @HiltWorker
class GrabDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context, @Assisted params: WorkerParameters,
    private val dao: DownloadDao, private val db: RoomDatabase, private val settingsStore: GrabSettingsStore
) : CoroutineWorker(appContext, params) {
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val workerScope = CoroutineScope(Dispatchers.IO + Job())
    private var smoothSpeed = 0.0

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Grab Media"
        val formatId = inputData.getString("formatId") ?: "best"
        val mediaType = inputData.getString("mediaType") ?: "VIDEO"
        val session = id.toString()

        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        val module = Python.getInstance().getModule("downloader")

        try { setForeground(createForegroundInfo(title, 3)) } catch (_: Exception) {}
        dao.updateState(session, 3, "RUNNING")

        val sandboxDir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir, "staging").apply { mkdirs() }
        val bridge = PythonProgressBridge(session, dao, workerScope) { pct, kb, eta ->
            setProgressAsync(workDataOf("progress" to pct, "speedKbps" to kb, "etaSec" to eta))
            if (kb > 0) smoothSpeed = if (smoothSpeed <= 0) kb else smoothSpeed * 0.65 + kb * 0.35
            try { nm.notify(4201, createForegroundInfo(title, pct, smoothSpeed, eta).notification) } catch (_: Exception) {}
        }

        val raw = try {
            module.callAttr("download_media", url, formatId, mediaType, sandboxDir.absolutePath, "", PyObject.fromJava(bridge)).toString()
        } catch (e: Exception) { """{"status":"ERROR","message":"${e.localizedMessage?.replace("\"", "'") ?: "engine crash"}"}""" }

        val json = JSONObject(raw)
        return if (json.optString("status") == "SUCCESS") {
            val staged = File(json.optString("filePath"))
            val finalPath = exportToPublicDownloads(staged, title, json.optString("ext", "mp4"))
            dao.updateState(session, 100, "SUCCEEDED", finalPath, speedKbps = 0.0, etaSec = 0)
            nm.cancel(4201)
            showCompleteNotification(title, true, finalPath)
            Result.success(workDataOf("filePath" to finalPath))
        } else {
            dao.updateState(session, 0, "FAILED")
            nm.cancel(4201)
            showCompleteNotification(title, false, "")
            Result.failure()
        }
    }

    private fun createForegroundInfo(title: String, pct: Int, kb: Double = 0.0, eta: Int = -1): ForegroundInfo {
        val cancel = WorkManager.getInstance(appContext).createCancelPendingIntent(id)
        val speed = if (kb >= 1024) String.format("%.1f MB/s", kb/1024) else if (kb > 0) String.format("%.0f KB/s", kb) else "calculating…"
        val etaStr = if (eta > 0) " • ${eta/60}m ${eta%60}s left" else ""
        val n = NotificationCompat.Builder(appContext, "grab_downloads")
            .setContentTitle(title.take(60)).setContentText("$pct% • $speed$etaStr")
            .setSmallIcon(R.drawable.ic_stat_grab).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setProgress(100, pct, pct <= 0).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(4201, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(4201, n)
    }

    private fun showCompleteNotification(title: String, success: Boolean, path: String) {
        try {
            val tap = PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java).apply { putExtra("open_path", path) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(4202, NotificationCompat.Builder(appContext, "grab_done")
                .setContentTitle(if (success) "Download Complete" else "Download Failed").setContentText(title.take(70))
                .setSmallIcon(if (success) R.drawable.ic_stat_grab_done else R.drawable.ic_stat_grab)
                .setContentIntent(tap).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
        } catch (_: Exception) {}
    }

    private fun exportToPublicDownloads(stagedFile: File, rawTitle: String, ext: String): String {
        if (!stagedFile.exists() || stagedFile.length() == 0L) return ""
        val base = rawTitle.replace(Regex("[^a-zA-Z0-9._ \\-\\u0900-\\u097F]"), "").trim().take(70).ifBlank { "Grab_${System.currentTimeMillis()}" }
        val fileName = "$base.$ext"
        val mime = when (ext.lowercase()) { "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; "jpg", "png" -> "image/jpeg"; else -> "video/mp4" }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Grab"); put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (mime.startsWith("image")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else if (mime.startsWith("audio")) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values) ?: return stagedFile.absolutePath
                resolver.openOutputStream(uri)?.use { out -> FileInputStream(stagedFile).use { ins -> ins.copyTo(out, 262144) } }
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); resolver.update(uri, values, null, null)
                stagedFile.delete(); "/storage/emulated/0/Download/Grab/$fileName"
            } else {
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Grab").apply { mkdirs() }
                val dest = File(pubDir, fileName); stagedFile.copyTo(dest, overwrite = true); stagedFile.delete()
                MediaScannerConnection.scanFile(appContext, arrayOf(dest.absolutePath), arrayOf(mime), null); dest.absolutePath
            }
        } catch (_: Exception) { stagedFile.absolutePath }
    }
}
