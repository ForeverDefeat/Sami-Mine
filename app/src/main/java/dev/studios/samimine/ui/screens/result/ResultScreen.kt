package dev.studios.samimine.ui.screens.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.util.Locale

@Composable
fun ResultScreen(evaluation: Evaluation, fromHistory: Boolean, finish: () -> Unit, restart: () -> Unit, returnLabel: String = "Volver al inicio") {
    ActionPage(scrollKey = "pvt-result-${evaluation.id}", action = {
        PrimaryAction(if (fromHistory) "Volver al historial" else returnLabel, onClick = finish)
        if (!fromHistory && evaluation.status in setOf(EvaluationStatus.INTERRUPTED, EvaluationStatus.INVALID)) {
            TextButton(onClick = restart, modifier = Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget)) { Text("Repetir actividad") }
        }
    }) {
        SegmentedProgress(if (evaluation.status == EvaluationStatus.COMPLETED) 3 else 2, description = evaluation.status.label())
        DemoLabel()
        Text("Resumen de la actividad", style = SamiTextStyles.SectionLabel, color = AuxiliaryInk, modifier = Modifier.fillMaxWidth())
        Box(Modifier.size(80.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
            Icon(if (evaluation.status == EvaluationStatus.COMPLETED) SamiIcons.Bolt else SamiIcons.Clock,
                null, Modifier.size(36.dp), tint = BrandBlue)
        }
        Text(evaluation.status.label(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("Prueba de reacción", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Text("Este resumen describe esta actividad. No determina si estás apto para trabajar.", style = MaterialTheme.typography.bodyLarge, color = MutedInk, modifier = Modifier.fillMaxWidth())
        Text("${evaluation.worker.name.participantName()}\nTurno ${evaluation.shift.label} · ${formatDate(evaluation.startedAtMillis)}", style = MaterialTheme.typography.bodyMedium, color = AuxiliaryInk, modifier = Modifier.fillMaxWidth())
        if (evaluation.status in setOf(EvaluationStatus.INTERRUPTED, EvaluationStatus.INVALID)) {
            InfoCard("ACTIVIDAD INTERRUMPIDA", "${evaluation.interruptionReason.orEmpty()} Se guardaron ${evaluation.attempts.size} de ${evaluation.configuration.attempts} señales.")
        }
        evaluation.metrics?.let { metrics ->
            AdaptiveDataPair(if (evaluation.calculationVersion == "descriptive-v2") "MEDIANA DE RESPUESTA" else "TIEMPO MEDIO DE RESPUESTA",
                (if (evaluation.calculationVersion == "descriptive-v2") metrics.medianReactionMillis else metrics.meanReactionMillis)?.let { String.format(Locale.getDefault(), "%.0f ms", it) } ?: "Sin datos",
                "RESPUESTAS", "${metrics.responses} de ${evaluation.configuration.attempts}", metric = true)
            if (evaluation.calculationVersion == "descriptive-v2") {
                var expanded by remember(evaluation.id) { mutableStateOf(false) }
                Text("Mediana calculada con n = ${metrics.responses}", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                TextButton({ expanded = !expanded }, Modifier.heightIn(min = SamiDimens.TouchTarget)) { Text(if (expanded) "Ocultar detalles" else "Ver detalles") }
                if (expanded) {
                    DataCard("Velocidad media · respuestas/segundo", metrics.meanResponseSpeed?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "Sin datos")
                    Text("n = ${evaluation.attempts.count { (it.reactionNanos ?: 0) > 0 }} respuestas con tiempo positivo", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    DataCard("Tiempo medio", metrics.meanReactionMillis?.let { String.format(Locale.getDefault(), "%.0f ms", it) } ?: "Sin datos")
                }
            }
            Text("Respuestas: ${metrics.responses}\nToques antes de la señal: ${metrics.anticipations}\nSeñales que no alcanzaste a tocar: ${metrics.omissions}",
                style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.fillMaxWidth())
        }
        IconMessage("Guardado en este teléfono", filled = true)
        Text("${evaluation.configuration.version} · Cálculo ${evaluation.calculationVersion}\n${dev.studios.samimine.ui.components.syncHint(dev.studios.samimine.ui.components.LocalSyncEnabled.current)}", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        InfoCard("BIENESTAR · ${EducationalContent.VERSION}", EducationalContent.BODY)
    }
}
