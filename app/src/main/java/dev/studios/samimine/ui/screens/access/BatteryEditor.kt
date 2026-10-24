package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.studios.samimine.data.remote.BatteryDraft
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.screens.cognitive.moduleTitle
import dev.studios.samimine.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BatteryEditorState(val draft: BatteryDraft? = null, val baseline: BatteryDraft? = null, val busy: Boolean = false,
    val error: String? = null, val message: String? = null) {
    val dirty get() = draft != baseline
    val valid get() = draft?.let { it.name.trim().length in 1..100 && it.modules.size in 1..8 && it.modules.distinct().size == it.modules.size } == true
    val publishable get() = valid && !dirty && (draft?.revision ?: 0) > 0 && draft!!.modules.all { id -> BatteryCatalog.modules().any { it.id == id && it.available } }
}

interface BatteryEditorActions {
    fun loadIfNeeded()
    fun change(value: BatteryDraft)
    fun save()
    fun publish()
    fun reload()
    fun discard()
    object None : BatteryEditorActions {
        override fun loadIfNeeded() = Unit
        override fun change(value: BatteryDraft) = Unit
        override fun save() = Unit
        override fun publish() = Unit
        override fun reload() = Unit
        override fun discard() = Unit
    }
}

class BatteryEditorController(private val scope: CoroutineScope, private val loadDraft: suspend () -> BatteryDraft,
    private val saveDraft: suspend (BatteryDraft) -> BatteryDraft, private val publishDraft: suspend (BatteryDraft) -> BatteryDefinition) : BatteryEditorActions {
    private val mutable = MutableStateFlow(BatteryEditorState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var disposed = false
    override fun loadIfNeeded() { if (state.value.draft == null) reload() }
    override fun change(value: BatteryDraft) { if (!state.value.busy && !disposed) mutable.update { it.copy(draft = value, error = null, message = null) } }
    override fun discard() { if (!state.value.busy) mutable.update { it.copy(draft = it.baseline, error = null, message = null) } }
    private fun perform(action: suspend () -> Unit) {
        if (disposed || state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = "No se pudo completar la operación. Tus cambios se conservan. Reintenta con conexión; si otro administrador editó el borrador, vuelve a cargarlo.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun reload() = perform { val value = loadDraft(); mutable.update { it.copy(draft = value, baseline = value) } }
    override fun save() {
        val value = state.value.draft ?: return
        if (!state.value.valid) return
        perform { val saved = saveDraft(value); mutable.update { it.copy(draft = saved, baseline = saved, message = "Borrador guardado") } }
    }
    override fun publish() {
        val value = state.value.draft ?: return
        if (!state.value.publishable) return
        perform { val published = publishDraft(value); mutable.update { it.copy(message = "Versión ${published.version} publicada") } }
    }
    fun dispose() { disposed = true; job?.cancel() }
}

@Composable
fun BatteryManagementContent(state: BatteryEditorState, actions: BatteryEditorActions, enabled: Boolean) {
    val value = state.draft
    val available = enabled && !state.busy
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmReload by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
      if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      state.error?.let { ManagementMessage(it, true) }
      state.message?.let { ManagementMessage(it) }
      LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ReferenceCard {
            Text("Configura el recorrido", style = MaterialTheme.typography.titleLarge)
            Text("Selecciona las actividades y el orden que verá el trabajador.", color = MutedInk)
            Text("Publicar una versión conserva los recorridos ya iniciados.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        } }
        if (value != null) {
            item { OutlinedTextField(value.name, { if (it.length <= 100) actions.change(value.copy(name = it)) }, label = { Text("Nombre de la batería") }, modifier = Modifier.fillMaxWidth(), enabled = available, singleLine = true) }
            item { Text("Actividades del recorrido", style = MaterialTheme.typography.titleMedium)
                Text(if (state.dirty) "Cambios sin guardar" else if (value.revision > 0) "Borrador guardado" else "Borrador nuevo", color = MutedInk) }
            if (value.modules.isEmpty()) item { ManagementEmpty("Agrega una actividad", "La batería necesita al menos una actividad antes de guardarse.") }
            itemsIndexed(value.modules, key = { _, id -> id }) { index, id -> ReferenceCard {
                Text("${index + 1}. ${moduleTitle(id)}", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ val modules = value.modules.toMutableList(); java.util.Collections.swap(modules, index, index - 1); actions.change(value.copy(modules = modules)) }, enabled = available && index > 0) { Text("Subir") }
                    TextButton({ val modules = value.modules.toMutableList(); java.util.Collections.swap(modules, index, index + 1); actions.change(value.copy(modules = modules)) }, enabled = available && index < value.modules.lastIndex) { Text("Bajar") }
                    TextButton({ actions.change(value.copy(modules = value.modules - id)) }, enabled = available) { Text("Quitar") }
                }
            } }
            item { Text("Agregar actividades", style = MaterialTheme.typography.titleMedium) }
            items(BatteryCatalog.modules().filter { it.id !in value.modules }, key = { "add-${it.id}" }) { module ->
                OutlinedButton({ actions.change(value.copy(modules = value.modules + module.id)) }, enabled = available && value.modules.size < 8, modifier = Modifier.fillMaxWidth()) { Text("Agregar ${moduleTitle(module.id)}${if (!module.available) " · pendiente de habilitación" else ""}") }
            }
            val unavailable = value.modules.filter { id -> BatteryCatalog.modules().none { it.id == id && it.available } }
            if (unavailable.isNotEmpty()) item { ManagementEmpty("Hay actividades pendientes de habilitación", "No se puede publicar: ${unavailable.joinToString { moduleTitle(it) }}. Quita esas actividades o incorpora su contenido revisado.") }
            item { PrimaryAction("Guardar borrador", enabled = available && state.valid && (state.dirty || value.revision == 0L), onClick = actions::save) }
            item { ReferenceCard {
                Text("Publicar para trabajadores", style = MaterialTheme.typography.titleMedium)
                Text("Guarda primero los cambios. La nueva versión se ofrecerá para los próximos recorridos.", color = MutedInk)
                PrimaryAction("Publicar nueva versión", enabled = available && state.publishable, onClick = { confirmPublish = true })
            } }
        } else if (!state.busy && state.error == null) item { ManagementEmpty("Cargando borrador", "Se consultará la configuración de tu organización.") }
        item { OutlinedButton({ if (state.dirty) confirmReload = true else actions.reload() }, enabled = available, modifier = Modifier.fillMaxWidth()) { Text("Volver a cargar borrador") } }
      }
    }
    if (confirmPublish) AlertDialog(onDismissRequest = { confirmPublish = false }, title = { Text("¿Publicar nueva versión?") },
        text = { Text("Se publicará «${value?.name.orEmpty()}» con ${value?.modules?.size ?: 0} actividades. Los recorridos iniciados conservarán su versión.") },
        confirmButton = { TextButton({ confirmPublish = false; actions.publish() }, enabled = available && state.publishable) { Text("Publicar") } }, dismissButton = { TextButton({ confirmPublish = false }) { Text("Cancelar") } })
    if (confirmReload) AlertDialog(onDismissRequest = { confirmReload = false }, title = { Text("¿Descartar los cambios?") }, text = { Text("Se cargará el último borrador guardado.") },
        confirmButton = { TextButton({ confirmReload = false; actions.reload() }) { Text("Descartar y cargar") } }, dismissButton = { TextButton({ confirmReload = false }) { Text("Seguir editando") } })
}
