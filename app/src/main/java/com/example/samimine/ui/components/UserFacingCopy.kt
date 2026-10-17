package com.example.samimine.ui.components

import com.example.samimine.domain.model.CognitiveSessionStatus
import com.example.samimine.domain.model.CognitiveTestType

fun String.participantName(): String = substringBefore(" ·")

/** Spanish labels for participants; domain identifiers and stored protocol names stay unchanged. */
fun CognitiveTestType.displayName(): String = when (this) {
    CognitiveTestType.FLANKER -> "Flecha central"
    CognitiveTestType.GO_NO_GO -> "Responder o esperar"
    CognitiveTestType.VISUAL_SEARCH -> "Busca la T"
}

fun CognitiveTestType.trialUnit(): String = when (this) {
    CognitiveTestType.FLANKER -> "series"
    CognitiveTestType.GO_NO_GO -> "figuras"
    CognitiveTestType.VISUAL_SEARCH -> "tableros"
}

fun CognitiveTestType.metricLabel(key: String): String = when (key) {
    "accuracyPercent" -> "Respuestas correctas"
    "correct" -> "Aciertos"
    "errors" -> "Respuestas incorrectas"
    "omissions" -> "Sin respuesta"
    "congruentErrors" -> "Errores con flechas iguales"
    "incongruentErrors" -> "Errores con flechas distintas"
    "congruentMeanRtMs" -> "Tiempo medio con flechas iguales"
    "incongruentMeanRtMs" -> "Tiempo medio con flechas distintas"
    "interferenceMs" -> "Diferencia de tiempo entre flechas"
    "meanCorrectRtMs", "meanGoRtMs" -> "Tiempo medio de respuesta"
    "goHits" -> "Aciertos al ver el círculo"
    "goOmissions" -> "Círculos sin respuesta"
    "noGoCommissions" -> "Toques ante el cuadrado"
    "correctInhibitions" -> "Cuadrados sin tocar"
    "anticipations" -> "Toques antes de la señal"
    "found" -> "Letras T encontradas"
    "meanCorrectDetectionMs" -> "Tiempo medio para encontrar la T"
    else -> "Resultado"
}

fun CognitiveSessionStatus.displayName(): String = when (this) {
    CognitiveSessionStatus.COMPLETED -> "Demostración completada"
    CognitiveSessionStatus.INCOMPLETE -> "Demostración interrumpida"
    CognitiveSessionStatus.IN_PROGRESS -> "En curso"
    CognitiveSessionStatus.INVALID -> "No se pudo completar"
}
