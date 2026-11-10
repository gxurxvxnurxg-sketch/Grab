package com.gaurav.grab.domain.usecase

import com.gaurav.grab.domain.model.*
import com.gaurav.grab.domain.repository.GrabRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AnalyzeUrlUseCase @Inject constructor(private val repository: GrabRepository) {
    suspend operator fun invoke(url: String, isPlaylist: Boolean = false): Result<MediaInfo, DownloadError> = if (url.trim().startsWith("http")) repository.analyzeUrl(url, isPlaylist) else Result.Error(DownloadError.EXTRACTION_FAILED, "Invalid link")
}
class EnqueueDownloadUseCase @Inject constructor(private val repository: GrabRepository) { suspend operator fun invoke(media: MediaInfo, format: FormatOption): String = repository.enqueueDownload(media, format) }
class ManageDownloadsUseCase @Inject constructor(private val repository: GrabRepository) {
    val downloadsFlow = repository.observeDownloads(); val settingsFlow: Flow<GrabSettings> = repository.observeSettings()
    suspend fun cancel(workId: String) = repository.cancelDownload(workId); suspend fun deleteRecord(id: Long) = repository.deleteDownloadRecord(id)
    suspend fun clearAll() = repository.clearAllHistory(); suspend fun updateEngine(): String = repository.updateExtractors()
    suspend fun saveSettings(transform: GrabSettings.() -> GrabSettings) = repository.saveSettings(transform)
}
