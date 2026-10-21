package dev.studios.samimine.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Locally authored vectors following the reference's outline language; no original library claimed. */
object SamiIcons {
    private fun outline(name: String, draw: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()

    val Helmet = outline("Casco") {
        moveTo(3f, 17f); lineTo(3f, 20f); lineTo(21f, 20f); lineTo(21f, 17f); close()
        moveTo(5f, 17f); lineTo(5f, 12f); curveTo(5f, 8f, 8f, 5f, 12f, 5f)
        curveTo(16f, 5f, 19f, 8f, 19f, 12f); lineTo(19f, 17f)
        moveTo(9f, 6f); lineTo(9f, 12f); moveTo(15f, 6f); lineTo(15f, 12f)
    }
    val Person = outline("Persona") {
        moveTo(8f, 7f); curveTo(8f, 2f, 16f, 2f, 16f, 7f)
        curveTo(16f, 12f, 8f, 12f, 8f, 7f); close()
        moveTo(4f, 21f); curveTo(4f, 10f, 20f, 10f, 20f, 21f)
    }
    val Lock = outline("Candado") {
        moveTo(7f, 10f); lineTo(7f, 7f); curveTo(7f, 0f, 17f, 0f, 17f, 7f); lineTo(17f, 10f)
        moveTo(4f, 10f); lineTo(20f, 10f); lineTo(20f, 21f); lineTo(4f, 21f); close()
        moveTo(12f, 14f); lineTo(12f, 17f)
    }
    val ArrowRight = outline("Flecha derecha") {
        moveTo(5f, 12f); lineTo(19f, 12f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }
    val ArrowLeft = outline("Flecha izquierda") {
        moveTo(19f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
    val Clock = outline("Reloj") {
        moveTo(21f, 12f); curveTo(21f, 24f, 3f, 24f, 3f, 12f)
        curveTo(3f, 0f, 21f, 0f, 21f, 12f); close()
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f)
    }
    val Wifi = outline("Wi-Fi") {
        moveTo(3f, 8f); curveTo(8f, 3f, 16f, 3f, 21f, 8f)
        moveTo(6f, 12f); curveTo(10f, 8f, 14f, 8f, 18f, 12f)
        moveTo(9f, 16f); curveTo(11f, 14f, 13f, 14f, 15f, 16f)
        moveTo(12f, 20f); lineTo(12.01f, 20f)
    }
    val Bolt = outline("Rayo") {
        moveTo(14f, 2f); lineTo(4f, 14f); lineTo(11f, 14f)
        lineTo(10f, 22f); lineTo(20f, 10f); lineTo(13f, 10f); close()
    }
    val Home = outline("Inicio") {
        moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
        moveTo(5f, 10f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 10f)
        moveTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f)
    }
    val Grid = outline("Pruebas") {
        moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 10f); lineTo(3f, 10f); close()
        moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(14f, 10f); close()
        moveTo(3f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(3f, 21f); close()
        moveTo(14f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); close()
    }
    val Questionnaire = outline("Cuestionario") {
        moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        moveTo(9f, 8f); lineTo(15f, 8f); moveTo(9f, 12f); lineTo(15f, 12f)
        moveTo(9f, 16f); lineTo(13f, 16f)
    }
    val Search = outline("Buscar") {
        moveTo(17f, 10f); curveTo(17f, 19f, 3f, 19f, 3f, 10f)
        curveTo(3f, 1f, 17f, 1f, 17f, 10f); close()
        moveTo(15f, 15f); lineTo(21f, 21f)
    }
    val Response = outline("Responder o esperar") {
        moveTo(11f, 7f); curveTo(11f, 14f, 1f, 14f, 1f, 7f)
        curveTo(1f, 0f, 11f, 0f, 11f, 7f); close()
        moveTo(14f, 13f); lineTo(22f, 13f); lineTo(22f, 21f); lineTo(14f, 21f); close()
    }
    val Memory = outline("Memoria") {
        moveTo(3f, 3f); lineTo(15f, 3f); lineTo(15f, 15f); lineTo(3f, 15f); close()
        moveTo(9f, 18f); lineTo(9f, 21f); lineTo(21f, 21f); lineTo(21f, 9f); lineTo(18f, 9f)
    }
    // Catalog only: the reference uses this in ALERTA, which is ambiguous. Not a demo result icon.
    val Check = outline("Verificación de referencia") {
        moveTo(5f, 12f); lineTo(10f, 17f); lineTo(20f, 7f)
    }
}
