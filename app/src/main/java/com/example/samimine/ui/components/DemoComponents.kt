package com.example.samimine.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.samimine.domain.model.EvaluationStatus
import com.example.samimine.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun Page(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SamiDimens.Page),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MutedInk)
        content()
    }
}

/** Scrollable reading content with a reachable action area anchored above system navigation. */
@Composable
fun ActionPage(
    modifier: Modifier = Modifier,
    scrollKey: Any = Unit,
    action: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = androidx.compose.runtime.remember(scrollKey) { ScrollState(0) }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(SamiDimens.Page).testTag("action-page-content"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
        Surface(color = PageBackground, shadowElevation = 4.dp) {
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SamiDimens.Page).padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = action,
            )
        }
    }
}

@Composable
fun Brand(large: Boolean = false) {
    if (large) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(76.dp).clip(SamiShapes.Card).background(Ink), contentAlignment = Alignment.Center) {
                Icon(SamiIcons.Helmet, null, Modifier.size(40.dp), tint = Color.White)
            }
            BrandText(true)
            Text("SEGURIDAD PRETURNO", style = SamiTextStyles.SectionLabel, color = AuxiliaryInk)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                Icon(SamiIcons.Helmet, null, Modifier.size(24.dp), tint = Color.White)
            }
            BrandText(false)
        }
    }
}

@Composable
private fun BrandText(large: Boolean) {
    Text(buildAnnotatedString {
        withStyle(SpanStyle(color = Ink)) { append("Sami") }
        withStyle(SpanStyle(color = BrandBlue)) { append("Mine") }
    }, style = if (large) SamiTextStyles.BrandLarge else SamiTextStyles.BrandCompact)
}

@Composable
fun DemoLabel(dark: Boolean = false) {
    Text("Modo demostración", style = MaterialTheme.typography.bodySmall, color = if (dark) TestRing else AuxiliaryInk)
}

@Composable
fun LocalBadge() {
    Surface(shape = SamiShapes.Capsule, color = Separator) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(SamiIcons.Clock, null, Modifier.size(16.dp), tint = BrandShadow)
            Text("GUARDADO EN EL TELÉFONO", style = MaterialTheme.typography.bodySmall, color = BrandShadow)
        }
    }
}

@Composable
fun BackAction(text: String = "Volver", dark: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.heightIn(min = SamiDimens.TouchTarget),
        colors = ButtonDefaults.textButtonColors(contentColor = if (dark) TestRing else BrandShadow)) {
        Icon(SamiIcons.ArrowLeft, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = SamiTextStyles.Button)
    }
}

@Composable
fun ReferenceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = SamiShapes.Card, color = Color.White, border = BorderStroke(1.dp, Separator)) {
        Column(Modifier.padding(SamiDimens.CardPadding), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun InfoCard(title: String, text: String, modifier: Modifier = Modifier) {
    ReferenceCard(modifier) {
        Text(title, style = SamiTextStyles.SectionLabel, color = AuxiliaryInk)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MutedInk)
    }
}

@Composable
fun DataCard(label: String, value: String, modifier: Modifier = Modifier, metric: Boolean = false) {
    Surface(modifier, shape = SamiShapes.DataCard, color = Color.White, border = BorderStroke(1.dp, Separator)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AuxiliaryInk)
            Text(value, style = if (metric) SamiTextStyles.Metric else SamiTextStyles.DataValue)
        }
    }
}

@Composable
fun AdaptiveDataPair(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String, metric: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        if (maxWidth < 300.dp || fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DataCard(firstLabel, firstValue, Modifier.fillMaxWidth(), metric)
                DataCard(secondLabel, secondValue, Modifier.fillMaxWidth(), metric)
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DataCard(firstLabel, firstValue, Modifier.weight(1f).fillMaxHeight(), metric)
                DataCard(secondLabel, secondValue, Modifier.weight(1f).fillMaxHeight(), metric)
            }
        }
    }
}

@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, arrow: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = SamiDimens.ButtonHeight).clip(SamiShapes.Button)
        .background(if (enabled) BrandShadow else Separator)) {
        Button(onClick, Modifier.fillMaxWidth().padding(bottom = SamiDimens.Relief).heightIn(min = 60.dp),
            enabled = enabled, shape = SamiShapes.Button,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, disabledContainerColor = Separator,
                disabledContentColor = MutedInk)) {
            Text(text, style = SamiTextStyles.Button, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            if (arrow) {
                Spacer(Modifier.width(12.dp))
                Icon(SamiIcons.ArrowRight, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SegmentedProgress(active: Int, dark: Boolean = false, description: String) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(SamiDimens.SmallGap)) {
        repeat(3) { index ->
            Box(Modifier.weight(1f).height(SamiDimens.ProgressHeight).clip(SamiShapes.Capsule)
                .background(if (index < active) { if (dark) TestSignal else BrandBlue } else { if (dark) TestTrack else Separator }))
        }
    }
}

@Composable
fun IconMessage(text: String, icon: ImageVector = SamiIcons.Clock, filled: Boolean = false) {
    Surface(color = if (filled) QuietSurface else Color.Transparent, shape = SamiShapes.DataCard, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 12.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Icon(icon, null, Modifier.size(16.dp), tint = AuxiliaryInk)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
    }
}

fun formatDate(millis: Long): String = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

fun EvaluationStatus.label() = when (this) {
    EvaluationStatus.COMPLETED -> "Demostración completada"
    EvaluationStatus.INTERRUPTED -> "Demostración interrumpida"
    EvaluationStatus.IN_PROGRESS -> "En curso"
}
