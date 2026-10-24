package dev.studios.samimine.app

import com.google.firebase.appdistribution.*
import dev.studios.samimine.ui.screens.access.*
import org.junit.Assert.*
import org.junit.Test

class AppUpdateViewModelTest {
    private class Client : AppUpdateClient {
        var calls = 0
        lateinit var progress: (UpdateProgress) -> Unit
        lateinit var success: () -> Unit
        lateinit var failure: (Exception) -> Unit
        lateinit var canceled: () -> Unit
        override fun start(progress: (UpdateProgress) -> Unit, success: () -> Unit, failure: (Exception) -> Unit, canceled: () -> Unit) {
            calls++; this.progress = progress; this.success = success; this.failure = failure; this.canceled = canceled
        }
    }
    private fun progress(status: UpdateStatus, downloaded: Long = 0, total: Long = 0) = object : UpdateProgress {
        override fun getUpdateStatus() = status
        override fun getApkBytesDownloaded() = downloaded
        override fun getApkFileTotalBytes() = total
    }
    @Test fun concurrentClicksDoNotStartTwoDownloadsAndCancelAllowsRetry() {
        val client = Client(); val model = AppUpdateViewModel(client)
        model.check(); model.check()
        assertEquals(1, client.calls); assertTrue(model.state.value.busy)
        client.canceled(); assertFalse(model.state.value.busy)
        model.check(); assertEquals(2, client.calls)
    }
    @Test fun unknownSizeDownloadAndInstallerConfirmationHaveHonestStatus() {
        val client = Client(); val model = AppUpdateViewModel(client); model.check()
        client.progress(progress(UpdateStatus.DOWNLOADING))
        assertEquals("Descargando actualización…", model.state.value.message)
        client.progress(progress(UpdateStatus.DOWNLOADING, 25, 100))
        assertTrue(model.state.value.message!!.contains("25 %"))
        client.progress(progress(UpdateStatus.DOWNLOADED)); client.success()
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.message!!.contains("Confirma la instalación"))
    }
    @Test fun noReleaseDoesNotClaimThatAnInstallationHappened() {
        val client = Client(); val model = AppUpdateViewModel(client); model.check()
        client.progress(progress(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE)); client.success()
        assertTrue(model.state.value.message!!.contains("última versión")); assertFalse(model.state.value.busy)
    }
    @Test fun failedUpdateCanRetryWithoutExposingErrorDetails() {
        val client = Client(); val model = AppUpdateViewModel(client); model.check()
        client.failure(IllegalStateException("private detail"))
        assertFalse(model.state.value.busy); assertTrue(model.state.value.message!!.contains("Internet"))
        assertFalse(model.state.value.message!!.contains("private detail"))
        model.check(); assertEquals(2, client.calls)
    }
}
