package dev.studios.samimine.ui.screens.access

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.studios.samimine.app.*
import dev.studios.samimine.data.remote.FirebaseBatteryConfigurationRepository
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class ManagementActions(
    val selectWorker: (UserProfile) -> Unit = {}, val backToWorkers: () -> Unit = {},
    val openResult: (RemoteRun) -> Unit = {}, val openAttempt: (BatteryAttempt) -> Unit = {},
    val closeResult: () -> Unit = {}, val moreWorkers: () -> Unit = {}, val moreRuns: () -> Unit = {},
    val refreshWorkers: () -> Unit = {}, val refreshRuns: () -> Unit = {}, val retry: () -> Unit = {}, val reconnect: () -> Unit = {},
    val clearFeedback: () -> Unit = {},
    val saveWorker: (UserProfile, WorkerEditDraft) -> Unit = { _, _ -> }, val logout: () -> Unit = {},
)

data class WorkerEditDraft(val name: String, val area: String, val employment: String, val active: Boolean, val assigned: String?) {
    companion object {
        fun from(worker: UserProfile?) = WorkerEditDraft(worker?.name.orEmpty(), worker?.area.orEmpty(), worker?.employment.orEmpty(), worker?.active ?: true, worker?.wellbeingUid)
        val Saver = listSaver<WorkerEditDraft, String>(
            save = { listOf(it.name, it.area, it.employment, it.active.toString(), it.assigned.orEmpty()) },
            restore = { WorkerEditDraft(it[0], it[1], it[2], it[3].toBoolean(), it[4].ifEmpty { null }) })
    }
}

@Composable
internal fun ManagementScreen(state: AccessState, access: AccessViewModel) {
    val profile = state.session!!.profile
    DisposableEffect(state.selectedWorker?.uid) { val stop = access.watchWorker(); onDispose { stop() } }
    val repository = remember(profile.organizationId) { FirebaseBatteryConfigurationRepository(access.container.firestore, profile.organizationId) }
    val scope = rememberCoroutineScope()
    val battery = remember(profile.uid) { BatteryEditorController(scope, repository::loadDraft, repository::saveDraft, repository::publish) }
    val batteryState by battery.state.collectAsStateWithLifecycle()
    DisposableEffect(battery) { onDispose { battery.dispose() } }
    ManagementExperience(state, ManagementActions(
        selectWorker = access::selectWorker, backToWorkers = access::backToWorkers,
        openResult = { access.detail(it) }, closeResult = access::closeDetail,
        openAttempt = { access.detail(RemoteRun("${it.kind}_${it.runId}", it.kind, it.status, 0), nested = true) },
        moreWorkers = { access.refreshWorkers(true) }, moreRuns = access::moreRuns,
        refreshWorkers = { access.refreshAccess(); access.refreshWorkers() }, refreshRuns = access::refreshRuns,
        retry = access::retryManagement, reconnect = access::refreshAccess, clearFeedback = access::clearManagementFeedback, logout = { access.logout(null) },
        saveWorker = { worker, draft -> access.saveWorker(worker, draft.name, draft.area, draft.employment, draft.active, draft.assigned) }),
        batteryState, battery)
}

