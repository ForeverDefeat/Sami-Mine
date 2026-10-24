package dev.studios.samimine.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.studios.samimine.domain.model.EducationalContent
import dev.studios.samimine.ui.components.ReferenceCard

/** General guidance, independent of scores. Individual interpretation belongs to occupational health. */
@Composable
fun RecommendationsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Recomendaciones", style = MaterialTheme.typography.headlineMedium)
        Text("Orientación general para acompañar tu evaluación. El personal de salud ocupacional revisa tus resultados y define las indicaciones para tu caso.",
            style = MaterialTheme.typography.bodyLarge)
        RecommendationCard("Antes de las pruebas", "Realiza las actividades en el momento indicado para tu evaluación. Lee las instrucciones y busca un lugar que te permita responder sin interrupciones.")
        RecommendationCard("Durante la evaluación", "Sigue las instrucciones de cada actividad. Si necesitas detenerte, pausa la batería; los pasos terminados se conservan y las pruebas interrumpidas se repiten desde el inicio.")
        RecommendationCard("Después de las pruebas", "Consulta tu historial y el estado de envío. Comparte tus dudas sobre los resultados con el personal de salud ocupacional que realiza el seguimiento.")
        RecommendationCard("Bienestar durante el turno", EducationalContent.BODY)
    }
}

@Composable
private fun RecommendationCard(title: String, body: String) {
    ReferenceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
