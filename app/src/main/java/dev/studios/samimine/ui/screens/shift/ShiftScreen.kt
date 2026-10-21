package dev.studios.samimine.ui.screens.shift

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*
import dev.studios.samimine.ui.screens.home.WorkerGrid

@Composable
fun ShiftScreen(worker: Worker, select: (Shift) -> Unit) {
    Page("Selecciona tu turno", worker.name.participantName()) {
        DemoLabel()
        InfoCard("ANTES DE COMENZAR", "Elige tu turno. La fecha y la hora se guardarán al iniciar la actividad.")
        WorkerGrid(Shift.entries) { shift, modifier ->
            ReferenceCard(modifier, contentPadding = 12.dp) {
                Icon(SamiIcons.Clock, null, Modifier.size(24.dp), tint = BrandBlue)
                Text(shift.label, style = SamiTextStyles.DataValue)
                PrimaryAction("Turno ${shift.label}", arrow = false, compact = true) { select(shift) }
            }
        }
    }
}
