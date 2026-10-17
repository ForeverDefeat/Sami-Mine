package com.example.samimine.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.theme.*

@Composable
fun HistoryScreen(worker: Worker, evaluations: List<Evaluation>, enabled: Boolean, openCognitive: (String) -> Unit = {}, cognitiveSessions: List<CognitiveTestSession> = emptyList(), open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = SamiDimens.Page), contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DemoLabel()
                Text("Mi historial", style = MaterialTheme.typography.headlineMedium)
                Text(worker.name.participantName(), style = MaterialTheme.typography.bodyLarge)
                Text("Actividades guardadas en este teléfono.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            }
        }
        if (evaluations.isEmpty() && cognitiveSessions.isEmpty()) item { InfoCard("AÚN NO HAY ACTIVIDADES", "Aquí aparecerán las actividades que completes.") }
        items(evaluations, key = { it.id }) { evaluation ->
            Card(onClick = { open(evaluation.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = SamiShapes.Card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, Separator)) {
                Column(Modifier.padding(SamiDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SamiIcons.Clock, null, Modifier.size(20.dp), tint = AuxiliaryInk)
                    Text(evaluation.status.label(), style = MaterialTheme.typography.titleMedium)
                    Text("${formatDate(evaluation.startedAtMillis)} · ${evaluation.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
                    Text("Prueba de reacción · ${evaluation.attempts.size} de ${evaluation.configuration.attempts} señales", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
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
                    Text("Ver detalle  →", style = SamiTextStyles.Button, color = BrandBlue)
                }
            }
        }
    }
}
