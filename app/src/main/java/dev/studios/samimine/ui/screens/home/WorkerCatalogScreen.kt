package dev.studios.samimine.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*

/** Shared adaptive rows keep reading order and allow unbounded card text height. */
@Composable
fun <T> WorkerGrid(items: List<T>, content: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.25f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { content(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun WorkerCatalogScreen(
    questionnaires: Boolean,
    instruments: List<InstrumentDefinition>,
    enabled: Boolean,
    startPvt: () -> Unit,
    chooseTest: (CognitiveTestType) -> Unit,
    openInstrument: (InstrumentId) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SamiDimens.Page),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(if (questionnaires) "Cuestionarios" else "Pruebas", style = MaterialTheme.typography.headlineMedium)
        DemoLabel()
        Text(if (questionnaires) "Esfuerzo físico y fatiga retrospectiva se consultan en su contexto; no forman parte del recorrido breve."
            else "Elige una actividad para ver sus instrucciones y comenzar.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        val modules = BatteryCatalog.modules(instruments).filter { (it.kind == ModuleKind.QUESTIONNAIRE) == questionnaires }
        WorkerGrid(modules) { module, modifier ->
            val type = CognitiveTestType.entries.firstOrNull { it.name == module.id }
            CatalogCard(
                title = type?.displayName() ?: module.title,
                status = if (!module.available) "Pendiente de habilitación · Ver disponibilidad"
                    else if (type in setOf(CognitiveTestType.DSST, CognitiveTestType.N_BACK)) "Disponible · Demo experimental"
                    else "Disponible · Modo demostración",
                icon = when (module.id) {
                    "PVT" -> SamiIcons.Bolt
                    "FLANKER" -> SamiIcons.ArrowRight
                    "GO_NO_GO" -> SamiIcons.Response
                    "VISUAL_SEARCH" -> SamiIcons.Search
                    "DSST" -> SamiIcons.Grid
                    "N_BACK" -> SamiIcons.Memory
                    else -> SamiIcons.Questionnaire
                }, modifier = modifier, enabled = enabled,
            ) {
                when {
                    questionnaires -> openInstrument(InstrumentId.valueOf(module.id))
                    type != null -> chooseTest(type)
                    else -> startPvt()
                }
            }
        }
        IconMessage("Se guardará en este dispositivo")
    }
}

@Composable
private fun CatalogCard(title: String, status: String, icon: ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 160.dp), shape = SamiShapes.Card,
        border = BorderStroke(1.dp, Separator), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.size(36.dp), tint = BrandBlue)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(status, style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
