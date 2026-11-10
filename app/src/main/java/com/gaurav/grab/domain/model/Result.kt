package com.gaurav.grab.domain.model

sealed interface RootError

enum class DownloadError : RootError {
    AUTH_REQUIRED,
    NETWORK_ERROR,
    EXTRACTION_FAILED,
    STORAGE_ERROR
}

sealed interface Result<out D, out E : RootError> {
    data class Success<out D, out E : RootError>(val data: D) : Result<D, E>
    data class Error<out D, out E : RootError>(val error: E, val details: String = "") : Result<D, E>
}
