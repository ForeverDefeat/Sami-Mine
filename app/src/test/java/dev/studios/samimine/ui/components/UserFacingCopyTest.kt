package dev.studios.samimine.ui.components

import dev.studios.samimine.domain.model.CognitiveSessionStatus
import dev.studios.samimine.domain.model.CognitiveTestType
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFacingCopyTest {
    @Test fun cognitiveTestsUsePlainSpanishNamesWithoutChangingDomainNames() {
        assertEquals("Flecha central", CognitiveTestType.FLANKER.displayName())
        assertEquals("Responder o esperar", CognitiveTestType.GO_NO_GO.displayName())
        assertEquals("Busca la T", CognitiveTestType.VISUAL_SEARCH.displayName())
        assertEquals("Flanker", CognitiveTestType.FLANKER.label)
        assertEquals("GO/NO-GO", CognitiveTestType.GO_NO_GO.label)
    }

    @Test fun metricsAndStatusesArePresentedInParticipantLanguage() {
        assertEquals("Toques ante el cuadrado", CognitiveTestType.GO_NO_GO.metricLabel("noGoCommissions"))
        assertEquals("Tiempo medio de respuesta", CognitiveTestType.GO_NO_GO.metricLabel("meanGoRtMs"))
        assertEquals("Letras T encontradas", CognitiveTestType.VISUAL_SEARCH.metricLabel("found"))
        assertEquals("Demostración interrumpida", CognitiveSessionStatus.INCOMPLETE.displayName())
        assertEquals("series", CognitiveTestType.FLANKER.trialUnit())
        assertEquals("figuras", CognitiveTestType.GO_NO_GO.trialUnit())
    }
}
