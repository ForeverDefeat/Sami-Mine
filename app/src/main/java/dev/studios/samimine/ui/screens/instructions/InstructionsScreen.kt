package dev.studios.samimine.ui.screens.instructions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*

@Composable
fun InstructionsScreen(enabled: Boolean, start: () -> Unit) {
    ActionPage(scrollKey = "pvt-instructions", action = { PrimaryAction("Comenzar prueba", enabled, arrow = true, onClick = start) }) {
        SegmentedProgress(1, description = "Instrucciones · paso 1 de 3")
        DemoLabel()
        Text("Prueba de reacción", style = SamiTextStyles.InstructionTitle)
        Text("Mira la señal y tócala en cuanto aparezca.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        ReferenceCard {
            Instruction(1, "Espera", "Mantén la vista en el centro. La señal aparecerá tras una espera variable de 2 a 5 segundos.")
            Instruction(2, "Toca al ver la señal", "Cuando aparezca el círculo azul, toca el botón de la parte inferior. Tienes hasta 3 segundos.")
            Instruction(3, "Espera a que aparezca", "Si tocas antes de ver el círculo o no alcanzas a tocarlo, la actividad lo tendrá en cuenta.")
        }
        Text("Esta práctica es demostrativa y no sirve para decidir si estás apto para trabajar.", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
        IconMessage("3 señales · Menos de 1 minuto")
    }
}

@Composable
private fun Instruction(number: Int, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(QuietSurface), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MutedInk)
        }
    }
}
