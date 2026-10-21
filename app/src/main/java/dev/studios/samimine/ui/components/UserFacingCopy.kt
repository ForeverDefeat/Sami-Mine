package dev.studios.samimine.ui.components

import dev.studios.samimine.domain.model.CognitiveSessionStatus
import dev.studios.samimine.domain.model.CognitiveTestType

fun String.participantName(): String = substringBefore(" ·")

/** Spanish labels for participants; domain identifiers and stored protocol names stay unchanged. */
fun CognitiveTestType.displayName(): String = when (this) {
    CognitiveTestType.FLANKER -> "Flecha central"
    CognitiveTestType.GO_NO_GO -> "Responder o esperar"
    CognitiveTestType.VISUAL_SEARCH -> "Busca la T"
    CognitiveTestType.DSST -> "Símbolos y dígitos"
    CognitiveTestType.N_BACK -> "Recuerda el anterior"
}

fun CognitiveTestType.trialUnit(): String = when (this) {
    CognitiveTestType.FLANKER -> "series"
    CognitiveTestType.GO_NO_GO -> "figuras"
    CognitiveTestType.VISUAL_SEARCH -> "tableros"
    CognitiveTestType.DSST -> "asociaciones"
    CognitiveTestType.N_BACK -> "comparaciones"
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
    "validTrials" -> "Ensayos registrados"
    "medianCorrectRtMs" -> "Mediana de respuesta correcta"
    "medianDetectionRtMs" -> "Mediana para encontrar la T"
    "accuracyCongruent" -> "Precisión con flechas iguales"
    "accuracyIncongruent" -> "Precisión con flechas distintas"
    "congruentMedianRtMs" -> "Mediana con flechas iguales"
    "incongruentMedianRtMs" -> "Mediana con flechas distintas"
    "flankerCostMs" -> "Diferencia de medianas · Exploratoria"
    "omissionRate" -> "Proporción sin respuesta"
    "wrongDirectionRate" -> "Direcciones incorrectas"
    "goHitRate" -> "Aciertos ante el círculo"
    "goOmissionRate" -> "Círculos sin respuesta"
    "noGoCommissionRate" -> "Toques ante el cuadrado"
    "noGoCorrectRejectionRate" -> "Cuadrados sin tocar"
    "medianGoRtMs" -> "Mediana de respuesta al círculo"
    "nGo" -> "Círculos registrados"
    "nNoGo" -> "Cuadrados registrados"
    "wrongCellRate" -> "Casillas incorrectas"
    "timeoutRate" -> "Tableros sin respuesta"
    "attempted" -> "Asociaciones intentadas"
    "correctPerMinute" -> "Asociaciones correctas por minuto"
    "hitRate" -> "Coincidencias identificadas"
    "falseAlarmRate" -> "Coincidencias indicadas por error"
    "medianHitRtMs" -> "Mediana de coincidencias acertadas"
    "falseAlarms" -> "Falsas coincidencias"
    "misses" -> "Coincidencias respondidas como diferentes"
    "nTargets" -> "Coincidencias presentadas"
    "nNonTargets" -> "Diferencias presentadas"
    else -> "Resultado"
}

fun CognitiveSessionStatus.displayName(): String = when (this) {
    CognitiveSessionStatus.COMPLETED -> "Demostración completada"
    CognitiveSessionStatus.INCOMPLETE -> "Demostración interrumpida"
    CognitiveSessionStatus.IN_PROGRESS -> "En curso"
    CognitiveSessionStatus.INVALID -> "No se pudo completar"
}
