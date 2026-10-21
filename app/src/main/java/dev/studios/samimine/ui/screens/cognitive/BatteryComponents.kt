package dev.studios.samimine.ui.screens.cognitive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.util.Locale

@Composable
fun BatterySymbol(symbol: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(48.dp)) {
        val stroke = size.minDimension * .07f
        val w = size.width; val h = size.height
        when (symbol) {
            0 -> drawCircle(Ink, size.minDimension * .35f, style = Stroke(stroke))
            1 -> drawRect(Ink, Offset(w * .18f, h * .18f), androidx.compose.ui.geometry.Size(w * .64f, h * .64f), style = Stroke(stroke))
            2, 3, 5 -> {
                val points = when (symbol) {
                    2 -> listOf(.5f to .1f, .9f to .85f, .1f to .85f)
                    3 -> listOf(.5f to .1f, .9f to .5f, .5f to .9f, .1f to .5f)
                    else -> listOf(.3f to .15f, .7f to .15f, .9f to .5f, .7f to .85f, .3f to .85f, .1f to .5f)
                }
                val path = Path().apply { moveTo(points[0].first * w, points[0].second * h); points.drop(1).forEach { lineTo(it.first * w, it.second * h) }; close() }
                drawPath(path, Ink, style = Stroke(stroke))
            }
            else -> {
                drawLine(Ink, Offset(w * .5f, h * .1f), Offset(w * .5f, h * .9f), stroke)
                drawLine(Ink, Offset(w * .1f, h * .5f), Offset(w * .9f, h * .5f), stroke)
            }
        }
    }
}

@Composable
fun SymbolKey(seed: Long) {
    val mapping = remember(seed) { CognitiveTrials.symbolMapping(seed) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Consulta la correspondencia", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { col ->
                    val symbol = row * 3 + col
                    Surface(Modifier.weight(1f), shape = SamiShapes.DataCard, color = QuietSurface) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BatterySymbol(symbol, Modifier.size(24.dp))
                            Text(mapping[symbol].toString(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssociationControls(gate: ContactGate, enabled: Boolean, trialIndex: Int, answer: (String, Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { col ->
                    val digit = row * 3 + col + 1
                    ResponseAction(digit.toString(), gate, enabled, Modifier.weight(1f).testTag("digit-$digit"), responseKey = trialIndex) { answer(digit.toString(), it) }
                }
            }
        }
    }
}

@Composable
fun MemoryControls(gate: ContactGate, enabled: Boolean, trialIndex: Int, answer: (String, Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ResponseAction("Igual", gate, enabled, Modifier.weight(1f), responseKey = trialIndex) { answer("SAME", it) }
        ResponseAction("Diferente", gate, enabled, Modifier.weight(1f), responseKey = trialIndex) { answer("DIFFERENT", it) }
    }
}

fun MetricResult.formatted(): String = value?.let { number ->
    when (unit) {
        "ms" -> String.format(Locale.getDefault(), "%.0f ms", number)
        "%" -> String.format(Locale.getDefault(), "%.1f %%", number)
        "count/min" -> String.format(Locale.getDefault(), "%.0f / min", number)
        else -> String.format(Locale.getDefault(), "%.0f", number)
    }
} ?: "Sin datos"

@Composable
fun BatteryMetricCards(type: CognitiveTestType, metrics: List<MetricResult>) {
    var expanded by remember(type) { mutableStateOf(false) }
    val first = metrics.take(2)
    if (first.size == 2) AdaptiveDataPair(type.metricLabel(first[0].key), first[0].formatted(), type.metricLabel(first[1].key), first[1].formatted(), metric = true)
    first.forEach { metric ->
        metric.denominator?.let { Text("${type.metricLabel(metric.key)} · n = $it", style = MaterialTheme.typography.bodySmall, color = MutedInk) }
    }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("metric-details")) { Text(if (expanded) "Ocultar detalles" else "Ver detalles") }
    if (expanded) metrics.drop(2).forEach { metric ->
        DataCard(type.metricLabel(metric.key), metric.formatted(), Modifier.fillMaxWidth().testTag("metric-${metric.key}"), metric = true)
        metric.denominator?.let { Text("n = $it", style = MaterialTheme.typography.bodySmall, color = MutedInk) }
    }
}
