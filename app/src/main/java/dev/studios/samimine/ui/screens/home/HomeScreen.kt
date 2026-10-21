package dev.studios.samimine.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(worker: Worker, shift: Shift, start: () -> Unit,
    instruments: List<InstrumentDefinition> = InstrumentCatalog.definitions, startBrief: () -> Unit = {}, enabled: Boolean = true) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(SamiDimens.Page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 320.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Brand(); LocalBadge() }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Brand(); LocalBadge() }
                }
                DemoLabel()
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-PE"))), style = MaterialTheme.typography.bodyMedium, color = AuxiliaryInk)
                Text("Hola, ${worker.name.substringBefore(" ·")}", style = MaterialTheme.typography.headlineMedium)
                Text("Practiquemos la atención por unos minutos.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
            }
        }
        HorizontalDivider(color = Separator)
        Column(Modifier.padding(SamiDimens.Page), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AdaptiveDataPair("TURNO", shift.label, "ÁREA", worker.area)
            ReferenceCard {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(SamiIcons.Bolt, null, Modifier.size(32.dp), tint = BrandBlue)
                    }
                    val briefAvailable = instruments.any { it.id == InstrumentId.KSS && it.available }
                    Text("RECORRIDO BREVE", style = SamiTextStyles.SectionLabel, color = BrandBlue)
                    Text("Somnolencia y atención", style = MaterialTheme.typography.headlineSmall)
                    Text(if (briefAvailable) "Responde sobre tu somnolencia actual y continúa con tres señales de reacción." else "El recorrido KSS → PVT está pendiente de habilitación de KSS. Puedes realizar la prueba de reacción por separado.", style = MaterialTheme.typography.bodyLarge, color = MutedInk,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    PrimaryAction("Iniciar recorrido breve", enabled = enabled && briefAvailable, arrow = true, onClick = startBrief)
                    PrimaryAction("Iniciar prueba de reacción", enabled = enabled, arrow = true, onClick = start)
                }
            }
            IconMessage("Se guardará en este dispositivo")
            Text("Es una demostración, no una evaluación laboral.", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        }
    }
}
