package dev.studios.samimine.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*

@Composable
fun HistoryScreen(worker: Worker, evaluations: List<Evaluation>, enabled: Boolean, openCognitive: (String) -> Unit = {}, cognitiveSessions: List<CognitiveTestSession> = emptyList(),
    assessments: List<AssessmentSession> = emptyList(), reports: List<SelfReport> = emptyList(), openAssessment: (String) -> Unit = {}, openReport: (String) -> Unit = {},
    guidedRuns: List<GuidedBatteryRun> = emptyList(), openGuided: (String) -> Unit = {}, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = SamiDimens.Page), contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DemoLabel()
                Text("Mi historial", style = MaterialTheme.typography.headlineMedium)
                Text(worker.name.participantName(), style = MaterialTheme.typography.bodyLarge)
                Text("Actividades guardadas en este teléfono.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
        if (evaluations.isEmpty() && cognitiveSessions.isEmpty() && assessments.isEmpty() && reports.isEmpty() && guidedRuns.isEmpty()) item { InfoCard("AÚN NO HAY ACTIVIDADES", "Aquí aparecerán las actividades que completes.") }
        items(guidedRuns, key = { "guided-${it.id}" }) { run ->
            ReferenceCard {
                Text(run.definition.name, style = MaterialTheme.typography.titleMedium)
                Text("Versión ${run.definition.version} · ${formatDate(run.startedAtMillis)} · ${run.shift.label}")
                Text(dev.studios.samimine.ui.screens.cognitive.runStatus(run.status))
                TextButton({ openGuided(run.id) }, enabled = enabled) { Text("Ver batería y resultados") }
            }
        }
        items(assessments, key = { "assessment-${it.id}" }) { parent ->
            ReferenceCard {
                Text("Recorrido breve · KSS → PVT", style = MaterialTheme.typography.titleMedium)
                Text("${formatDate(parent.startedAtMillis)} · ${parent.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                Text(when (parent.status) { "COMPLETED" -> "Completado"; "INVALID" -> "Inválido"; else -> "Incompleto" }, style = MaterialTheme.typography.bodyMedium)
                Text(parent.bundleVersion, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                TextButton({ openAssessment(parent.id) }, enabled = enabled, modifier = Modifier.heightIn(min = SamiDimens.TouchTarget)) { Text("Ver recorrido") }
            }
        }
        items(reports, key = { "report-${it.id}" }) { report ->
            ReferenceCard {
                Text(report.instrumentId.label, style = MaterialTheme.typography.titleMedium)
                Text("${formatDate(report.answeredAtMillis)} · ${report.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                Text(if (report.omitted) "Respuesta omitida" else "Cuestionario respondido", style = MaterialTheme.typography.bodyMedium)
                Text(report.instrumentVersion, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                TextButton({ openReport(report.id) }, enabled = enabled, modifier = Modifier.heightIn(min = SamiDimens.TouchTarget)) { Text("Ver cuestionario") }
            }
        }
        items(evaluations, key = { it.id }) { evaluation ->
            Card(onClick = { open(evaluation.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = SamiShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Separator)) {
                Column(Modifier.padding(SamiDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SamiIcons.Clock, null, Modifier.size(20.dp), tint = AuxiliaryInk)
                    Text(evaluation.status.label(), style = MaterialTheme.typography.titleMedium)
                    Text("${formatDate(evaluation.startedAtMillis)} · ${evaluation.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                    Text("Prueba de reacción · ${evaluation.attempts.size} de ${evaluation.configuration.attempts} señales", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                    Text(evaluation.configuration.version, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ver detalle", style = SamiTextStyles.Button, color = BrandBlue)
                        Icon(SamiIcons.ArrowRight, null, Modifier.size(20.dp), tint = BrandBlue)
                    }
                }
            }
        }
        items(cognitiveSessions, key = { "cognitive-${it.id}" }) { session ->
            Card(onClick = { openCognitive(session.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = SamiShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Separator)) {
                Column(Modifier.padding(SamiDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SamiIcons.Clock, null, Modifier.size(20.dp), tint = AuxiliaryInk)
                    Text(session.type.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(session.status.displayName(), style = MaterialTheme.typography.bodyMedium)
                    Text("${formatDate(session.startedAtMillis)} · ${session.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                    Text("${session.trials.count { !it.isPractice }} ${session.type.trialUnit()}", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                    Text(session.protocolVersion, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
                    Text("Ver detalle  →", style = SamiTextStyles.Button, color = BrandBlue)
                }
            }
        }
    }
}
