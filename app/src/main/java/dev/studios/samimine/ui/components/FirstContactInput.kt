package dev.studios.samimine.ui.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.studios.samimine.ui.theme.*

/**
 * Compuerta compartida por los objetivos de respuesta de una pantalla.
 * Solo el primer dedo de un gesto nuevo puede reclamarlo y cada clave de ensayo responde una vez.
 * Debe conservarse entre ensayos para que un dedo mantenido no responda al siguiente.
 */
class ContactGate {
    private var first: PointerId? = null
    private var claimed = false
    private var answeredKey: Any? = null

    fun observe(event: PointerEvent) {
        if (first == null && event.changes.any { it.changedToDownIgnoreConsumed() }) {
            first = event.changes.first { it.changedToDownIgnoreConsumed() }.id
            claimed = false
        }
        if (event.changes.none { it.pressed }) first = null
    }

    fun claim(id: PointerId): Boolean {
        if (claimed || first != id) return false
        claimed = true
        return true
    }

    fun claimResponse(key: Any): Boolean {
        if (answeredKey == key) return false
        answeredKey = key
        return true
    }
}

/** Observa antes que los controles; mantener montado durante ensayos y cambios de habilitación. */
fun Modifier.observeContacts(gate: ContactGate) = pointerInput(gate) {
    awaitPointerEventScope {
        while (true) gate.observe(awaitPointerEvent(PointerEventPass.Initial))
    }
}

/**
 * Responde al primer contacto con uptime del evento y consume hasta levantar todos los dedos.
 * Usa callbacks actualizados sin reiniciar pointerInput; responseKey identifica el ensayo.
 * La acción semántica accesible usa uptime actual y comparte el bloqueo por clave.
 */
@Composable
fun Modifier.firstContactInput(gate: ContactGate, enabled: Boolean, responseKey: Any = Unit, answer: (Long) -> Unit): Modifier {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAnswer by rememberUpdatedState(answer)
    val currentKey by rememberUpdatedState(responseKey)
    return this.pointerInput(gate) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val accept = gate.claim(down.id)
            down.consume()
            if (accept && currentEnabled && gate.claimResponse(currentKey)) currentAnswer(down.uptimeMillis)
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }.semantics(mergeDescendants = true) {
        role = Role.Button
        if (!enabled) disabled()
        onClick {
            if (currentEnabled && gate.claimResponse(currentKey)) { currentAnswer(SystemClock.uptimeMillis()); true } else false
        }
    }
}

@Composable
fun ResponseAction(label: String, gate: ContactGate, enabled: Boolean, modifier: Modifier = Modifier, outlined: Boolean = false, responseKey: Any = Unit, answer: (Long) -> Unit) {
    Box(modifier.firstContactInput(gate, enabled, responseKey, answer).heightIn(min = SamiDimens.ButtonHeight)
        .clip(SamiShapes.Button).background(if (outlined) androidx.compose.ui.graphics.Color.Transparent else if (enabled) BrandShadow else Separator)) {
        Surface(
            Modifier.fillMaxWidth().padding(bottom = if (outlined) 0.dp else SamiDimens.Relief),
            shape = SamiShapes.Button,
            border = if (outlined) BorderStroke(1.dp, Separator) else null,
            color = if (outlined) androidx.compose.ui.graphics.Color.Transparent else if (enabled) BrandBlue else Separator,
            contentColor = if (outlined || !enabled) MutedInk else androidx.compose.ui.graphics.Color.White,
        ) {
            Box(Modifier.heightIn(min = 60.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(label, style = SamiTextStyles.Button)
            }
        }
    }
}
