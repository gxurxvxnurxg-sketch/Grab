package com.gaurav.grab.domain.repository

import com.gaurav.grab.domain.model.DownloadError
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
import com.gaurav.grab.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface GrabRepository {
    suspend fun analyzeUrl(url: String): Result<MediaInfo, DownloadError>
    suspend fun enqueueDownload(media: MediaInfo, format: FormatOption): String
    suspend fun cancelDownload(workId: String)
    fun observeDownloads(): Flow<List<DownloadRecord>>
    suspend fun saveCookies(netscapeCookies: String)
    fun hasValidCookies(): Boolean
}