@Composable
fun ManagementExperience(state: AccessState, actions: ManagementActions, batteryState: BatteryEditorState = BatteryEditorState(),
    battery: BatteryEditorActions = BatteryEditorActions.None, update: @Composable () -> Unit = { AppUpdateButton() }) {
    val session = state.session ?: return
    val profile = session.profile
    if (profile.role == UserRole.WORKER) return
    val admin = profile.role == UserRole.ADMIN
    var tab by rememberSaveable(profile.uid, profile.role) { mutableStateOf("workers") }
    var editing by rememberSaveable(profile.uid, state.selectedWorker?.uid) { mutableStateOf(false) }
    var draft by rememberSaveable(profile.uid, state.selectedWorker?.uid, stateSaver = WorkerEditDraft.Saver) { mutableStateOf(WorkerEditDraft.from(state.selectedWorker)) }
    var pendingLeave by remember(profile.uid) { mutableStateOf<(() -> Unit)?>(null) }
    val dirty = editing && draft != WorkerEditDraft.from(state.selectedWorker) || tab == "batteries" && batteryState.dirty
    val enabled = !state.loading && !session.offline
    fun leave(action: () -> Unit) { if (state.loading || batteryState.busy) return; if (dirty) pendingLeave = action else action() }
    fun back() {
        leave {
            when {
                editing -> editing = false
                tab != "workers" -> tab = "workers"
                state.detail != null -> actions.closeResult()
                state.selectedWorker != null -> actions.backToWorkers()
            }
        }
    }
    LaunchedEffect(state.profileSaveVersion) {
        if (state.notice == "Perfil y asignación guardados") { draft = WorkerEditDraft.from(state.selectedWorker); editing = false }
    }
    LaunchedEffect(session.offline, state.selectedWorker?.uid) {
        if (state.selectedWorker == null) { editing = false; pendingLeave = null }
    }
    val nested = tab == "workers" && (state.selectedWorker != null || state.detail != null || editing)
    BackHandler(nested || tab != "workers") { back() }
    val workerList = rememberLazyListState()
    val detailState = rememberSaveableStateHolder()
    val historyList = key(state.selectedWorker?.uid) { rememberLazyListState() }
    val title = when {
        tab == "account" -> "Mi cuenta"
        tab == "batteries" -> "Baterías"
        editing -> "Editar perfil y asignación"
        state.detail != null && !admin -> "Resultado de actividad"
        state.selectedWorker != null -> if (admin) "Participación recibida" else "Historial de evaluaciones"
        admin -> "Trabajadores"
        else -> "Trabajadores asignados"
    }
    Scaffold(containerColor = PageBackground, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            val tabs = if (admin) listOf("workers" to "Trabajadores", "batteries" to "Baterías", "account" to "Cuenta") else listOf("workers" to "Trabajadores", "account" to "Cuenta")
            tabs.forEach { (id, label) -> NavigationBarItem(selected = tab == id, enabled = !state.loading && !batteryState.busy,
                onClick = { if (id != tab) leave { editing = false; tab = id } },
                modifier = Modifier.testTag("management-tab-$id"),
                colors = NavigationBarItemDefaults.colors(selectedIconColor = BrandBlue, selectedTextColor = BrandBlue,
                    indicatorColor = SoftBlue, unselectedIconColor = MutedInk, unselectedTextColor = MutedInk),
                icon = { Icon(when (id) { "workers" -> SamiIcons.Person; "batteries" -> SamiIcons.Grid; else -> SamiIcons.Lock }, null) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }) }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Brand()
                        if (tab == "workers" && !nested) TextButton(actions.refreshWorkers, enabled = enabled) { Text("Actualizar") }
                    }
                    Text(profile.role.label, style = SamiTextStyles.SectionLabel, color = BrandBlue)
                    if (nested) TextButton({ back() }, enabled = !state.loading) {
                        Icon(SamiIcons.ArrowLeft, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(when { editing -> "Volver al seguimiento"; state.detailParents.isNotEmpty() -> "Volver al recorrido"; state.detail != null -> "Volver al historial"; else -> "Volver a trabajadores" })
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
            }
            HorizontalDivider(color = Separator)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Cargando información" })
            state.error?.let { message -> ManagementMessage(message, true) {
                if (editing && admin && state.selectedWorker != null) {
                    if (draft.name.trim().length in 1..100) actions.saveWorker(state.selectedWorker, draft)
                } else actions.retry()
            } }
            state.notice?.let { ManagementMessage(it) }
            when {
                tab == "account" -> ManagementAccount(profile, !state.loading, { leave(actions.logout) }, update)
                session.offline -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManagementEmpty("Sin conexión", "Conéctate para consultar información o guardar cambios.")
                    if (dirty) Text("Tus cambios sin guardar se conservan en esta pantalla.", color = MutedInk)
                    OutlinedButton(actions.reconnect) { Text("Reintentar conexión") }
                }
                tab == "batteries" && admin -> {
                    LaunchedEffect(Unit) { battery.loadIfNeeded() }
                    BatteryManagementContent(batteryState, battery, enabled)
                }
                editing && admin && state.selectedWorker != null -> WorkerProfileEditor(draft, state.selectedWorker, state.wellbeing,
                    enabled, { draft = it }, { actions.saveWorker(state.selectedWorker, draft) })
                state.detail != null && !admin && state.selectedWorker != null -> detailState.SaveableStateProvider("${state.selectedWorker.uid}/${state.detail.id}") {
                    RemoteResultScreen(state.selectedWorker, state.detail, state.detailChunks, enabled, actions.openAttempt)
                }
                state.selectedWorker != null -> ManagementHistory(state.selectedWorker, state.runs, admin, enabled, state.loading,
                    state.moreRuns, historyList, actions.openResult, actions.moreRuns, actions.refreshRuns,
                    { draft = WorkerEditDraft.from(state.selectedWorker); editing = true })
                else -> ManagementWorkers(state.workers, state.moreWorkers, state.loading, enabled, workerList, actions.selectWorker, actions.moreWorkers)
            }
        }
    }
    pendingLeave?.let { action -> AlertDialog(onDismissRequest = { pendingLeave = null }, title = { Text("¿Descartar los cambios?") },
        text = { Text("Los cambios sin guardar se perderán.") },
        confirmButton = { TextButton({ draft = WorkerEditDraft.from(state.selectedWorker); battery.discard(); actions.clearFeedback(); pendingLeave = null; action() }) { Text("Descartar y salir") } },
        dismissButton = { TextButton({ pendingLeave = null }) { Text("Seguir editando") } }) }
}

