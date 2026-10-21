package dev.studios.samimine.ui.screens.cognitive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*

@Composable
fun QuestionnaireScreen(definition: InstrumentDefinition, brief: Boolean, enabled: Boolean,
    submit: (Map<String, String>, String?, String?, Boolean) -> Unit, back: () -> Unit) {
    var answers by remember(definition.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var context by remember(definition.id) { mutableStateOf("") }
    var moment by remember(definition.id) { mutableStateOf("") }
    val complete = definition.questions.all { answers.containsKey(it.id) } &&
        (definition.id != InstrumentId.BORG || context.isNotBlank() && moment.isNotBlank())
    ActionPage(scrollKey = "questionnaire-${definition.id}", action = {
        if (definition.available) {
            PrimaryAction(if (brief) "Guardar y continuar a PVT" else "Guardar respuestas", enabled = enabled && complete,
                onClick = { submit(answers, context.takeIf { it.isNotBlank() }, moment.takeIf { it.isNotBlank() }, false) })
            if (definition.id == InstrumentId.KSS) TextButton(onClick = { submit(emptyMap(), null, null, true) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget)) { Text(if (brief) "Omitir respuesta y continuar" else "Omitir respuesta") }
        } else PrimaryAction("Volver al catálogo", onClick = back)
    }) {
        DemoLabel()
        if (brief) Text("Recorrido breve · Paso 1 de 2", style = SamiTextStyles.SectionLabel, color = BrandBlue)
        Text(definition.id.label, style = MaterialTheme.typography.headlineMedium)
        if (!definition.available) {
            InfoCard("PENDIENTE DE HABILITACIÓN", "Este cuestionario necesita una versión autorizada en español, instrucciones y aprobación de contenido. Aún no se pueden registrar respuestas.")
            Text("Puedes realizar las pruebas cognitivas disponibles desde el catálogo.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        } else {
            Text(definition.instructions, style = MaterialTheme.typography.bodyLarge)
            Text(definition.recallPeriod, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            if (definition.id == InstrumentId.BORG) {
                OutlinedTextField(context, { context = it }, enabled = enabled, label = { Text("Actividad realizada") }, modifier = Modifier.fillMaxWidth().testTag("borg-context"))
                OutlinedTextField(moment, { moment = it }, enabled = enabled, label = { Text("Momento de la actividad") }, modifier = Modifier.fillMaxWidth().testTag("borg-moment"))
            }
            definition.questions.forEach { question ->
                ReferenceCard {
                    Text(question.text, style = MaterialTheme.typography.titleMedium)
                    question.options.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget)
                            .selectable(answers[question.id] == option.code, enabled = enabled, role = Role.RadioButton,
                                onClick = { answers = answers + (question.id to option.code) }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(selected = answers[question.id] == option.code, onClick = null, enabled = enabled)
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Text("${definition.version} · ${definition.language}", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        }
    }
}

@Composable
fun SelfReportResultScreen(report: SelfReport, fromHistory: Boolean, finish: () -> Unit) {
    ActionPage(scrollKey = report.id, action = { PrimaryAction(if (fromHistory) "Volver al historial" else "Volver a cuestionarios", onClick = finish) }) {
        DemoLabel()
        Text(report.instrumentId.label, style = MaterialTheme.typography.headlineMedium)
        Text(if (report.omitted) "Respuesta omitida" else "Respuestas guardadas", style = MaterialTheme.typography.titleLarge)
        Text("${formatDate(report.answeredAtMillis)} · Turno ${report.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        if (report.omitted) Text("No se registró un valor. Esta ausencia no equivale a una puntuación de cero.", style = MaterialTheme.typography.bodyLarge)
        else report.answers.entries.forEachIndexed { index, entry -> DataCard("Respuesta ${index + 1} · Valor ordinal", entry.value.toString(), Modifier.fillMaxWidth()) }
        report.total?.let { DataCard("Total según versión autorizada", it.toString()) }
        report.context?.let { InfoCard("ACTIVIDAD REALIZADA", it) }
        report.moment?.let { InfoCard("MOMENTO", it) }
        Text("${report.instrumentVersion} · ${report.language} · ${report.scoringMethod}", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        IconMessage("Guardado en este teléfono", filled = true)
        Text(dev.studios.samimine.ui.components.syncHint(dev.studios.samimine.ui.components.LocalSyncEnabled.current), style = MaterialTheme.typography.bodySmall, color = MutedInk)
        InfoCard("BIENESTAR · ${EducationalContent.VERSION}", EducationalContent.BODY)
    }
}

@Composable
fun AssessmentResultScreen(parent: AssessmentSession, reports: List<SelfReport>, pvt: Evaluation?, fromHistory: Boolean, finish: () -> Unit) {
    ActionPage(scrollKey = parent.id, action = { PrimaryAction(if (fromHistory) "Volver al historial" else "Volver al inicio", onClick = finish) }) {
        DemoLabel()
        Text("Resumen del recorrido breve", style = MaterialTheme.typography.headlineMedium)
        Text(when (parent.status) { "COMPLETED" -> "Recorrido completado"; "INVALID" -> "Recorrido inválido"; else -> "Recorrido incompleto" }, style = MaterialTheme.typography.titleLarge)
        Text("${formatDate(parent.startedAtMillis)} · Turno ${parent.shift.label}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        parent.reason?.let { InfoCard("MOTIVO", it) }
        ReferenceCard {
            Text("Somnolencia actual · KSS", style = MaterialTheme.typography.titleMedium)
            val report = reports.firstOrNull { it.instrumentId == InstrumentId.KSS }
            Text(when { report == null -> "Sin respuesta registrada"; report.omitted -> "Respuesta omitida"; else -> "Valor ordinal: ${report.answers.values.firstOrNull()}" }, style = MaterialTheme.typography.bodyLarge)
        }
        ReferenceCard {
            Text("Prueba de reacción · PVT", style = MaterialTheme.typography.titleMedium)
            Text(pvt?.status?.label() ?: "Sin prueba registrada", style = MaterialTheme.typography.bodyLarge)
            pvt?.metrics?.let { Text("Respuestas: ${it.responses} de ${pvt.configuration.attempts}", style = MaterialTheme.typography.bodyMedium) }
        }
        Text("Los resultados se presentan por separado. Este recorrido no determina aptitud laboral.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        Text(parent.bundleVersion, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        IconMessage("Guardado en este teléfono", filled = true)
        InfoCard("BIENESTAR · ${EducationalContent.VERSION}", EducationalContent.BODY)
    }
}
