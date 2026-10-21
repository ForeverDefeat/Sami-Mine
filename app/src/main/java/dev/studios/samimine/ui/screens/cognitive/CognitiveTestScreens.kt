package dev.studios.samimine.ui.screens.cognitive

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.util.Locale

@Composable
fun CognitiveInstructionsScreen(type: CognitiveTestType, back: () -> Unit, practice: () -> Unit, enabled: Boolean = true) {
    val instructions = when (type) {
        CognitiveTestType.FLANKER -> Triple("Mira solo la flecha del centro.", "Responde Izquierda o Derecha según apunte la flecha central. Ignora las flechas de los lados.", listOf("Observa las cinco flechas", "Identifica la dirección central", "Responde tan pronto como puedas"))
        CognitiveTestType.GO_NO_GO -> Triple("Círculo: responde. Cuadrado: espera.", "Pulsa Responder ante el círculo. Si aparece un cuadrado, no toques nada.", listOf("Mira la figura", "Círculo: toca Responder", "Cuadrado: no toques"))
        CognitiveTestType.VISUAL_SEARCH -> Triple("Busca la T entre las L.", "Toca directamente la casilla que contiene la letra T.", listOf("Observa la cuadrícula", "Encuentra la T", "Toca su casilla"))
        CognitiveTestType.DSST -> Triple("Asocia el símbolo con su dígito.", "La tabla permanece visible. Cada respuesta presenta otro símbolo durante 60 segundos.", listOf("Consulta la tabla de símbolos", "Busca el dígito correspondiente", "Toca ese dígito para continuar"))
        CognitiveTestType.N_BACK -> Triple("¿Es igual al símbolo anterior?", "Observa cada símbolo y compáralo con el que acabas de ver. El símbolo aparece 500 ms y tienes 2.500 ms para responder.", listOf("Recuerda el símbolo inicial", "Compara el símbolo siguiente con el anterior", "Responde Igual o Diferente"))
    }
    ActionPage(scrollKey = "cognitive-instructions-${type.name}", action = {
        PrimaryAction("Comenzar práctica", arrow = true, enabled = enabled, onClick = practice)
    }) {
        SegmentedProgress(1, description = "Instrucciones, etapa 1 de 3")
        DemoLabel()
        Text(type.displayName(), style = MaterialTheme.typography.headlineMedium)
        Text(instructions.first, style = MaterialTheme.typography.titleLarge)
        Text(instructions.second, style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        if (type in setOf(CognitiveTestType.FLANKER, CognitiveTestType.GO_NO_GO)) Text("Tienes hasta 3 segundos para responder. Luego aparecerá la siguiente figura.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        ReferenceCard {
            instructions.third.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(8.dp), color = QuietSurface) {
                        Box(contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.titleMedium) }
                    }
                    Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        val config = CognitiveTestConfig.forType(type)
        Text("${config.practiceTrials} ejemplos de práctica · " + if (type == CognitiveTestType.DSST) "60 segundos" else "${config.scoredTrials} ${type.trialUnit()}", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        if (type in setOf(CognitiveTestType.DSST, CognitiveTestType.N_BACK)) Text("Protocolo experimental propio · Sin validación clínica", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        Text("Esta es una práctica demostrativa. Sus resultados no sirven para decidir si estás apto para trabajar.", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
    }
}

/** Práctica sin RT puntuable: conserva registros y comparte la protección por gesto/ensayo. */
@Composable
fun CognitivePracticeScreen(
    type: CognitiveTestType,
    trial: CognitiveTrial,
    position: Int,
    total: Int,
    feedback: String?,
    enabled: Boolean,
    answer: (String?) -> Unit,
    seed: Long = 0,
) {
    val gate = remember { ContactGate() }
    val associationOrMemory = type in setOf(CognitiveTestType.DSST, CognitiveTestType.N_BACK)
    Column(Modifier.fillMaxSize().observeContacts(gate).padding(SamiDimens.Page), verticalArrangement = Arrangement.spacedBy(if (associationOrMemory) 12.dp else 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SegmentedProgress(1, description = "Práctica")
        DemoLabel()
        Text("Práctica ${position + 1}/$total", style = MaterialTheme.typography.headlineSmall)
        Text("Esta respuesta no cuenta en el resultado.", style = MaterialTheme.typography.bodyMedium, color = MutedInk, textAlign = TextAlign.Center)
        if (type == CognitiveTestType.DSST) SymbolKey(seed)
        if (type == CognitiveTestType.N_BACK) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Anterior", style = MaterialTheme.typography.bodyMedium)
            BatterySymbol(trial.referenceSymbol ?: 0)
        }
        Spacer(Modifier.weight(1f))
        Stimulus(type, trial, Modifier.fillMaxWidth().heightIn(min = if (associationOrMemory) 72.dp else 110.dp))
        if (type == CognitiveTestType.VISUAL_SEARCH) SearchGrid(trial.targetIndex ?: 0, enabled, gate, responseKey = trial.index) { cell, _ -> answer(cell.toString()) }
        else if (type == CognitiveTestType.DSST) AssociationControls(gate, enabled, trial.index) { value, _ -> answer(value) }
        else if (type == CognitiveTestType.N_BACK) MemoryControls(gate, enabled, trial.index) { value, _ -> answer(value) }
        else if (type == CognitiveTestType.FLANKER) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ResponseAction("Izquierda", gate, enabled, Modifier.fillMaxWidth(), responseKey = trial.index) { answer("LEFT") }
                ResponseAction("Derecha", gate, enabled, Modifier.fillMaxWidth(), responseKey = trial.index) { answer("RIGHT") }
            }
        } else {
            ResponseAction("Responder", gate, enabled, Modifier.fillMaxWidth(), responseKey = trial.index) { answer("RESPOND") }
            if (trial.stimulusCode == "NO_GO") ResponseAction("No responder", gate, enabled, outlined = true, responseKey = trial.index) { answer(null) }
        }
        feedback?.let { Text("Respuesta anterior: $it", style = MaterialTheme.typography.titleMedium, color = BrandBlue) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun CognitiveReadyScreen(type: CognitiveTestType, onStart: () -> Unit, enabled: Boolean = true) {
    val count = CognitiveTestConfig.forType(type).scoredTrials
    ActionPage(scrollKey = "cognitive-ready-${type.name}", action = { PrimaryAction("Comenzar prueba", arrow = true, enabled = enabled, onClick = onStart) }) {
        SegmentedProgress(1, description = "Preparación")
        DemoLabel()
        Text("Listo para comenzar", style = MaterialTheme.typography.headlineMedium)
        Text(if (type == CognitiveTestType.DSST) "Tendrás 60 segundos para asociar símbolos y dígitos. La tabla seguirá visible." else "Verás $count ${type.trialUnit()}. Durante la actividad no recibirás comentarios después de cada respuesta.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        if (type in setOf(CognitiveTestType.FLANKER, CognitiveTestType.GO_NO_GO)) Text("Tienes hasta 3 segundos para responder. Después aparecerá la siguiente figura.", style = MaterialTheme.typography.bodyLarge)
        if (type == CognitiveTestType.N_BACK) Text("Primero verás un símbolo de referencia. Después responde Igual o Diferente respecto al símbolo anterior.", style = MaterialTheme.typography.bodyLarge)
        if (type == CognitiveTestType.GO_NO_GO) InfoCard("¿QUÉ DEBO HACER?", "Toca Responder cuando veas un círculo. Si aparece un cuadrado, espera sin tocar.")
        else InfoCard("MODO DEMOSTRACIÓN", "Responde según las indicaciones. Puedes salir de la actividad en cualquier momento.")
    }
}

/**
 * Presenta fases del motor sin crear temporizadores de UI.
 * Notifica dibujo y primer contacto en uptime ms; Búsqueda Visual inicia desde la cuadrícula.
 * El gate persiste entre ensayos y el callback externo conserva ID de sesión e índice.
 */
@Composable
fun CognitiveRunScreen(
    type: CognitiveTestType,
    trial: CognitiveTrial,
    scoredPosition: Int,
    scoredTotal: Int,
    phase: CognitivePhase,
    enabled: Boolean,
    stimulusDrawn: (Long) -> Unit,
    answer: (String, Long) -> Unit,
    seed: Long = 0,
    stimulusVisible: Boolean = true,
) {
    val gate = remember { ContactGate() }
    Column(Modifier.fillMaxSize().observeContacts(gate).padding(horizontal = SamiDimens.Page, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SegmentedProgress(2, description = "Ejecución, etapa 2 de 3")
        DemoLabel()
        Text(if (type == CognitiveTestType.DSST) "${type.displayName()} · 60 segundos" else "${type.displayName()} · $scoredPosition/$scoredTotal", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (type == CognitiveTestType.DSST) SymbolKey(seed)
        if (phase == CognitivePhase.REFERENCE) {
            Text("Recuerda este símbolo inicial", style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f).fillMaxWidth().drawWithContent { drawContent(); stimulusDrawn(SystemClock.uptimeMillis()) }, contentAlignment = Alignment.Center) {
                BatterySymbol(trial.referenceSymbol ?: 0, Modifier.size(72.dp))
            }
        }
        else if (phase == CognitivePhase.FIXATION) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("+", style = MaterialTheme.typography.displayMedium, color = Ink)
        }
        else if (phase == CognitivePhase.STIMULUS && stimulusVisible) {
            Stimulus(type, trial, Modifier.fillMaxWidth().weight(1f).testTag("cognitive-stimulus").drawWithContent {
                drawContent()
                if (type != CognitiveTestType.VISUAL_SEARCH) stimulusDrawn(SystemClock.uptimeMillis())
            })
        } else Spacer(Modifier.weight(1f))
        when (type) {
            CognitiveTestType.FLANKER -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResponseAction("Izquierda", gate, enabled && phase == CognitivePhase.STIMULUS, Modifier.weight(1f), responseKey = trial.index) { answer("LEFT", it) }
                ResponseAction("Derecha", gate, enabled && phase == CognitivePhase.STIMULUS, Modifier.weight(1f), responseKey = trial.index) { answer("RIGHT", it) }
            }
            CognitiveTestType.GO_NO_GO -> ResponseAction("Responder", gate, enabled && phase in setOf(CognitivePhase.FIXATION, CognitivePhase.STIMULUS), Modifier.fillMaxWidth(), responseKey = trial.index) { answer("RESPOND", it) }
            CognitiveTestType.VISUAL_SEARCH -> if (phase == CognitivePhase.STIMULUS) SearchGrid(trial.targetIndex ?: 0, enabled, gate, Modifier.drawWithContent {
                drawContent()
                stimulusDrawn(SystemClock.uptimeMillis())
            }, responseKey = trial.index) { cell, time -> answer(cell.toString(), time) }
            CognitiveTestType.DSST -> AssociationControls(gate, enabled && phase == CognitivePhase.STIMULUS, trial.index, answer)
            CognitiveTestType.N_BACK -> MemoryControls(gate, enabled && phase == CognitivePhase.STIMULUS, trial.index, answer)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Stimulus(type: CognitiveTestType, trial: CognitiveTrial, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (type) {
            CognitiveTestType.FLANKER -> {
                val center = if (trial.expectedResponse == "LEFT") "←" else "→"
                val side = if (trial.stimulusCode.startsWith("CONGRUENT")) center else if (center == "←") "→" else "←"
                Text("$side$side$center$side$side", style = MaterialTheme.typography.headlineLarge, color = Ink, textAlign = TextAlign.Center)
            }
            CognitiveTestType.GO_NO_GO -> Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                if (trial.stimulusCode == "GO") Box(Modifier.size(84.dp).background(BrandBlue, CircleShape))
                else Box(Modifier.size(84.dp).background(BrandBlue, RoundedCornerShape(8.dp)))
            }
            CognitiveTestType.VISUAL_SEARCH -> Text("Encuentra la T", style = MaterialTheme.typography.titleLarge, color = Ink)
            CognitiveTestType.DSST, CognitiveTestType.N_BACK -> BatterySymbol(trial.stimulusCode.removePrefix("SYMBOL_").toIntOrNull() ?: 0, Modifier.size(72.dp))
        }
    }
}

@Composable
private fun SearchGrid(target: Int, enabled: Boolean, gate: ContactGate, modifier: Modifier = Modifier, responseKey: Any, onSelect: (Int, Long) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(4) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { column ->
                    val index = row * 4 + column
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).aspectRatio(1f)
                            .semantics { contentDescription = "Casilla ${index + 1}" }
                            .testTag(if (index == target) "visual-target" else "visual-cell-$index")
                            .firstContactInput(gate, enabled, responseKey) { onSelect(index, it) },
                        shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, Separator),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(24.dp)) {
                                val stroke = size.minDimension * 0.13f
                                if (index == target) {
                                    drawLine(Ink, Offset(size.width * 0.12f, size.height * 0.2f), Offset(size.width * 0.88f, size.height * 0.2f), stroke)
                                    drawLine(Ink, Offset(size.width / 2, size.height * 0.2f), Offset(size.width / 2, size.height * 0.88f), stroke)
                                } else {
                                    drawLine(Ink, Offset(size.width * 0.2f, size.height * 0.12f), Offset(size.width * 0.2f, size.height * 0.88f), stroke)
                                    drawLine(Ink, Offset(size.width * 0.2f, size.height * 0.88f), Offset(size.width * 0.88f, size.height * 0.88f), stroke)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CognitiveResultScreen(session: CognitiveTestSession, fromHistory: Boolean, finish: () -> Unit, retry: () -> Unit, returnLabel: String = "Volver a pruebas") {
    ActionPage(scrollKey = "cognitive-result-${session.id}", action = {
        PrimaryAction(if (fromHistory) "Volver al historial" else returnLabel, onClick = finish)
        if (!fromHistory && session.status in setOf(CognitiveSessionStatus.INCOMPLETE, CognitiveSessionStatus.INVALID)) {
            TextButton(onClick = retry, modifier = Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget)) { Text("Repetir actividad") }
        }
    }) {
        SegmentedProgress(if (session.status == CognitiveSessionStatus.COMPLETED) 3 else 2, description = "Resultado")
        DemoLabel()
        Text(session.type.displayName(), style = MaterialTheme.typography.headlineMedium)
        Text(session.status.displayName(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Este resumen describe esta actividad. No determina si estás apto para trabajar.", style = MaterialTheme.typography.bodyMedium, color = MutedInk, modifier = Modifier.fillMaxWidth())
        Text("${session.protocolVersion} · Cálculo ${session.calculationVersion}", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        if (session.status in setOf(CognitiveSessionStatus.INCOMPLETE, CognitiveSessionStatus.INVALID)) {
            InfoCard("ACTIVIDAD INTERRUMPIDA", "${session.interruptionReason.orEmpty()} Se guardaron ${session.trials.count { !it.isPractice }} ${session.type.trialUnit()}.")
        } else if (session.status == CognitiveSessionStatus.COMPLETED && session.calculationVersion == "descriptive-v2") {
            val ordered = BatteryScoring.describe(session.type, emptyList()).map { it.key }
            BatteryMetricCards(session.type, (session.storedMetrics ?: BatteryScoring.describe(session.type, session.trials)).sortedBy { ordered.indexOf(it.key) })
        } else session.metrics?.values?.forEach { (key, value) ->
            val label = session.type.metricLabel(key)
            val isTime = key.contains("Ms")
            val formatted = value?.let {
                when {
                    isTime -> String.format(Locale.getDefault(), "%.0f ms", it)
                    key == "accuracyPercent" -> String.format(Locale.getDefault(), "%.0f%%", it)
                    else -> String.format(Locale.getDefault(), "%.0f", it)
                }
            } ?: "Sin datos"
            DataCard(label, formatted, Modifier.fillMaxWidth().testTag("metric-$key"), metric = true)
        }
        Text("${session.workerName.participantName()} · Turno ${session.shift.label}", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        IconMessage("Guardado en este teléfono", filled = true)
        Text(dev.studios.samimine.ui.components.syncHint(dev.studios.samimine.ui.components.LocalSyncEnabled.current), style = MaterialTheme.typography.bodySmall, color = MutedInk)
        InfoCard("BIENESTAR · ${EducationalContent.VERSION}", EducationalContent.BODY)
    }
}
