package com.gaurav.grab.domain.usecase

import com.gaurav.grab.domain.model.DownloadError
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
import com.gaurav.grab.domain.model.Result
import com.gaurav.grab.domain.repository.GrabRepository
import javax.inject.Inject

class AnalyzeUrlUseCase @Inject constructor(
    private val repository: GrabRepository
) {
    suspend operator fun invoke(url: String): Result<MediaInfo, DownloadError> {
        if (url.isBlank() || !url.startsWith("http")) {
            return Result.Error(DownloadError.EXTRACTION_FAILED, "Please paste a valid http/https link.")
        }
        return repository.analyzeUrl(url.trim())
    }
}

class EnqueueDownloadUseCase @Inject constructor(
    private val repository: GrabRepository
) {
    suspend operator fun invoke(media: MediaInfo, format: FormatOption): String {
        return repository.enqueueDownload(media, format)
    }
}

class ManageDownloadsUseCase @Inject constructor(
    private val repository: GrabRepository
) {
    val downloadsFlow = repository.observeDownloads()
    suspend fun cancel(workId: String) = repository.cancelDownload(workId)
    suspend fun saveCookies(content: String) = repository.saveCookies(content)
    fun hasCookies(): Boolean = repository.hasValidCookies()
}
