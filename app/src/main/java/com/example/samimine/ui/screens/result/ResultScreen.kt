package com.example.samimine.ui.screens.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.theme.*
import java.util.Locale

@Composable
fun ResultScreen(evaluation: Evaluation, fromHistory: Boolean, finish: () -> Unit, restart: () -> Unit) {
    ActionPage(scrollKey = "pvt-result-${evaluation.id}", action = {
        PrimaryAction(if (fromHistory) "Volver al historial" else "Volver al inicio", onClick = finish)
        if (!fromHistory && evaluation.status == EvaluationStatus.INTERRUPTED) {
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
        if (evaluation.status == EvaluationStatus.INTERRUPTED) {
            InfoCard("ACTIVIDAD INTERRUMPIDA", "${evaluation.interruptionReason.orEmpty()} Se guardaron ${evaluation.attempts.size} de ${evaluation.configuration.attempts} señales.")
        }
        evaluation.metrics?.let { metrics ->
            AdaptiveDataPair("TIEMPO MEDIO DE RESPUESTA", metrics.meanReactionMillis?.let { String.format(Locale.getDefault(), "%.1f segundos", it / 1000.0) } ?: "Sin datos",
                "RESPUESTAS", "${metrics.responses} de ${evaluation.configuration.attempts}", metric = true)
            Text("Respuestas: ${metrics.responses}\nToques antes de la señal: ${metrics.anticipations}\nSeñales que no alcanzaste a tocar: ${metrics.omissions}",
                style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.fillMaxWidth())
        }
        IconMessage("Guardado en este teléfono", filled = true)
    }
}
