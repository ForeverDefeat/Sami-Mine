package com.example.samimine.ui.screens.identification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.samimine.domain.model.Worker
import com.example.samimine.ui.components.*
import com.example.samimine.ui.theme.*

@Composable
fun IdentificationScreen(workers: List<Worker>, enabled: Boolean, select: (Worker) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SamiDimens.Page), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Brand(large = true) }
        Text("ACCESO DE DEMOSTRACIÓN", style = SamiTextStyles.SectionLabel, color = BrandBlue)
        Text("Elige un perfil para\ncontinuar", style = MaterialTheme.typography.headlineMedium)
        Text("Usa uno de estos perfiles de práctica.", style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        DemoLabel()
        workers.forEach { worker ->
            ReferenceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SamiIcons.Person, null, Modifier.size(24.dp), tint = AuxiliaryInk)
                    Column(Modifier.weight(1f)) {
                        Text(worker.name.participantName(), style = MaterialTheme.typography.titleMedium)
                    }
                }
                PrimaryAction("Usar ${worker.name.participantName()}", enabled, arrow = true) { select(worker) }
            }
        }
        Text("Perfiles de práctica · No se necesitan datos reales", style = MaterialTheme.typography.bodySmall, color = AuxiliaryInk)
    }
}
