package dev.studios.samimine.domain.model

data class ExerciseSource(val citation: String, val url: String, val summary: String)
enum class ExerciseVisual { POSTURE, BREATHING, HANDS_TENSE, HANDS_REST, ARMS_TENSE, ARMS_REST, SHOULDERS_TENSE, SHOULDERS_REST, LEGS_TENSE, LEGS_REST, GROUNDING, LISTENING, LOOK_AROUND, CLOSING }

data class ExerciseStep(val title: String, val instruction: String, val seconds: Int, val canSkip: Boolean = false, val visual: ExerciseVisual = ExerciseVisual.POSTURE)
data class WellbeingExercise(
    val id: String,
    val title: String,
    val purpose: String,
    val preparation: String,
    val evidence: String,
    val sources: List<ExerciseSource>,
    val steps: List<ExerciseStep>,
    val version: String = "1.0",
    val clinicianApproved: Boolean = false,
) {
    val durationSeconds: Int get() = steps.sumOf { it.seconds }
}

/** Educational adaptations. No score input, adherence history or clinical approval is inferred. */
object WellbeingExercises {
    private val balban = ExerciseSource(
        "Balban y colaboradores (2023). Cell Reports Medicine. DOI: 10.1016/j.xcrm.2022.100895",
        "https://pmc.ncbi.nlm.nih.gov/articles/PMC9873947/",
        "Ensayo remoto en adultos: respiración estructurada y meditación de 5 minutos diarios durante 28 días. Evaluó estado de ánimo, ansiedad y variables fisiológicas. No estudió esta app ni seguridad laboral minera.",
    )
    private val toussaint = ExerciseSource(
        "Toussaint y colaboradores (2021). DOI: 10.1155/2021/5924040",
        "https://pubmed.ncbi.nlm.nih.gov/34306146/",
        "Ensayo con 60 estudiantes universitarios sanos: comparó relajación muscular, respiración profunda, imaginación guiada y control, con sesiones de 20 minutos. Evaluó relajación psicológica y fisiológica; no valida una sesión de 5 minutos.",
    )
    private val safety = ExerciseSource(
        "NCCIH / NIH. Relaxation Techniques: What You Need To Know",
        "https://www.nccih.nih.gov/health/relaxation-techniques-what-you-need-to-know",
        "Orientación institucional sobre técnicas de relajación y precauciones. No sustituye atención profesional ni establece un protocolo minero.",
    )
    const val SAFE_PLACE = "Hazlo solo durante una pausa permitida y en un lugar seguro. No lo realices mientras conduces, operas equipos o necesitas vigilar una tarea."
    const val STOP_GUIDANCE = "Si aparece dolor, mareo, falta de aire o malestar, termina la guía. Ante dolor de pecho, dificultad respiratoria intensa, confusión o debilidad súbita, solicita asistencia según el protocolo de tu organización."
    const val LIMITATION = "Estas guías son de autocuidado general. No reemplazan el sueño, las pausas reglamentarias ni la evaluación de Salud Ocupacional. Terminarlas no determina aptitud para trabajar."