@Composable
internal fun ManagementMessage(message: String, error: Boolean = false, retry: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), shape = SamiShapes.Card,
        color = if (error) MaterialTheme.colorScheme.errorContainer else SoftBlue) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
            retry?.let { TextButton(it) { Text("Reintentar") } }
        }
    }
}

@Composable
internal fun ManagementEmpty(title: String, description: String) {
    ReferenceCard { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = MutedInk) }
}

@Composable
internal fun ManagementWorkers(workers: List<UserProfile>, more: Boolean, loading: Boolean, enabled: Boolean, scroll: LazyListState,
    select: (UserProfile) -> Unit, loadMore: () -> Unit) {
    LazyColumn(state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("management-workers")) {
        item { Text("Selecciona una persona para consultar su información.", color = MutedInk) }
        if (workers.isEmpty() && !loading) item { ManagementEmpty("No hay trabajadores disponibles", "Actualiza la lista o consulta las asignaciones de tu organización.") }
        items(workers, key = { it.uid }) { worker ->
            Card(onClick = { select(worker) }, enabled = enabled, shape = SamiShapes.Card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = SamiShapes.Card, color = SoftBlue) { Icon(SamiIcons.Person, null, Modifier.padding(12.dp).size(24.dp), tint = BrandBlue) }
                        Text("${worker.name} · ${if (worker.active) "Activo" else "Desactivado"}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(SamiIcons.ArrowRight, null, Modifier.size(20.dp), tint = BrandBlue)
                    }
                    Text(worker.area.ifBlank { "Área sin registrar" }, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                    Text("Consultar información", color = BrandBlue, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (more) item { OutlinedButton(loadMore, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Cargar más trabajadores") } }
    }
}

@Composable
internal fun WorkerIdentity(worker: UserProfile) {
    ReferenceCard {
        Text(worker.name, style = MaterialTheme.typography.titleLarge)
        Text(worker.area.ifBlank { "Área sin registrar" }, color = MutedInk)
        if (worker.employment.isNotBlank()) Text(worker.employment, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        Text(if (worker.active) "Acceso activo" else "Acceso desactivado", style = MaterialTheme.typography.labelLarge, color = BrandBlue)
    }
}

@Composable
internal fun ManagementHistory(worker: UserProfile, runs: List<RemoteRun>, admin: Boolean, enabled: Boolean, loading: Boolean,
    more: Boolean, scroll: LazyListState, open: (RemoteRun) -> Unit, loadMore: () -> Unit, refresh: () -> Unit, edit: () -> Unit) {
    LazyColumn(state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("management-history")) {
        item { WorkerIdentity(worker) }
        if (admin) item { OutlinedButton(edit, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Editar perfil y asignación") } }
        item { Text(if (admin) "Seguimiento de actividades recibidas" else "Evaluaciones más recientes", style = MaterialTheme.typography.titleMedium) }
        item { TextButton(refresh, enabled = enabled) { Text("Actualizar historial") } }
        if (runs.isEmpty() && !loading) item { ManagementEmpty("Sin ejecuciones recibidas", "Las actividades aparecerán cuando el trabajador las haya enviado.") }
        items(runs, key = { it.id }) { run ->
            ReferenceCard {
                Text(if (admin) runTitle(run.kind) else run.title?.let(::displayTitle) ?: runTitle(run.kind), style = MaterialTheme.typography.titleMedium)
                StatusBadge(run.status)
                Text(managementDate(run.startedAtMillis), style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                if (!admin) TextButton({ open(run) }, enabled = enabled) { Text("Ver resultado y respuestas") }
            }
        }
        if (more) item { OutlinedButton(loadMore, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Cargar más evaluaciones") } }
    }
}

@Composable
internal fun StatusBadge(status: String) {
    Surface(shape = SamiShapes.Capsule, color = if (status == "COMPLETED") SoftBlue else QuietSurface) {
        Text(statusLabel(status), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

internal fun managementDate(millis: Long) = if (millis > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es-PE")).format(Date(millis)) else "Fecha no disponible"

@Composable
private fun ManagementAccount(profile: UserProfile, enabled: Boolean, logout: () -> Unit, update: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ReferenceCard {
            Icon(SamiIcons.Person, null, Modifier.size(32.dp), tint = BrandBlue)
            Text(profile.name, style = MaterialTheme.typography.headlineSmall)
            Text(profile.role.label, color = MutedInk)
        }
        ReferenceCard { Text("Actualizaciones", style = MaterialTheme.typography.titleMedium); Text("Consulta si hay una nueva versión de SamiMine.", color = MutedInk); update() }
        OutlinedButton(logout, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Cerrar sesión") }
    }
}

@Composable
internal fun WorkerProfileEditor(draft: WorkerEditDraft, worker: UserProfile, wellbeing: List<UserProfile>, enabled: Boolean,
    change: (WorkerEditDraft) -> Unit, save: () -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    val valid = draft.name.trim().length in 1..100 && draft.area.length <= 100 && draft.employment.length <= 100
    ActionPage(modifier = Modifier.imePadding(), scrollKey = worker.uid, action = {
        PrimaryAction("Guardar cambios", enabled = enabled && valid && draft != WorkerEditDraft.from(worker), onClick = save)
    }) {
        Text("Datos del trabajador", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(draft.name, { if (it.length <= 100) change(draft.copy(name = it)) }, label = { Text("Nombre") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.area, { if (it.length <= 100) change(draft.copy(area = it)) }, label = { Text("Área") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.employment, { if (it.length <= 100) change(draft.copy(employment = it)) }, label = { Text("Vínculo laboral") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        ReferenceCard {
            Text("Acceso a la aplicación", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.active, { change(draft.copy(active = it)) }, enabled = enabled); Text("Acceso activo", Modifier.weight(1f)) }
            Text("Desactivar el acceso conserva el historial registrado.", color = MutedInk)
        }
        ReferenceCard {
            Text("Personal de bienestar asignado", style = MaterialTheme.typography.titleMedium)
            Text(wellbeing.firstOrNull { it.uid == draft.assigned }?.name ?: if (draft.assigned == null) "Sin asignación" else "Asignación actual no disponible", color = MutedInk)
            OutlinedButton({ selecting = true }, enabled = enabled) { Text("Cambiar asignación") }
        }
    }
    if (selecting) AlertDialog(onDismissRequest = { selecting = false }, title = { Text("Asignar bienestar") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { TextButton({ change(draft.copy(assigned = null)); selecting = false }, modifier = Modifier.fillMaxWidth()) { Text("Sin asignación") } }
            items(wellbeing, key = { it.uid }) { person -> TextButton({ change(draft.copy(assigned = person.uid)); selecting = false }, modifier = Modifier.fillMaxWidth()) { Text(person.name) } }
        }
    }, confirmButton = { TextButton({ selecting = false }) { Text("Cancelar") } })
}
