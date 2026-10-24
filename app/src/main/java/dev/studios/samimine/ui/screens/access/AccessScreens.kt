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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.studios.samimine.app.*
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.theme.SamiMineTheme
import dev.studios.samimine.ui.components.LocalSyncEnabled
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
                else -> key(session.profile.uid, session.profile.role) { ManagementScreen(state, access) }
            }
        }
    }
}

@Composable
internal fun LoginScreen(state: AccessState, login: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val submit = { if (!state.loading && email.isNotBlank() && password.isNotEmpty()) {
        val secret = password; password = ""; focus.clearFocus(); login(email.trim(), secret)
    } }
    Surface(color = MaterialTheme.colorScheme.background) {
      Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        dev.studios.samimine.ui.components.Brand(large = true)
        Card(Modifier.widthIn(max = 440.dp).fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
          Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bienvenido", style = MaterialTheme.typography.headlineMedium)
            Text("Ingresa a tu espacio de evaluación", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true,
                leadingIcon = { Icon(dev.studios.samimine.ui.components.SamiIcons.Person, null) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
            OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
                leadingIcon = { Icon(dev.studios.samimine.ui.components.SamiIcons.Lock, null) },
                trailingIcon = { TextButton({ visible = !visible }, enabled = !state.loading) { Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium) } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), enabled = !state.loading)
            Button(submit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                enabled = !state.loading && email.isNotBlank() && password.isNotEmpty()) {
                Text(if (state.loading) "Ingresando…" else "Ingresar")
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) }
          }
        }
        Text("Acceso con la cuenta asignada por tu organización.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppUpdateButton()
        Text("Entorno de desarrollo · Usa únicamente datos ficticios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
}

@Composable
private fun WorkerExperience(access: AccessViewModel, session: UserSession, state: AccessState, attach: (SamiMineViewModel?) -> Unit) {
    val uid = session.profile.uid
    val store = remember(uid) { ViewModelStore() }
    val database = remember(uid) { access.container.database(uid) }
    val offline by rememberUpdatedState(session.offline)
    val model = remember(uid) {
        val repository = RoomEvaluationRepository(database)
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SamiMineViewModel(repository,
                EvaluationUseCases(repository), access.container.clock, repository,
                organizationId = session.profile.organizationId,
                batteryConfigLoader = { dev.studios.samimine.data.remote.FirebaseBatteryConfigurationRepository(access.container.firestore,
                    session.profile.organizationId).workerDefinition(database, offline) }) as T
        })[SamiMineViewModel::class.java]
    }
    DisposableEffect(model) { attach(model); onDispose { attach(null); store.clear() } }
    LaunchedEffect(uid) {
        database.sync().observe(uid).map { rows -> rows.filter { it.state == "PENDING" }.map { "${it.kind}/${it.runId}" }.toSet() }
            .distinctUntilChanged().collect { pending -> if (pending.isNotEmpty()) access.container.sync(uid).schedule(uid) }
    }
    val queue by remember(uid) { database.sync().observe(uid) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var showQueue by remember { mutableStateOf(false) }
    var showUpdates by remember { mutableStateOf(false) }
    val workerState by model.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(session.profile.name, style = MaterialTheme.typography.titleSmall)
            if (workerState.destination == dev.studios.samimine.app.navigation.Destination.HOME) {
                TextButton({ showUpdates = true }) { Text("Actualizar SamiMine") }
            }
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
        if (showUpdates) AlertDialog(onDismissRequest = { showUpdates = false },
            title = { Text("Actualizar SamiMine") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("La actualización conserva tu sesión e historial. Android solicitará confirmar la instalación.")
                AppUpdateButton()
            } }, confirmButton = { TextButton({ showUpdates = false }) { Text("Cerrar") } })
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            CompositionLocalProvider(LocalSyncEnabled provides true) { SamiMineApp(model, session.profile.worker()) { access.logout(model) } }
            if (state.loading) Box(Modifier.fillMaxSize().clickable { })
        }
    }
}
