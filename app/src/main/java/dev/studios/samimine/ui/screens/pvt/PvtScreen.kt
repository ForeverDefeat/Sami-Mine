package dev.studios.samimine.ui.screens.pvt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.studios.samimine.app.PvtPhase
import dev.studios.samimine.ui.components.*
import dev.studios.samimine.ui.theme.*

/**
 * Dibuja la fase PVT y envía primer contacto/dibujo sin programar temporizadores propios.
 * El ViewModel fija el inicio una sola vez aunque Canvas invoque drawn en varios redibujos.
 * PVT consulta su reloj al responder; no utiliza el uptime del evento que entrega el control.
 */
@Composable
fun PvtScreen(phase: PvtPhase, index: Int, total: Int, enabled: Boolean, drawn: () -> Unit, respond: () -> Unit, continueTest: () -> Unit = {}) {
    val gate = remember { ContactGate() }
    val currentRespond by rememberUpdatedState(respond)
    val currentDrawn by rememberUpdatedState(drawn)
    val active = enabled && (phase == PvtPhase.WAITING || phase == PvtPhase.SIGNAL)
    val bolt = remember { Path().apply {
        moveTo(14f, 2f); lineTo(4f, 14f); lineTo(11f, 14f)
        lineTo(10f, 22f); lineTo(20f, 10f); lineTo(13f, 10f); close()
    } }
    Column(Modifier.fillMaxSize().observeContacts(gate).background(TestBackground).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PRUEBA DE REACCIÓN", Modifier.weight(1f), color = TestRing, style = MaterialTheme.typography.titleSmall)
            Surface(shape = SamiShapes.Capsule, color = Color.Transparent, border = BorderStroke(1.dp, TestTrack)) {
                Text("$index / $total", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
        DemoLabel(dark = true)
        SegmentedProgress(index, dark = true, description = "Señal $index de $total")
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp).clip(SamiShapes.Panel).background(TestPanel), contentAlignment = Alignment.Center) {
            if (phase == PvtPhase.SIGNAL) {
                Canvas(Modifier.fillMaxSize().testTag("pvt-stimulus").semantics { contentDescription = "Señal azul visible" }) {
                    val factor = minOf(1f, (size.minDimension - 24.dp.toPx()).coerceAtLeast(0f) / 208.dp.toPx())
                    drawCircle(TestSignal.copy(alpha = 0.17f), 104.dp.toPx() * factor)
                    drawCircle(TestRing, 80.dp.toPx() * factor)
                    drawCircle(TestSignal, 68.dp.toPx() * factor)
                    val boltSize = 48.dp.toPx() * factor
                    withTransform({
                        translate(center.x - boltSize / 2f, center.y - boltSize / 2f)
                        scale(boltSize / 24f, boltSize / 24f, pivot = Offset.Zero)
                    }) { drawPath(bolt, Color.White, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                    // One callback draws the full motif and binds the same onset as the previous UI.
                    currentDrawn()
                }
            } else {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SamiIcons.Clock, null, Modifier.size(24.dp), tint = TestRing)
                    Text(when (phase) {
                        PvtPhase.WAITING -> "Espera la señal"
                        PvtPhase.SAVING -> "Guardando respuesta…"
                        PvtPhase.AWAITING_CONTINUE -> "No se registró una respuesta a tiempo"
                        else -> "Prueba interrumpida"
                    }, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (phase == PvtPhase.WAITING) Text("El círculo aparecerá pronto", color = TestRing, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
        }
        if (phase == PvtPhase.AWAITING_CONTINUE) {
            Text("Se guardó como señal sin respuesta. Pulsa para continuar.", color = Color.White, textAlign = TextAlign.Center)
            Button(onClick = continueTest, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (index == total) "Ver resultado" else "Continuar prueba")
            }
            return@Column
        }
        val label = if (phase == PvtPhase.SIGNAL) "TOCAR AHORA" else "ESPERA LA SEÑAL"
        Box(Modifier.fillMaxWidth().heightIn(min = SamiDimens.ResponseHeight).testTag("pvt-response").clip(SamiShapes.Card)
            .background(if (phase == PvtPhase.SIGNAL) BrandShadow else TestTrack)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!active) disabled()
            }
            .firstContactInput(gate, active, index) { currentRespond() }
            .padding(bottom = SamiDimens.Relief)) {
            Surface(Modifier.fillMaxWidth(), shape = SamiShapes.Card,
                color = if (phase == PvtPhase.SIGNAL) TestSignal else TestResponse,
                border = BorderStroke(2.dp, if (phase == PvtPhase.SIGNAL) TestRing else TestTrack)) {
                Column(Modifier.heightIn(min = 112.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    Text(label, color = Color.White, style = SamiTextStyles.Button, textAlign = TextAlign.Center)
                    Text("Toca aquí cuando aparezca el círculo", color = if (phase == PvtPhase.SIGNAL) Color.White else TestRing,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
