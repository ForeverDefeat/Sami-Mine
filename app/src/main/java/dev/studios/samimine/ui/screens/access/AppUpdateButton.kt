package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.google.firebase.appdistribution.UpdateStatus
import com.google.firebase.appdistribution.UpdateProgress
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppUpdateState(val busy: Boolean = false, val message: String? = null)

internal fun interface AppUpdateClient {
    fun start(progress: (UpdateProgress) -> Unit, success: () -> Unit, failure: (Exception) -> Unit, canceled: () -> Unit)
}

internal class AppUpdateViewModel(private val client: AppUpdateClient = AppUpdateClient { progress, success, failure, canceled ->
    FirebaseAppDistribution.getInstance().updateIfNewReleaseAvailable()
        .addOnProgressListener { progress(it) }
        .addOnSuccessListener { success() }
        .addOnFailureListener { failure(it) }
        .addOnCanceledListener { canceled() }
}) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()
    fun check() {
        if (state.value.busy) return
        mutableState.value = AppUpdateState(true, "Usa la cuenta de Google invitada al piloto. Es independiente de tu cuenta de SamiMine.")
        try {
            client.start(progress = { progress ->
                    val message = when (progress.updateStatus) {
                        UpdateStatus.DOWNLOADING -> if (progress.apkFileTotalBytes > 0)
                            "Descargando actualización: ${(100.0 * progress.apkBytesDownloaded / progress.apkFileTotalBytes).toInt().coerceIn(0, 100)} %"
                            else "Descargando actualización…"
                        UpdateStatus.DOWNLOADED -> "Descarga lista. Confirma la instalación en Android."
                        UpdateStatus.NEW_RELEASE_NOT_AVAILABLE -> "Ya tienes la última versión disponible para tu cuenta."
                        else -> "Sigue las indicaciones de Firebase y Android para actualizar."
                    }
                    mutableState.value = AppUpdateState(true, message)
                }, success = { mutableState.value = state.value.copy(busy = false) },
                failure = ::fail,
                canceled = { mutableState.value = AppUpdateState(message = "Actualización cancelada. Puedes volver a intentarlo.") })
        } catch (failure: Exception) { fail(failure) }
    }
    private fun fail(failure: Exception) {
        val code = (failure as? FirebaseAppDistributionException)?.errorCode
        val message = when (code) {
            FirebaseAppDistributionException.Status.NOT_IMPLEMENTED -> "Las actualizaciones están disponibles en la versión piloto firmada; esta compilación debug no las incluye."
            FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE -> "Ya tienes la última versión disponible para tu cuenta."
            FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED,
            FirebaseAppDistributionException.Status.INSTALLATION_CANCELED -> "Actualización cancelada. Puedes volver a intentarlo."
            FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE -> "Ingresa con la cuenta de Google que recibió la invitación y acepta primero el acceso al piloto."
            FirebaseAppDistributionException.Status.NETWORK_FAILURE -> "No hay conexión con Firebase. Revisa Internet y vuelve a intentarlo."
            FirebaseAppDistributionException.Status.API_DISABLED -> "El responsable del piloto debe habilitar Firebase App Testers API."
            else -> "No se pudo completar la actualización. Revisa Internet y permite instalar desde SamiMine cuando Android lo solicite. Vuelve a intentarlo."
        }
        mutableState.value = AppUpdateState(message = message)
    }
}

/** Manual pilot updates: Firebase handles tester authorization, download and Android installer. */
@Composable
fun AppUpdateButton() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    val model: AppUpdateViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = model::check, enabled = !state.busy) { Text(if (state.busy) "Buscando actualización…" else "Buscar actualizaciones · v$version") }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}
