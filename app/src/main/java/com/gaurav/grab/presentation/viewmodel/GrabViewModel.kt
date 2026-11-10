package com.gaurav.grab.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.grab.domain.model.*
import com.gaurav.grab.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GrabUiState(val urlInput: String = "", val isAnalyzing: Boolean = false, val analyzedMedia: MediaInfo? = null, val statusMessage: String? = null, val settings: GrabSettings = GrabSettings(), val hasNotifPermission: Boolean = false)

@HiltViewModel
class GrabViewModel @Inject constructor(private val analyzeUrlUseCase: AnalyzeUrlUseCase, private val enqueueDownloadUseCase: EnqueueDownloadUseCase, private val manageDownloadsUseCase: ManageDownloadsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(GrabUiState()); val uiState: StateFlow<GrabUiState> = _uiState.asStateFlow()
    val downloads: StateFlow<List<DownloadRecord>> = manageDownloadsUseCase.downloadsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    init { viewModelScope.launch { manageDownloadsUseCase.settingsFlow.collect { s -> _uiState.update { it.copy(settings = s) } } } }

    fun onUrlChanged(u: String) = _uiState.update { it.copy(urlInput = u) }
    fun analyzeUrl() {
        if (_uiState.value.urlInput.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, analyzedMedia = null) }
            when (val res = analyzeUrlUseCase(_uiState.value.urlInput)) {
                is Result.Success -> _uiState.update { it.copy(isAnalyzing = false, analyzedMedia = res.data) }
                is Result.Error -> _uiState.update { it.copy(isAnalyzing = false, statusMessage = res.details) }
            }
        }
    }
    fun startDownload(format: FormatOption) {
        val media = _uiState.value.analyzedMedia ?: return
        viewModelScope.launch { enqueueDownloadUseCase(media, format); _uiState.update { it.copy(analyzedMedia = null, urlInput = "", statusMessage = "Started: ${media.title}") } }
    }
    fun dismissSheet() = _uiState.update { it.copy(analyzedMedia = null) }
    fun cancelDownload(w: String) = viewModelScope.launch { manageDownloadsUseCase.cancel(w) }
    fun removeHistory(id: Long) = viewModelScope.launch { manageDownloadsUseCase.deleteRecord(id) }
    fun clearAll() = viewModelScope.launch { manageDownloadsUseCase.clearAll() }
    fun updateExtractors() = viewModelScope.launch { _uiState.update { it.copy(statusMessage = manageDownloadsUseCase.updateEngine()) } }
    fun clearToast() = _uiState.update { it.copy(statusMessage = null) }
    fun setNotifPerm(g: Boolean) = _uiState.update { it.copy(hasNotifPermission = g) }

    fun setTheme(m: String) = persist { copy(themeMode = m) }
    fun setNotifications(on: Boolean) = persist { copy(notificationsEnabled = on) }
    fun setTurboThreads(n: Int) = persist { copy(turboThreads = n) }
    fun setWifiOnly(on: Boolean) = persist { copy(wifiOnly = on) }
    fun setAutoWeeklyUpdate(on: Boolean) = persist { copy(autoWeeklyUpdate = on) }
    fun toggleBraveShield() = persist { copy(adShield = !adShield) }
    
    private fun persist(transform: GrabSettings.() -> GrabSettings) { viewModelScope.launch { runCatching { manageDownloadsUseCase.saveSettings(transform) } } }
}
