package com.gaurav.grab.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.grab.domain.model.DownloadError
import com.gaurav.grab.domain.model.DownloadRecord
import com.gaurav.grab.domain.model.FormatOption
import com.gaurav.grab.domain.model.MediaInfo
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
    val errorBanner: String? = null,
    val requiresLogin: Boolean = false,
    val hasValidCookies: Boolean = false,
    val filterTab: String = "ALL" // ALL, RUNNING, SUCCEEDED
)

@HiltViewModel
class GrabViewModel @Inject constructor(
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val enqueueDownloadUseCase: EnqueueDownloadUseCase,
    private val manageDownloadsUseCase: ManageDownloadsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GrabUiState(hasValidCookies = manageDownloadsUseCase.hasCookies()))
    val uiState: StateFlow<GrabUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<List<DownloadRecord>> = manageDownloadsUseCase.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlChanged(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl, errorBanner = null, requiresLogin = false) }
    }

    fun onFilterChanged(tab: String) {
        _uiState.update { it.copy(filterTab = tab) }
    }

    fun analyzeCurrentUrl() {
        val target = _uiState.value.urlInput
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, errorBanner = null, analyzedMedia = null) }
            when (val res = analyzeUrlUseCase(target)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isAnalyzing = false, analyzedMedia = res.data) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            requiresLogin = res.error == DownloadError.AUTH_REQUIRED,
                            errorBanner = if (res.error == DownloadError.AUTH_REQUIRED)
                                "Authentication required. Import cookies.txt or sign in via Settings."
                            else res.details
                        )
                    }
                }
            }
        }
    }

    fun startDownload(format: FormatOption) {
        val media = _uiState.value.analyzedMedia ?: return
        viewModelScope.launch {
            enqueueDownloadUseCase(media, format)
            _uiState.update { it.copy(analyzedMedia = null, urlInput = "") }
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(analyzedMedia = null) }
    }

    fun cancelDownload(workId: String) {
        viewModelScope.launch {
            manageDownloadsUseCase.cancel(workId)
        }
    }

    fun saveCookies(netscapeContent: String) {
        viewModelScope.launch {
            manageDownloadsUseCase.saveCookies(netscapeContent)
            _uiState.update { it.copy(hasValidCookies = manageDownloadsUseCase.hasCookies(), requiresLogin = false) }
        }
    }
}
