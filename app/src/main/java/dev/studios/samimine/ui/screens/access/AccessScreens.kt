package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.studios.samimine.app.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.theme.SamiMineTheme
import dev.studios.samimine.ui.components.LocalSyncEnabled
import com.google.gson.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.DateFormat
import java.util.Date

@Composable
fun AccessRoot(access: AccessViewModel, attachWorker: (SamiMineViewModel?) -> Unit) {
    val state by access.state.collectAsStateWithLifecycle()
    val session = state.session
    DisposableEffect(session?.profile?.uid) {
        val stop = access.watchAccess()
        onDispose { stop() }
    }
    SamiMineTheme {
        Surface(Modifier.fillMaxSize()) {
            when {
                session == null -> LoginScreen(state, access::login)
                session.profile.role == UserRole.WORKER -> key(session.profile.uid) { WorkerExperience(access, session, state, attachWorker) }
                else -> ManagementScreen(state, access)
            }
        }
    }
}

@Composable
private fun LoginScreen(state: AccessState, login: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SamiMine", style = MaterialTheme.typography.headlineLarge)
        Text("Ingresa con tu cuenta de prueba", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
        Button({ val secret = password; password = ""; login(email, secret) }, enabled = !state.loading && email.isNotBlank() && password.isNotEmpty()) { Text("Ingresar") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Entorno de desarrollo · Usa únicamente datos ficticios.")
    }
}

@Composable
private fun WorkerExperience(access: AccessViewModel, session: UserSession, state: AccessState, attach: (SamiMineViewModel?) -> Unit) {
    val uid = session.profile.uid
    val store = remember(uid) { ViewModelStore() }
    val database = remember(uid) { access.container.database(uid) }
    val model = remember(uid) {
        val repository = RoomEvaluationRepository(database)
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SamiMineViewModel(repository,
                EvaluationUseCases(repository), access.container.clock, repository) as T
        })[SamiMineViewModel::class.java]
    }
    DisposableEffect(model) { attach(model); onDispose { attach(null); store.clear() } }
    LaunchedEffect(uid) {
        database.sync().observe(uid).map { rows -> rows.filter { it.state == "PENDING" }.map { "${it.kind}/${it.runId}" }.toSet() }
            .distinctUntilChanged().collect { pending -> if (pending.isNotEmpty()) access.container.sync(uid).schedule(uid) }
    }
    val queue by remember(uid) { database.sync().observe(uid) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var showQueue by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(session.profile.name, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ showQueue = !showQueue }) { Text(if (session.offline) "Sin conexión · ${queue.count { it.state != "SYNCED" }} pendientes" else "Envíos: ${queue.count { it.state == "SYNCED" }} confirmados") }
                TextButton({ access.logout(model) }, enabled = !state.loading) { Text("Cerrar sesión") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (showQueue) {
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    if (queue.isEmpty()) Text("Todavía no hay evaluaciones para enviar.")
                    queue.forEach { entry ->
                        Text("${runTitle(entry.kind)} · ${entry.runId.take(8)} · ${syncLabel(entry.state)}")
                        entry.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                TextButton({ access.container.sync(uid).schedule(uid); access.refreshAccess() }, enabled = !session.offline) { Text("Reintentar envíos") }
            }
        }
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            CompositionLocalProvider(LocalSyncEnabled provides true) { SamiMineApp(model, session.profile.worker()) { access.logout(model) } }
            if (state.loading) Box(Modifier.fillMaxSize().clickable { })
        }
    }
}

