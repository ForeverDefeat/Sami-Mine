package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.*
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.screens.cognitive.moduleTitle

@Composable
fun RemoteBatterySummary(summary: String, chunks: List<String>, enabled: Boolean, open: (BatteryAttempt) -> Unit) {
    val definition = runCatching { JsonParser.parseString(summary).asJsonObject.getAsJsonObject("definition") }.getOrNull()
    val version = runCatching { definition?.get("version")?.asInt }.getOrNull()
    Text("Versión ${version ?: "anterior"}")
    Text("Resultados por actividad. No se calcula una puntuación global de aptitud.")
    val steps = runCatching {
        chunks.flatMap { JsonParser.parseString(it).asJsonArray.toList() }.map { element ->
            Gson().fromJson(element, BatteryStep::class.java).also { step ->
                // Old battery records may contain Borg even though new configurations exclude it.
                require(step.moduleId in (BatteryCatalog.modules().map { it.id } + "BORG") && step.status in BatteryStepStatus.entries)
                require(step.attempts.all { attempt -> attempt.kind in setOf("PVT", "COGNITIVE", "REPORT") &&
                    attempt.runId.matches(Regex("[A-Za-z0-9_-]{1,128}")) })
            }
        }
    }.getOrDefault(emptyList())
    if (steps.isEmpty()) Text("No se pudieron mostrar los pasos de este recorrido. Vuelve a cargarlo o consulta su historial individual.")
    steps.forEachIndexed { index, step ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1}. ${moduleTitle(step.moduleId)}", style = MaterialTheme.typography.titleMedium)
                Text(when (step.status) {
                    BatteryStepStatus.COMPLETED -> "Completada"; BatteryStepStatus.OMITTED -> "Respuesta omitida"
                    BatteryStepStatus.INTERRUPTED -> "Interrumpida"; BatteryStepStatus.IN_PROGRESS -> "En curso"; BatteryStepStatus.PENDING -> "Sin realizar"
                })
                step.attempts.forEachIndexed { attemptIndex, attempt ->
                    TextButton({ open(attempt) }, enabled = enabled) { Text("Ver resultado del intento ${attemptIndex + 1}") }
                }
            }
        }
    }
}
