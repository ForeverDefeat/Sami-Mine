package dev.studios.samimine.app.navigation

enum class Destination { IDENTIFICATION, SHIFT, HOME, TESTS, QUESTIONNAIRES, INSTRUCTIONS, PVT, RESULT, HISTORY, COGNITIVE_INSTRUCTIONS, COGNITIVE_PRACTICE, COGNITIVE_READY, COGNITIVE_RUN, COGNITIVE_RESULT, QUESTIONNAIRE, SELF_REPORT_RESULT, ASSESSMENT_RESULT }


enum class WorkerTab(val label: String, val destination: Destination) {
    HOME("Inicio", Destination.HOME), TESTS("Pruebas", Destination.TESTS),
    QUESTIONNAIRES("Cuestionarios", Destination.QUESTIONNAIRES), HISTORY("Historial", Destination.HISTORY);

    val returnLabel: String get() = when (this) {
        HOME -> "Volver al inicio"
        TESTS -> "Volver a pruebas"
        QUESTIONNAIRES -> "Volver a cuestionarios"
        HISTORY -> "Volver al historial"
    }
}

val Destination.isWorkerRoot: Boolean get() = WorkerTab.entries.any { it.destination == this }
val Destination.showsWorkerNavigation: Boolean get() = isWorkerRoot || this in setOf(
    Destination.INSTRUCTIONS, Destination.COGNITIVE_INSTRUCTIONS, Destination.RESULT,
    Destination.COGNITIVE_RESULT, Destination.SELF_REPORT_RESULT, Destination.ASSESSMENT_RESULT
)
