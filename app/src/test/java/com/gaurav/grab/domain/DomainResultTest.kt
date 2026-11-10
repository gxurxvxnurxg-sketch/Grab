package com.gaurav.grab.domain

import com.gaurav.grab.domain.model.DownloadError
import com.gaurav.grab.domain.model.Result
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainResultTest {
    @Test
    fun sealedResultSuccessAndErrorBehaveCorrectly() {
        val ok: Result<String, DownloadError> = Result.Success("GrabReady")
        val fail: Result<String, DownloadError> = Result.Error(DownloadError.AUTH_REQUIRED, "Need cookies")
        assertTrue(ok is Result.Success)
        assertTrue(fail is Result.Error)
    }
}
