package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.*
import dev.studios.samimine.app.RemoteRun
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

data class RemoteMetric(val key: String, val label: String, val value: String, val unit: String)
data class RemoteResultData(val metrics: List<RemoteMetric>, val shift: String, val answers: List<String>, val malformed: Boolean)

/** Reads stored values only. Missing, invalid or incomplete metrics are never replaced by zero. */
fun remoteResultData(run: RemoteRun, chunks: List<String>): RemoteResultData {
    val root = runCatching { JsonParser.parseString(run.summaryJson ?: "{}").asJsonObject }.getOrNull()
    val type = runCatching { CognitiveTestType.valueOf(root?.get("type")?.asString.orEmpty()) }.getOrNull()
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).apply { maximumFractionDigits = 2 }
    fun metric(key: String, value: JsonElement?, unit: String): RemoteMetric {
        val display = runCatching { value?.takeUnless { it.isJsonNull }?.asDouble?.takeIf { it.isFinite() }?.let(number::format) }.getOrNull() ?: "Sin datos"
        val label = type?.metricLabel(key) ?: when (key) {
            "medianReactionMillis" -> "Mediana de respuesta"; "meanReactionMillis" -> "Tiempo medio de respuesta"
            "responses" -> "Respuestas"; "anticipations" -> "Toques antes de la señal"; "omissions" -> "Sin respuesta"
            "meanResponseSpeed" -> "Velocidad de respuesta"; "total" -> "Total registrado"; else -> "Resultado registrado"
        }
        return RemoteMetric(key, label, display, when (unit) { "count" -> ""; "responses/s" -> "respuestas/s"; else -> unit })
    }
    val stored = runCatching { root?.getAsJsonArray("storedMetrics")?.map { entry ->
        val item = entry.asJsonObject
        metric(item.get("key").asString, item.get("value"), item.get("unit")?.asString.orEmpty())
    }?.distinctBy { it.key } }.getOrNull()
    val metrics = if (run.status != "COMPLETED") emptyList() else if (run.kind == "REPORT" && root?.get("total")?.isJsonPrimitive == true)
        listOf(metric("total", root.get("total"), "")) else stored ?: runCatching {
        val values = root?.getAsJsonObject("metrics")
        (values?.get("values")?.takeIf { it.isJsonObject }?.asJsonObject ?: values)?.entrySet()?.map { (key, value) -> metric(key, value, when {
            key.endsWith("Millis") || key.endsWith("Ms") -> "ms"
            key == "accuracyPercent" -> "%"
            key == "meanResponseSpeed" -> "respuestas/s"
            key == "correctPerMinute" -> "/ min"
            else -> ""
        }) }.orEmpty()
    }.getOrDefault(emptyList())
    var malformed = root == null
    val answers = chunks.flatMap { chunk ->
        runCatching { JsonParser.parseString(chunk).asJsonArray.map { it.toString() } }.getOrElse { malformed = true; emptyList() }
    }
    val shift = runCatching { when (root?.get("shift")?.asString) { "DAY" -> "Día"; "NIGHT" -> "Noche"; else -> "No registrado" } }.getOrDefault("No registrado")
    return RemoteResultData(metrics, shift, answers, malformed)
}

@Composable
fun RemoteResultScreen(worker: UserProfile, run: RemoteRun, chunks: List<String>, enabled: Boolean, open: (BatteryAttempt) -> Unit) {
    val data = remember(run, chunks) { remoteResultData(run, chunks) }
    var showAnswers by rememberSaveable { mutableStateOf(false) }
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    var showMetrics by rememberSaveable { mutableStateOf(false) }
    var visibleAnswers by rememberSaveable { mutableIntStateOf(25) }
    val scroll = rememberLazyListState()
    val primaryKeys = listOf("medianReactionMillis", "responses", "correct", "goHits", "found", "attempted", "hitRate", "accuracyPercent", "omissions", "goOmissions", "errors", "noGoCommissions", "falseAlarms", "correctPerMinute", "medianCorrectRtMs", "medianHitRtMs")
    val primary = data.metrics.sortedBy { primaryKeys.indexOf(it.key).let { position -> if (position < 0) Int.MAX_VALUE else position } }.take(6)
    LazyColumn(state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(worker.name, style = MaterialTheme.typography.titleMedium)
            Text(worker.area.ifBlank { "Área sin registrar" }, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
        } }
        item { ReferenceCard {
            Text(run.title?.let(::displayTitle) ?: runTitle(run.kind), style = MaterialTheme.typography.titleLarge)
            StatusBadge(run.status)
            Text(managementDate(run.startedAtMillis), color = MutedInk)
            Text("Turno: ${data.shift}", color = MutedInk)
        } }
        if (run.kind == "BATTERY") item { RemoteBatterySummary(run.summaryJson ?: "{}", chunks, enabled, open) }
        else {
            item { Text("Resumen de resultados", style = MaterialTheme.typography.titleLarge) }
            if (data.metrics.isEmpty()) item { ManagementEmpty("Sin métricas finales disponibles", if (run.status == "COMPLETED") "Este registro no contiene métricas que puedan mostrarse." else "La actividad conserva su estado y respuestas registradas.") }
            items(primary, key = { "metric-${it.key}" }) { metric -> ReferenceCard {
                Text(metric.label, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                Text(metric.value + if (metric.value != "Sin datos" && metric.unit.isNotBlank()) " ${metric.unit}" else "", style = MaterialTheme.typography.headlineSmall)
            } }
            if (data.metrics.size > primary.size) {
                item { Disclosure("Más métricas", showMetrics) { showMetrics = !showMetrics } }
                if (showMetrics) items(data.metrics.filterNot { it in primary }, key = { "extra-${it.key}" }) { metric ->
                    ReferenceCard { Text(metric.label, style = MaterialTheme.typography.titleSmall); Text("${metric.value} ${metric.unit}") }
                }
            }
            item { Disclosure("Respuestas registradas", showAnswers) { showAnswers = !showAnswers } }
            if (showAnswers) {
                if (data.answers.isEmpty()) item { Text("No hay respuestas disponibles en este registro.", color = MutedInk) }
                itemsIndexed(data.answers.take(visibleAnswers), key = { index, _ -> "answer-$index" }) { index, answer -> ReferenceCard {
                    Text("Registro ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    SummaryView(answer)
                } }
                if (visibleAnswers < data.answers.size) item { OutlinedButton({ visibleAnswers += 25 }, modifier = Modifier.fillMaxWidth()) { Text("Mostrar más respuestas") } }
            }
            item { Disclosure("Datos técnicos", showTechnical) { showTechnical = !showTechnical } }
            if (showTechnical) item { ReferenceCard {
                val technical = remember(run.summaryJson) { runCatching { JsonParser.parseString(run.summaryJson ?: "{}").asJsonObject.apply {
                    remove("metrics")
                    runCatching { get("configSnapshot")?.asString?.let { add("configuration", JsonParser.parseString(it)) } }
                }.toString() }.getOrDefault("{}") }
                SummaryView(technical, technical = true)
            } }
        }
        if (data.malformed) item { ManagementMessage("Parte del registro no está disponible. Puedes volver al historial e intentar cargarlo de nuevo.", true) }
        item { Text("Los resultados se presentan por actividad. La interpretación corresponde al personal de salud ocupacional.", style = MaterialTheme.typography.bodySmall, color = MutedInk) }
    }
}

@Composable
private fun Disclosure(title: String, expanded: Boolean, toggle: () -> Unit) {
    OutlinedButton(toggle, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (expanded) "Ocultar detalle" else "Mostrar detalle", style = MaterialTheme.typography.labelMedium)
        }
    }
}
