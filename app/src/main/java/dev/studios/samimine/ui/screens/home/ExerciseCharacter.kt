package dev.studios.samimine.ui.screens.home

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.studios.samimine.domain.model.*
import kotlin.math.sin

/** Local vector artwork; the session clock remains the sole source of demonstration time. */
@Composable
internal fun ExerciseCharacter(visual: ExerciseVisual, session: ExerciseSession? = null, compact: Boolean = false, playing: Boolean = session == null || session.status == ExerciseSessionStatus.ACTIVE) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var visible by remember { mutableStateOf(false) }
    fun motionAllowed() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motion by remember { mutableStateOf(motionAllowed()) }
    DisposableEffect(owner, context) {
        val lifecycleObserver = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = motionAllowed() }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver)
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }
    var elapsed by remember(session, visual) { mutableLongStateOf(session?.visualElapsedMillis ?: 0L) }
    val active = playing
    LaunchedEffect(session, visual, visible, resumed, motion, active) {
        if (session != null) elapsed = session.visualElapsedMillis
        if (visible && resumed && motion && active) {
            var last = 0L
            while (true) withInfiniteAnimationFrameMillis { now ->
                elapsed = session?.visualElapsedMillis ?: (elapsed + if (last == 0L) 0L else now - last)
                last = now
            }
        }
    }
    val description = when (visual) {
        ExerciseVisual.POSTURE -> "Persona sentada con pies y manos apoyados"
        ExerciseVisual.BREATHING -> "Persona sentada respirando sin esfuerzo. Respira a tu ritmo"
        ExerciseVisual.HANDS_TENSE -> "Cierra las manos ligeramente"
        ExerciseVisual.HANDS_REST -> "Abre las manos y déjalas apoyadas"
        ExerciseVisual.ARMS_TENSE -> "Flexiona ligeramente los codos"
        ExerciseVisual.ARMS_REST -> "Descansa los brazos sobre las piernas"
        ExerciseVisual.SHOULDERS_TENSE -> "Eleva apenas los hombros, sin mover el cuello"
        ExerciseVisual.SHOULDERS_REST -> "Deja bajar los hombros"
        ExerciseVisual.LEGS_TENSE -> "Contrae suavemente los muslos con los pies apoyados"
        ExerciseVisual.LEGS_REST -> "Descansa las piernas con los pies apoyados"
        ExerciseVisual.GROUNDING -> "Nota los pies en el suelo y el cuerpo en el asiento"
        ExerciseVisual.LISTENING -> "Persona atenta a los sonidos del entorno"
        ExerciseVisual.LOOK_AROUND -> "Persona con ojos abiertos observando su entorno"
        ExerciseVisual.CLOSING -> "Persona sentada moviendo suavemente manos y pies"
    }
    val colors = MaterialTheme.colorScheme
    val phase = if (motion) elapsed / 1000f else 1f
    val wave = ((sin(phase * 1.4f) + 1f) / 2f)
    val tense = visual in setOf(ExerciseVisual.HANDS_TENSE, ExerciseVisual.ARMS_TENSE, ExerciseVisual.SHOULDERS_TENSE, ExerciseVisual.LEGS_TENSE)
    // Tension is introduced gently once, then held until the rest step.
    val effort = if (tense) (phase / 1.5f).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(if (compact) 132.dp else 232.dp)
            .testTag(if (compact) "exercise-preview" else "exercise-character")
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                visible = bounds.bottom > 0 && bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
            }.semantics { contentDescription = description }) {
            val scale = minOf(size.width / 320f, size.height / 250f)
            withTransform({ translate((size.width - 320f * scale) / 2, (size.height - 250f * scale) / 2); scale(scale, scale, Offset.Zero) }) {
                fun line(a: Offset, b: Offset, color: Color, width: Float) = drawLine(color, a, b, width, StrokeCap.Round)
                val skin = Color(0xFFC98F6A)
                val hair = Color(0xFF39302D)
                val shirt = colors.primary
                val trousers = colors.secondary
                drawOval(colors.primaryContainer.copy(alpha = .45f), Offset(55f, 15f), Size(210f, 220f))
                drawOval(colors.outline.copy(alpha = .15f), Offset(70f, 226f), Size(180f, 12f))
                // Chair and planted feet.
                line(Offset(111f, 113f), Offset(111f, 178f), colors.outline, 9f)
                line(Offset(111f, 177f), Offset(207f, 177f), colors.outline, 9f)
                line(Offset(119f, 180f), Offset(112f, 227f), colors.outline, 7f)
                line(Offset(198f, 180f), Offset(207f, 227f), colors.outline, 7f)
                val shoulderY = 105f - if (visual == ExerciseVisual.SHOULDERS_TENSE) 5f * effort else 0f
                line(Offset(145f, 158f), Offset(136f, 189f), trousers, 24f)
                line(Offset(175f, 158f), Offset(184f, 189f), trousers, 24f)
                line(Offset(136f, 189f), Offset(136f, 220f), trousers, 18f)
                line(Offset(184f, 189f), Offset(184f, 220f), trousers, 18f)
                line(Offset(136f, 223f), Offset(119f, 223f), hair, 12f)
                line(Offset(184f, 223f), Offset(201f, 223f), hair, 12f)
                drawRoundRect(shirt, Offset(133f, shoulderY - 8), Size(54f, 68f + (105f - shoulderY)), androidx.compose.ui.geometry.CornerRadius(20f))
                line(Offset(160f, 81f), Offset(160f, 98f), skin, 15f)
                drawOval(hair, Offset(138f, 37f), Size(44f, 52f))
                drawOval(skin, Offset(140f, 46f), Size(40f, 43f))
                drawArc(hair, 190f, 160f, true, Offset(136f, 35f), Size(48f, 35f))
                val gaze = if (visual == ExerciseVisual.LOOK_AROUND) sin(phase * .7f) * 2 else 0f
                drawCircle(hair, 2f, Offset(151f + gaze, 65f)); drawCircle(hair, 2f, Offset(169f + gaze, 65f))
                drawArc(hair, 15f, 150f, false, Offset(154f, 71f), Size(12f, 7f), style = Stroke(1.5f))
                val flex = if (visual == ExerciseVisual.ARMS_TENSE) 25f * effort else 0f
                val closing = if (visual == ExerciseVisual.CLOSING) wave * 4f else 0f
                for (side in listOf(-1f, 1f)) {
                    val elbow = Offset(160f + side * 43f, 141f - flex * .2f)
                    val hand = Offset(160f + side * 24f, 159f - flex - closing)
                    line(Offset(160f + side * 24f, shoulderY), elbow, shirt, 17f)
                    line(elbow, hand, skin, 12f)
                    drawCircle(skin, if (visual == ExerciseVisual.HANDS_TENSE) 7f - effort else 7f, hand)
                    if (visual != ExerciseVisual.HANDS_TENSE) {
                        for (finger in -1..1) line(hand + Offset(finger * 3f, 0f), hand + Offset(finger * 4f, 7f), skin, 2.5f)
                    }
                }
                if (visual == ExerciseVisual.LEGS_TENSE || visual == ExerciseVisual.LEGS_REST || visual == ExerciseVisual.GROUNDING) {
                    val alpha = if (visual == ExerciseVisual.LEGS_TENSE) .25f + effort * .4f else .35f
                    drawOval(colors.tertiary.copy(alpha = alpha), Offset(121f, 169f), Size(29f, 20f), style = Stroke(2f))
                    drawOval(colors.tertiary.copy(alpha = alpha), Offset(170f, 169f), Size(29f, 20f), style = Stroke(2f))
                    if (visual == ExerciseVisual.GROUNDING) line(Offset(109f, 234f), Offset(211f, 234f), colors.tertiary, 3f)
                }
                if (visual == ExerciseVisual.BREATHING) {
                    // Drifting air marks are illustrative, never a paced inhale/exhale cue.
                    for (i in 0..2) {
                        val drift = ((phase * 7f + i * 12f) % 35f)
                        line(Offset(201f + drift, 75f + i * 10), Offset(209f + drift, 72f + i * 10), colors.tertiary.copy(alpha = .5f), 3f)
                    }
                }
                if (visual == ExerciseVisual.LISTENING || visual == ExerciseVisual.LOOK_AROUND) {
                    for (side in listOf(-1f, 1f)) for (i in 0..1) {
                        drawArc(colors.tertiary.copy(alpha = .3f + wave * .3f), if (side > 0) -45f else 135f, 90f, false,
                            Offset(160f + side * (54f + i * 14) - 10, 49f - i * 4), Size(20f, 34f + i * 8), style = Stroke(2.5f))
                    }
                }
            }
        }
        if (!compact && visual == ExerciseVisual.BREATHING) Text("Respira a tu ritmo", style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
}
