package com.example.samimine.ui.screens.shift

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.samimine.domain.model.*
import com.example.samimine.ui.components.*
import com.example.samimine.ui.theme.*

@Composable
fun ShiftScreen(worker: Worker, select: (Shift) -> Unit) {
    Page("Selecciona tu turno", worker.name.participantName()) {
        DemoLabel()
        InfoCard("ANTES DE COMENZAR", "Elige tu turno. La fecha y la hora se guardarán al iniciar la actividad.")
        Shift.entries.forEach { shift ->
            ReferenceCard {
                Icon(SamiIcons.Clock, null, Modifier.size(24.dp), tint = BrandBlue)
                Text(shift.label, style = SamiTextStyles.DataValue)
                PrimaryAction("Turno ${shift.label}", arrow = true) { select(shift) }
            }
        }
    }
}
