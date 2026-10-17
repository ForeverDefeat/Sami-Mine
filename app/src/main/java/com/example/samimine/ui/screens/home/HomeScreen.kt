package com.example.samimine.ui.screens.home

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
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(worker: Worker, shift: Shift, start: () -> Unit, history: () -> Unit, changeWorker: () -> Unit, chooseTest: (CognitiveTestType) -> Unit = {}) {
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
                    Text("ACTIVIDAD DISPONIBLE", style = SamiTextStyles.SectionLabel, color = BrandBlue)
                    Text("Práctica de atención", style = MaterialTheme.typography.headlineSmall)
                    Text("Una práctica breve para observar tu atención y tiempo de respuesta.", style = MaterialTheme.typography.bodyLarge, color = MutedInk,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    PrimaryAction("Iniciar prueba de reacción", arrow = true, onClick = start)
                }
            }
            Text("Otras pruebas de atención", style = MaterialTheme.typography.titleLarge)
            Text("También puedes probar estas actividades por separado.", style = MaterialTheme.typography.bodyMedium, color = MutedInk)
            listOf(
                CognitiveTestType.FLANKER to "Responde según la flecha del centro",
                CognitiveTestType.GO_NO_GO to "Toca ante el círculo; espera ante el cuadrado",
                CognitiveTestType.VISUAL_SEARCH to "Encuentra la letra T entre varias L",
            ).forEach { (type, description) ->
                OutlinedButton(onClick = { chooseTest(type) }, modifier = Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget), shape = SamiShapes.Button) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(type.displayName(), style = SamiTextStyles.Button)
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                    }
                }
            }
            IconMessage("Se guardará en este dispositivo")
            OutlinedButton(history, Modifier.fillMaxWidth().heightIn(min = SamiDimens.TouchTarget), shape = SamiShapes.Button) { Text("Ver mi historial", style = SamiTextStyles.Button) }
            TextButton(changeWorker, Modifier.heightIn(min = SamiDimens.TouchTarget)) { Text("Cambiar perfil", style = SamiTextStyles.Button) }
            Text("Es una demostración, no una evaluación laboral.", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        }
    }
}