@Composable
private fun ManagementScreen(state: AccessState, access: AccessViewModel) {
    val profile = state.session!!.profile
    val worker = state.selectedWorker
    val online = !state.session.offline
    DisposableEffect(worker?.uid) {
        val stop = access.watchWorker()
        onDispose { stop() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(profile.role.label, style = MaterialTheme.typography.headlineSmall)
        Text(profile.name)
        Row {
            TextButton({ access.logout(null) }, enabled = !state.loading) { Text("Cerrar sesión") }
            TextButton({ access.refreshAccess(); access.refreshWorkers() }, enabled = !state.loading) { Text("Actualizar") }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            !online -> Text("Se necesita conexión para consultar o administrar información.")
            state.detail != null -> {
                TextButton(access::closeDetail, enabled = !state.loading) { Text("Volver al historial") }
                Text(state.detail.title?.let(::displayTitle) ?: runTitle(state.detail.kind), style = MaterialTheme.typography.titleLarge)
                Text(statusLabel(state.detail.status))
                SummaryView(state.detail.summaryJson ?: "{}")
                Text("Respuestas registradas", style = MaterialTheme.typography.titleMedium)
                state.detailChunks.forEach { chunk ->
                    JsonParser.parseString(chunk).asJsonArray.forEachIndexed { index, element ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text("Registro ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            SummaryView(element.toString())
                        } }
                    }
                }
            }
            worker != null -> {
                TextButton(access::backToWorkers, enabled = !state.loading) { Text("Volver a trabajadores") }
                Text(worker.name, style = MaterialTheme.typography.titleLarge)
                if (profile.role == UserRole.ADMIN) WorkerEditor(worker, state.wellbeing, !state.loading) { name, area, employment, active, wellbeing ->
                    access.saveWorker(worker, name, area, employment, active, wellbeing)
                }
                Text(if (profile.role == UserRole.ADMIN) "Participación recibida" else "Historial de evaluaciones", style = MaterialTheme.typography.titleMedium)
                if (state.runs.isEmpty() && !state.loading) Text("Sin ejecuciones recibidas.")
                state.runs.forEach { run ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Text(run.title?.let(::displayTitle) ?: runTitle(run.kind)); Text(statusLabel(run.status)); Text(DateFormat.getDateTimeInstance().format(Date(run.startedAtMillis)))
                        if (profile.role == UserRole.WELLBEING) TextButton({ access.detail(run) }, enabled = !state.loading) { Text("Ver resultado y respuestas") }
                    } }
                }
                if (state.moreRuns) TextButton(access::moreRuns, enabled = !state.loading) { Text("Cargar más evaluaciones") }
            }
            else -> {
                Text(if (profile.role == UserRole.WELLBEING) "Trabajadores asignados" else "Trabajadores", style = MaterialTheme.typography.titleLarge)
                if (state.workers.isEmpty() && !state.loading) Text("No hay trabajadores disponibles.")
                state.workers.forEach { item ->
                    OutlinedButton({ access.selectWorker(item) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                        Text("${item.name} · ${if (item.active) "Activo" else "Desactivado"}")
                    }
                }
                if (state.moreWorkers) TextButton({ access.refreshWorkers(true) }, enabled = !state.loading) { Text("Cargar más trabajadores") }
            }
        }
    }
}

@Composable
private fun WorkerEditor(worker: UserProfile, wellbeing: List<UserProfile>, enabled: Boolean,
    save: (String, String, String, Boolean, String?) -> Unit) {
    var name by remember(worker) { mutableStateOf(worker.name) }
    var area by remember(worker) { mutableStateOf(worker.area) }
    var employment by remember(worker) { mutableStateOf(worker.employment) }
    var active by remember(worker) { mutableStateOf(worker.active) }
    var assigned by remember(worker) { mutableStateOf(worker.wellbeingUid) }
    OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("Nombre") }, enabled = enabled)
    OutlinedTextField(area, { if (it.length <= 100) area = it }, label = { Text("Área") }, enabled = enabled)
    OutlinedTextField(employment, { if (it.length <= 100) employment = it }, label = { Text("Vínculo laboral") }, enabled = enabled)
    Row { Checkbox(active, { active = it }, enabled = enabled); Text("Acceso activo") }
    Text("Personal de bienestar asignado")
    TextButton({ assigned = null }, enabled = enabled) { Text(if (assigned == null) "✓ Sin asignación" else "Sin asignación") }
    wellbeing.forEach { person ->
        TextButton({ assigned = person.uid }, enabled = enabled) { Text("${if (assigned == person.uid) "✓ " else ""}${person.name}") }
    }
    Button({ save(name, area, employment, active, assigned) }, enabled = enabled && name.isNotBlank()) { Text("Guardar perfil y asignación") }
}