    val all = listOf(
        WellbeingExercise(
            "gentle-breathing", "Respiración suave", "Dedica una pausa a respirar con comodidad y soltar la tensión.",
            "Siéntate con apoyo y deja las manos descansar. Respira a tu ritmo, sin retener el aire ni forzar inspiraciones. Si tienes una indicación médica sobre respiración, síguela.",
            "Adaptación educativa de 5 minutos. Los estudios utilizaron técnicas distintas; no se atribuyen sus resultados a esta secuencia de respiración natural.",
            listOf(toussaint, balban, safety), listOf(
                ExerciseStep("Encuentra una postura cómoda", "Apoya los pies y deja descansar las manos. Afloja la mandíbula sin hacer fuerza.", 30, visual = ExerciseVisual.POSTURE),
                ExerciseStep("Nota tu respiración", "Observa cómo entra y sale el aire. No necesitas respirar más hondo ni cambiar el ritmo.", 60, visual = ExerciseVisual.BREATHING),
                ExerciseStep("Respira sin esfuerzo", "Deja salir el aire suavemente. Vuelve a tomar aire cuando tu cuerpo lo pida; no lo retengas.", 90, visual = ExerciseVisual.BREATHING),
                ExerciseStep("Vuelve con calma", "Si te distraes, vuelve a notar una respiración. No hay una velocidad que debas alcanzar.", 90, visual = ExerciseVisual.BREATHING),
                ExerciseStep("Cierra tu pausa", "Respira como de costumbre. Observa el entorno y mueve las manos suavemente si te resulta cómodo.", 30, visual = ExerciseVisual.CLOSING),
            ),
        ),
        WellbeingExercise(
            "muscle-relaxation", "Relajación muscular", "Reconoce la diferencia entre tensión suave y descanso muscular.",
            "Siéntate con apoyo. Usa muy poca fuerza y respira normalmente. Omite cualquier zona con dolor, lesión o restricción médica. No tenses el cuello ni contengas el aire.",
            "Adaptación abreviada de relajación muscular. El ensayo citado usó 20 minutos; esta secuencia de 5 minutos no tiene eficacia equivalente demostrada ni trata lesiones.",
            listOf(toussaint, safety), listOf(
                ExerciseStep("Prepárate sin esfuerzo", "Apoya los pies y las manos. Respira normalmente. Puedes omitir cualquier grupo muscular.", 30, visual = ExerciseVisual.POSTURE),
                ExerciseStep("Manos · tensión suave", "Cierra las manos ligeramente, sin apretar fuerte. Si molesta, omite este paso.", 5, true, visual = ExerciseVisual.HANDS_TENSE),
                ExerciseStep("Manos · suelta", "Abre las manos y déjalas apoyadas. Observa la diferencia sin buscar una sensación concreta.", 45, true, visual = ExerciseVisual.HANDS_REST),
                ExerciseStep("Brazos · tensión suave", "Flexiona un poco los codos y contrae suavemente los brazos, sin levantar peso.", 5, true, visual = ExerciseVisual.ARMS_TENSE),
                ExerciseStep("Brazos · descansa", "Suelta los brazos y apóyalos con comodidad. Mantén la respiración natural.", 45, true, visual = ExerciseVisual.ARMS_REST),
                ExerciseStep("Hombros · tensión suave", "Eleva apenas los hombros, sin forzar ni tensar el cuello.", 5, true, visual = ExerciseVisual.SHOULDERS_TENSE),
                ExerciseStep("Hombros · suelta", "Deja bajar los hombros. Mantén la cabeza en una posición cómoda.", 45, true, visual = ExerciseVisual.SHOULDERS_REST),
                ExerciseStep("Piernas · tensión suave", "Con los pies apoyados, contrae ligeramente los muslos sin mover las articulaciones.", 5, true, visual = ExerciseVisual.LEGS_TENSE),
                ExerciseStep("Piernas · descansa", "Suelta la tensión. Deja que los pies permanezcan apoyados y respira normalmente.", 45, true, visual = ExerciseVisual.LEGS_REST),
                ExerciseStep("Termina con calma", "Permanece con comodidad y observa el entorno. Mueve manos y pies suavemente antes de levantarte.", 70, visual = ExerciseVisual.CLOSING),
            ),
        ),
        WellbeingExercise(
            "mindful-pause", "Atención plena", "Haz una pausa para notar el presente y volver a una cosa a la vez.",
            "Siéntate en un lugar tranquilo y seguro. Mantén los ojos abiertos y la mirada cómoda. Si centrarte en el cuerpo te incomoda, observa un objeto del entorno o termina la guía.",
            "Adaptación educativa de atención plena. El ensayo citado incluyó meditación de 5 minutos diarios durante 28 días, con instrucciones diferentes. No valida esta secuencia ni efectos sobre accidentes o aptitud.",
            listOf(balban, safety), listOf(
                ExerciseStep("Ubícate en el presente", "Con los ojos abiertos, observa un objeto frente a ti y nota dónde estás sentado.", 30, visual = ExerciseVisual.LOOK_AROUND),
                ExerciseStep("Nota los apoyos", "Observa el contacto de tus pies con el suelo y de tu cuerpo con el asiento. No necesitas cambiar nada.", 60, visual = ExerciseVisual.GROUNDING),
                ExerciseStep("Observa una respiración", "Nota el aire al entrar y salir, sin cambiar su ritmo. Puedes mirar un objeto si eso es más cómodo.", 90, visual = ExerciseVisual.BREATHING),
                ExerciseStep("Escucha y vuelve", "Nota los sonidos cercanos. Si aparecen pensamientos, reconócelos y vuelve con calma a un sonido o a tu respiración.", 90, visual = ExerciseVisual.LISTENING),
                ExerciseStep("Reconoce tu entorno", "Amplía la mirada y nota el lugar donde estás. Termina la pausa con movimientos suaves.", 30, visual = ExerciseVisual.CLOSING),
            ),
        ),
    )
}
