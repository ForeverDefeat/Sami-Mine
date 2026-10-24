package dev.studios.samimine.ui.screens.cognitive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*

@Composable
fun GuidedBatteryScreen(run: GuidedBatteryRun?, definition: BatteryDefinition?, message: String?, enabled: Boolean,
    start: () -> Unit, next: () -> Unit, pause: () -> Unit, abandon: () -> Unit, refresh: () -> Unit,
    openAttempt: (BatteryAttempt) -> Unit) {
    var confirmAbandon by remember(run?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(run?.definition?.name ?: "Batería guiada", style = MaterialTheme.typography.headlineMedium)
        message?.let { Text(it) }
        if (run == null) {
            Text(definition?.let { "Versión ${it.version} · ${it.modules.size} actividades" } ?: "No hay una batería disponible en este dispositivo.")
            definition?.modules?.forEachIndexed { index, id -> Text("${index + 1}. ${moduleTitle(id)}") }
            Button(start, enabled = enabled && definition != null) { Text("Iniciar batería") }
            OutlinedButton(refresh, enabled = enabled) { Text("Actualizar configuración") }
        } else {
            Text("Versión ${run.definition.version} · Turno ${run.shift.label}")
            Text(runStatus(run.status), style = MaterialTheme.typography.titleMedium)
            val completed = run.steps.count { it.status in setOf(BatteryStepStatus.COMPLETED, BatteryStepStatus.OMITTED) }
            Text("$completed de ${run.steps.size} actividades resueltas")
            run.steps.forEachIndexed { index, step ->
                ReferenceCard {
                    Text("${index + 1}. ${moduleTitle(step.moduleId)}", style = MaterialTheme.typography.titleMedium)
                    Text(stepStatus(step.status))
                    step.attempts.forEachIndexed { attemptIndex, attempt ->
                        TextButton({ openAttempt(attempt) }, enabled = enabled && attempt.status != "IN_PROGRESS") {
                            Text("Ver intento ${attemptIndex + 1} · ${attemptStatus(attempt.status)}")
                        }
                    }
                }
            }
            if (!run.terminal) {
                Button(next, enabled = enabled) { Text(if (run.steps.any { it.attempts.isNotEmpty() }) "Continuar batería" else "Comenzar primera actividad") }
                Text("Las actividades terminadas se conservan. Una prueba interrumpida empieza de nuevo.")
                OutlinedButton(pause, enabled = enabled) { Text("Pausar y volver al inicio") }
                TextButton({ confirmAbandon = true }, enabled = enabled) { Text("Abandonar batería") }
            } else {
                Text("Los resultados son descriptivos; no hay una puntuación global de aptitud.")
                Button(pause, enabled = enabled) { Text("Volver al inicio") }
            }
        }
    }
    if (confirmAbandon) AlertDialog(onDismissRequest = { confirmAbandon = false },
        title = { Text("¿Abandonar la batería?") }, text = { Text("Se conservarán los resultados, pero este recorrido no podrá continuar.") },
        confirmButton = { TextButton({ confirmAbandon = false; abandon() }, enabled = enabled) { Text("Abandonar") } },
        dismissButton = { TextButton({ confirmAbandon = false }) { Text("Continuar revisando") } })
}
fun moduleTitle(id: String): String = BatteryCatalog.modules().firstOrNull { it.id == id }?.title
    ?: InstrumentId.entries.firstOrNull { it.name == id }?.label ?: id
fun runStatus(status: BatteryRunStatus) = when (status) {
    BatteryRunStatus.PREPARED -> "Preparada"; BatteryRunStatus.IN_PROGRESS -> "En curso"; BatteryRunStatus.PAUSED -> "Pausada"
    BatteryRunStatus.COMPLETED -> "Completada"; BatteryRunStatus.ABANDONED -> "Abandonada"
}
private fun stepStatus(status: BatteryStepStatus) = when (status) {
    BatteryStepStatus.PENDING -> "Pendiente"; BatteryStepStatus.IN_PROGRESS -> "En curso"
    BatteryStepStatus.COMPLETED -> "Completada"; BatteryStepStatus.OMITTED -> "Respuesta omitida explícitamente"
    BatteryStepStatus.INTERRUPTED -> "Interrumpida · requiere un nuevo intento"
}
private fun attemptStatus(status: String) = when (status) {
    "COMPLETED" -> "Completado"; "OMITTED" -> "Omitido"; "INVALID" -> "Inválido"; else -> "Interrumpido"
}
