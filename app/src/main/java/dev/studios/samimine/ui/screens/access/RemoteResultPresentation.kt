package dev.studios.samimine.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.*

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

@Composable internal fun SummaryView(json: String, technical: Boolean = false) {
    val tree = remember(json) { runCatching { JsonParser.parseString(json) }.getOrNull() }
    tree?.let { RenderValue(it, technical = technical) }
}
private val technicalFields = setOf("configuration", "configurationVersion", "version", "protocolVersion", "methodVersion", "calculationVersion", "onsetUptimeMillis", "responseUptimeMillis", "interTrialDelayMillis", "instrumentVersion", "language", "scoringMethod", "minWaitMillis", "maxWaitMillis", "responseWindowMillis")
@Composable private fun RenderValue(value: JsonElement, depth: Int = 0, technical: Boolean = false) {
    if (depth > 5 || value.isJsonNull) return
    when {
        value.isJsonObject -> value.asJsonObject.entrySet().filter { it.key in labels && (technical || it.key !in technicalFields) }.forEach { (key, item) ->
            if (!item.isJsonNull) {
                if (item.isJsonPrimitive) {
                    if (key == "reactionNanos" && !technical) Text("Reacción (ms): ${runCatching { item.asDouble / 1_000_000 }.getOrNull() ?: "Sin datos"}", style = MaterialTheme.typography.bodyMedium)
                    else Text("${labels[key]}: ${displayValue(item.asString)}", style = MaterialTheme.typography.bodyMedium)
                } else { Text(labels[key]!!, style = MaterialTheme.typography.titleSmall); Column(Modifier.padding(start = 12.dp)) { RenderValue(item, depth + 1, technical) } }
            }
        }
        value.isJsonArray -> value.asJsonArray.forEach { RenderValue(it, depth + 1, technical) }
    }
}
private fun displayValue(value: String) = when (value) {
    "true" -> "Sí"; "false" -> "No"; "DAY" -> "Día"; "NIGHT" -> "Noche"
    "LEFT" -> "Izquierda"; "RIGHT" -> "Derecha"; "SAME" -> "Igual"; "DIFFERENT" -> "Diferente"; "RESPOND" -> "Responder"
    "HIT", "GO_HIT", "NBACK_HIT", "PRACTICE_CORRECT" -> "Respuesta correcta"
    "RESPONSE" -> "Respuesta registrada"
    "ERROR", "PRACTICE_ERROR" -> "Respuesta incorrecta"
    "OMISSION", "GO_OMISSION" -> "Sin respuesta"; "ANTICIPATION" -> "Toque antes de la señal"
    "NO_GO_CORRECT_REJECTION" -> "Inhibición correcta"; "CORRECT_REJECTION" -> "Diferencia identificada"
    "NO_GO_COMMISSION" -> "Toque ante el cuadrado"; "FALSE_ALARM" -> "Falsa coincidencia"; "MISS" -> "Coincidencia no identificada"
    else -> statusLabel(value)
}
internal fun runTitle(kind: String) = when (kind) { "PVT" -> "Prueba de reacción"; "COGNITIVE" -> "Prueba cognitiva"; "REPORT" -> "Cuestionario"; "ASSESSMENT" -> "Recorrido breve"; "BATTERY" -> "Batería guiada"; else -> kind }
internal fun displayTitle(title: String) = when (title) {
    "FLANKER" -> "Flecha central · Flanker"; "GO_NO_GO" -> "Responder o esperar · GO/NO-GO"
    "VISUAL_SEARCH" -> "Busca la T"; "DSST" -> "Símbolos y dígitos"; "N_BACK" -> "Recuerda el anterior"
    else -> title
}
internal fun statusLabel(status: String) = when (status) { "COMPLETED" -> "Completada"; "INCOMPLETE", "INTERRUPTED" -> "Interrumpida"; "INVALID" -> "No válida"; "OMITTED" -> "Omitida"; "ABANDONED" -> "Abandonada"; else -> status }
internal fun syncLabel(status: String) = when (status) { "PENDING" -> "Pendiente"; "SENDING" -> "Enviando"; "SYNCED" -> "Sincronizado"; else -> "Requiere atención" }
