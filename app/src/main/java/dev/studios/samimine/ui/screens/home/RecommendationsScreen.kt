package dev.studios.samimine.ui.screens.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.ReferenceCard
import kotlinx.coroutines.delay
import java.util.Locale

/** Local, voluntary guidance. Session state is intentionally never persisted or synchronized. */
@Composable
fun RecommendationsScreen() {
    var selected by remember { mutableStateOf<WellbeingExercise?>(null) }
    var session by remember { mutableStateOf<ExerciseSession?>(null) }
    fun catalog() { session?.pause(); session = null; selected = null }
    BackHandler(selected != null) {
        if (session != null) { session?.pause(); session = null } else catalog()
    }
    val exercise = selected
    val active = session
    when {
        exercise == null -> ExerciseCatalog { selected = it }
        active != null -> ExerciseSessionView(active, onExit = ::catalog, onRepeat = {
            session = ExerciseSession(exercise, SystemClock::elapsedRealtime).also { it.startOrResume() }
        })
        else -> ExerciseDetail(exercise, onBack = ::catalog, onStart = {
            session = ExerciseSession(exercise, SystemClock::elapsedRealtime).also { it.startOrResume() }
        })
    }
}

@Composable
private fun GuidancePage(key: String, content: @Composable ColumnScope.() -> Unit) {
    key(key) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
private fun ExerciseCatalog(open: (WellbeingExercise) -> Unit) {
    var evaluationTips by remember { mutableStateOf(false) }
    GuidancePage("catalog") {
        Text("Recomendaciones", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Una pausa para ti", style = MaterialTheme.typography.titleLarge)
        Text("Elige una guía de 5 minutos para tu pausa. Puedes detenerla cuando quieras.", style = MaterialTheme.typography.bodyLarge)
        Text(WellbeingExercises.SAFE_PLACE, style = MaterialTheme.typography.bodyMedium)
        WellbeingExercises.all.forEachIndexed { index, exercise ->
            ReferenceCard {
                ExerciseCharacter(visual = when (exercise.id) {
                    "gentle-breathing" -> ExerciseVisual.BREATHING
                    "muscle-relaxation" -> ExerciseVisual.SHOULDERS_TENSE
                    else -> ExerciseVisual.LISTENING
                }, compact = true)
                Text("0${index + 1} · 5 MINUTOS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(exercise.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(exercise.purpose, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { open(exercise) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("exercise-${exercise.id}")) {
                    Text("Ver ejercicio")
                }
            }
        }
        ReferenceCard {
            Text("Bienestar durante el turno", style = MaterialTheme.typography.titleMedium)
            Text(EducationalContent.BODY)
        }
        OutlinedButton(onClick = { evaluationTips = !evaluationTips }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (evaluationTips) "Ocultar consejos para tu evaluación" else "Consejos para tu evaluación")
        }
        if (evaluationTips) {
            GuidanceCard("Antes de las pruebas", "Realiza las actividades en el momento indicado para tu evaluación. Lee las instrucciones y busca un lugar que te permita responder sin interrupciones.")
            GuidanceCard("Durante la evaluación", "Sigue las instrucciones de cada actividad. Si necesitas descansar, puedes pausar la evaluación.")
            GuidanceCard("Después de las pruebas", "Consulta tu historial y el estado de envío. Comparte tus dudas con el personal de Salud Ocupacional.")
        }
    }
}

@Composable
private fun ExerciseDetail(exercise: WellbeingExercise, onBack: () -> Unit, onStart: () -> Unit) {
    GuidancePage("detail-${exercise.id}") {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver a las guías") }
        Text(exercise.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("5 minutos · A tu ritmo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(exercise.purpose, style = MaterialTheme.typography.bodyLarge)
        GuidanceCard("Antes de comenzar", exercise.preparation)
        Text(WellbeingExercises.SAFE_PLACE)
        Text(WellbeingExercises.STOP_GUIDANCE, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("exercise-start")) { Text("Comenzar") }

    }
}

@Composable
internal fun ExerciseSessionView(session: ExerciseSession, onExit: () -> Unit, onRepeat: () -> Unit) {
    // The domain controller owns time; this revision only requests recomposition after an action/tick.
    var revision by remember(session) { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { session.pause(); revision++ }
        }
        owner.lifecycle.addObserver(observer)
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { session.pause(); revision++ }
        onDispose { session.pause(); owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session) {
        while (true) {
            delay(200)
            if (session.status == ExerciseSessionStatus.ACTIVE) { session.tick(); revision++ }
        }
    }
    // Read before rendering because the controller is intentionally independent of Compose.
    @Suppress("UNUSED_VARIABLE") val frame = revision
    val status = session.status
    if (status == ExerciseSessionStatus.FINISHED) {
        GuidancePage("finished-${session.exercise.id}") {
            Text("Has terminado la guía", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(session.exercise.title, style = MaterialTheme.typography.titleLarge)
            Text("Tómate un momento para observar tu entorno antes de continuar con tu día.")
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Volver a las guías") }
            OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Repetir guía") }
        }
        return
    }
    GuidancePage("session-${session.exercise.id}") {
        Text(session.exercise.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(if (status == ExerciseSessionStatus.PAUSED) "En pausa · Continúa cuando estés listo" else "Paso ${session.stepIndex + 1} de ${session.exercise.steps.size}",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("exercise-status"))
        LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
        ReferenceCard {
            ExerciseCharacter(visual = session.step.visual, session = session)
            Text(session.step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(session.step.instruction, style = MaterialTheme.typography.bodyLarge)
            Text(String.format(Locale.ROOT, "%d:%02d", session.totalRemainingSeconds / 60, session.totalRemainingSeconds % 60),
                style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("exercise-time"))
            Text("Tiempo restante", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            if (status == ExerciseSessionStatus.ACTIVE) session.pause() else session.startOrResume()
            revision++
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (status == ExerciseSessionStatus.ACTIVE) "Pausar" else "Continuar") }
        if (session.step.canSkip) OutlinedButton(onClick = { session.skipStep(); revision++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Omitir este paso") }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Terminar") }
        Text("Si notas molestias, termina la guía. No necesitas completar todos los pasos.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuidanceCard(title: String, body: String) {
    ReferenceCard {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