private val labels = mapOf(
    "metrics" to "Resultados", "storedMetrics" to "Métricas guardadas", "key" to "Métrica", "value" to "Valor", "unit" to "Unidad",
    "denominator" to "Observaciones", "methodVersion" to "Método", "calculationVersion" to "Versión de cálculo",
    "configuration" to "Configuración", "configurationVersion" to "Versión", "version" to "Versión", "protocolVersion" to "Protocolo",
    "shift" to "Turno", "meanReactionMillis" to "Tiempo medio (ms)", "medianReactionMillis" to "Mediana (ms)",
    "meanResponseSpeed" to "Velocidad de respuesta", "responses" to "Respuestas", "anticipations" to "Anticipaciones", "omissions" to "Omisiones",
    "index" to "Intento", "outcome" to "Resultado", "reactionNanos" to "Reacción (ns)", "isPractice" to "Práctica",
    "stimulusCode" to "Estímulo", "expectedResponse" to "Respuesta esperada", "actualResponse" to "Respuesta registrada", "outcomeCode" to "Resultado",
    "reactionTimeMillis" to "Reacción (ms)", "onsetUptimeMillis" to "Inicio del estímulo (ms monotónicos)", "responseUptimeMillis" to "Respuesta (ms monotónicos)",
    "interTrialDelayMillis" to "Intervalo (ms)", "interruptionReason" to "Motivo de interrupción", "reason" to "Motivo",
    "question" to "Pregunta", "answer" to "Respuesta", "instrumentId" to "Cuestionario", "instrumentVersion" to "Versión del cuestionario",
    "language" to "Idioma", "omitted" to "Omitido", "total" to "Total", "scoringMethod" to "Método de puntuación", "context" to "Contexto", "moment" to "Momento",
    "attempts" to "Intentos", "minWaitMillis" to "Espera mínima (ms)", "maxWaitMillis" to "Espera máxima (ms)", "responseWindowMillis" to "Ventana de respuesta (ms)",
    "medianReactionMs" to "Mediana (ms)", "meanReactionMs" to "Tiempo medio (ms)", "correct" to "Aciertos", "errors" to "Errores", "validTrials" to "Ensayos válidos",
    "accuracyPercent" to "Precisión (%)", "medianCorrectRtMs" to "Mediana de aciertos (ms)", "correctPerMinute" to "Aciertos por minuto",
    "commissionRate" to "Tasa de comisiones", "omissionRate" to "Tasa de omisiones", "status" to "Estado", "type" to "Prueba",
)

@Composable private fun SummaryView(json: String) {
    val tree = remember(json) { runCatching { JsonParser.parseString(json) }.getOrNull() }
    tree?.let { RenderValue(it) }
}
@Composable private fun RenderValue(value: JsonElement, depth: Int = 0) {
    if (depth > 5 || value.isJsonNull) return
    when {
        value.isJsonObject -> value.asJsonObject.entrySet().filter { it.key in labels }.forEach { (key, item) ->
            if (!item.isJsonNull) {
                if (item.isJsonPrimitive) Text("${labels[key]}: ${displayValue(item.asString)}", style = MaterialTheme.typography.bodyMedium)
                else { Text(labels[key]!!, style = MaterialTheme.typography.titleSmall); Column(Modifier.padding(start = 12.dp)) { RenderValue(item, depth + 1) } }
            }
        }
        value.isJsonArray -> value.asJsonArray.forEach { RenderValue(it, depth + 1) }
    }
}
private fun displayValue(value: String) = when (value) {
    "true" -> "Sí"; "false" -> "No"; "DAY" -> "Día"; "NIGHT" -> "Noche"
    else -> statusLabel(value)
}
private fun runTitle(kind: String) = when (kind) { "PVT" -> "Prueba de reacción"; "COGNITIVE" -> "Prueba cognitiva"; "REPORT" -> "Cuestionario"; "ASSESSMENT" -> "Recorrido breve"; else -> kind }
private fun displayTitle(title: String) = when (title) {
    "FLANKER" -> "Flecha central · Flanker"; "GO_NO_GO" -> "Responder o esperar · GO/NO-GO"
    "VISUAL_SEARCH" -> "Busca la T"; "DSST" -> "Símbolos y dígitos"; "N_BACK" -> "Recuerda el anterior"
    else -> title
}
private fun statusLabel(status: String) = when (status) { "COMPLETED" -> "Completada"; "INCOMPLETE", "INTERRUPTED" -> "Interrumpida"; "INVALID" -> "No válida"; "OMITTED" -> "Omitida"; else -> status }
private fun syncLabel(status: String) = when (status) { "PENDING" -> "Pendiente"; "SENDING" -> "Enviando"; "SYNCED" -> "Sincronizado"; else -> "Requiere atención" }
