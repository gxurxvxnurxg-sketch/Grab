package com.gaurav.grab.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
import com.gaurav.grab.domain.model.PlatformSpec
import com.gaurav.grab.domain.model.PlaylistEntryItem
import com.gaurav.grab.domain.model.Result
import com.gaurav.grab.domain.usecase.AnalyzeUrlUseCase
import com.gaurav.grab.domain.usecase.EnqueueDownloadUseCase
import com.gaurav.grab.domain.usecase.ManageDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GrabUiState(
    val urlInput: String = "",
    val isAnalyzing: Boolean = false,
    val analyzedMedia: MediaInfo? = null,
    val statusMessage: String? = null,
    val filterTab: String = "ALL",
    val themeMode: String = "DARK",
    val turboThreads: Int = 16,
    val notificationsEnabled: Boolean = true,
    val autoWeeklyUpdate: Boolean = true,
    val selectedPlatformHub: PlatformSpec? = null,
    val showPlaylistDialog: Boolean = false,
    val showMusicStudioDialog: Boolean = false,
    val showAllPlatformsSheet: Boolean = false,
    val activeSettingSheet: String? = null // DOWNLOAD_CFG, NOTIFY_CFG, STATUS_SAVER, VAULT, CLEANER
)

@HiltViewModel
class GrabViewModel @Inject constructor(
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val enqueueDownloadUseCase: EnqueueDownloadUseCase,
    private val manageDownloadsUseCase: ManageDownloadsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GrabUiState())
    val uiState: StateFlow<GrabUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<List<DownloadRecord>> = manageDownloadsUseCase.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, statusMessage = null) }
    }

    fun onFilterChanged(tab: String) {
        _uiState.update { it.copy(filterTab = tab) }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setTurboThreads(threads: Int) {
        _uiState.update { it.copy(turboThreads = threads, statusMessage = "Parallel engine set to ${threads}x threads") }
    }

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
    }

    fun togglePlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showPlaylistDialog = show) }
    }

    fun toggleMusicStudioDialog(show: Boolean) {
        _uiState.update { it.copy(showMusicStudioDialog = show) }
    }

    fun toggleAllPlatformsSheet(show: Boolean) {
        _uiState.update { it.copy(showAllPlatformsSheet = show) }
    }

    fun selectPlatformHub(platform: PlatformSpec?) {
        _uiState.update { it.copy(selectedPlatformHub = platform) }
    }

    fun openSettingSheet(sheetId: String?) {
        _uiState.update { it.copy(activeSettingSheet = sheetId) }
    }

    fun analyzeUrl(customUrl: String? = null, isPlaylist: Boolean = false) {
        val target = (customUrl ?: _uiState.value.urlInput).trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    urlInput = target,
                    isAnalyzing = true,
                    statusMessage = null,
                    analyzedMedia = null,
                    showPlaylistDialog = false,
                    showMusicStudioDialog = false
                )
            }
            when (val res = analyzeUrlUseCase(target, isPlaylist)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isAnalyzing = false, analyzedMedia = res.data) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isAnalyzing = false, statusMessage = res.details) }
                }
            }
        }
    }

    fun startDownload(format: FormatOption) {
        val media = _uiState.value.analyzedMedia ?: return
        viewModelScope.launch {
            enqueueDownloadUseCase(media, format)
            _uiState.update {
                it.copy(
                    analyzedMedia = null,
                    urlInput = "",
                    statusMessage = "Downloading: ${media.title}"
                )
            }
        }
    }

    fun downloadSinglePlaylistEntry(entry: PlaylistEntryItem, asAudio: Boolean) {
        val parent = _uiState.value.analyzedMedia ?: return
        val singleMedia = parent.copy(
            url = entry.url,
            title = entry.title,
            thumbnail = entry.thumbnail.ifBlank { parent.thumbnail },
            isPlaylist = false
        )
        val fmt = if (asAudio) {
            FormatOption("bestaudio/best", "MP3 Studio Master", "320kbps MP3", "AUDIO", "mp3", 5.5)
        } else {
            FormatOption("best[ext=mp4]/best", "1080p Full HD Video", "1080p MP4", "VIDEO", "mp4", 16.0)
        }
        viewModelScope.launch {
            enqueueDownloadUseCase(singleMedia, fmt)
            _uiState.update { it.copy(statusMessage = "Queued: ${entry.title}") }
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(analyzedMedia = null) }
    }

    fun pauseDownload(record: DownloadRecord) {
        viewModelScope.launch { manageDownloadsUseCase.pause(record) }
    }

    fun resumeDownload(record: DownloadRecord) {
        viewModelScope.launch { manageDownloadsUseCase.resume(record) }
    }

    fun removeHistoryItem(id: Long) {
        viewModelScope.launch { manageDownloadsUseCase.deleteRecord(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { manageDownloadsUseCase.clearAll() }
    }

    fun triggerExtractorUpdate() {
        viewModelScope.launch {
            val msg = manageDownloadsUseCase.updateEngine()
            _uiState.update { it.copy(statusMessage = msg) }
        }
    }

    fun showToastMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }
}
