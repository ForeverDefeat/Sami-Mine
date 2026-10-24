package dev.studios.samimine.domain.model

/** Values are descriptive; null means no eligible observations. Rates use percentages. */
data class MetricResult(val key: String, val value: Double?, val unit: String, val denominator: Int?, val methodVersion: String = "descriptive-v2")

object BatteryScoring {
    fun median(values: List<Double>): Double? = values.sorted().takeIf { it.isNotEmpty() }?.let {
        if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2
    }

    fun describe(type: CognitiveTestType, all: List<CognitiveTrialRecord>): List<MetricResult> {
        val rows = all.filterNot { it.isPractice }
        fun subset(code: String) = rows.filter { it.stimulusCode.startsWith(code) }
        fun count(key: String, items: List<CognitiveTrialRecord>) = MetricResult(key, items.size.toDouble(), "count", null)
        fun rate(key: String, items: List<CognitiveTrialRecord>, outcome: String) = MetricResult(key,
            items.takeIf { it.isNotEmpty() }?.let { list -> 100.0 * list.count { it.outcomeCode == outcome } / list.size }, "%", items.size)
        fun median(key: String, items: List<CognitiveTrialRecord>) = MetricResult(key,
            median(items.mapNotNull { it.reactionTimeMillis?.toDouble() }), "ms", items.count { it.reactionTimeMillis != null })
        val hits = rows.filter { it.outcomeCode in setOf("HIT", "GO_HIT", "NBACK_HIT") }
        val common = listOf(count("validTrials", rows), count("correct", hits), count("errors", rows.filter { it.outcomeCode == "ERROR" }),
            count("omissions", rows.filter { it.outcomeCode == "OMISSION" }))
        return when (type) {
            CognitiveTestType.FLANKER -> {
                val congruent = subset("CONGRUENT"); val incongruent = subset("INCONGRUENT")
                val c = congruent.filter { it.outcomeCode == "HIT" }; val i = incongruent.filter { it.outcomeCode == "HIT" }
                val cm = median("congruentMedianRtMs", c); val im = median("incongruentMedianRtMs", i)
                listOf(rate("accuracyPercent", rows, "HIT"), median("medianCorrectRtMs", hits)) + common + listOf(
                    rate("accuracyCongruent", congruent, "HIT"), rate("accuracyIncongruent", incongruent, "HIT"), cm, im,
                    MetricResult("flankerCostMs", if (c.size >= 3 && i.size >= 3 && cm.value != null && im.value != null) im.value - cm.value else null,
                        "ms", minOf(c.size, i.size)), rate("omissionRate", rows, "OMISSION"), rate("wrongDirectionRate", rows, "ERROR"))
            }
            CognitiveTestType.GO_NO_GO -> {
                val go = rows.filter { it.stimulusCode == "GO" }; val noGo = rows.filter { it.stimulusCode == "NO_GO" }
                listOf(rate("goHitRate", go, "GO_HIT"), median("medianGoRtMs", hits),
                    rate("goOmissionRate", go, "GO_OMISSION"), rate("noGoCommissionRate", noGo, "NO_GO_COMMISSION"),
                    rate("noGoCorrectRejectionRate", noGo, "NO_GO_CORRECT_REJECTION"), count("nGo", go), count("nNoGo", noGo),
                    count("goHits", go.filter { it.outcomeCode == "GO_HIT" }), count("goOmissions", go.filter { it.outcomeCode == "GO_OMISSION" }),
                    count("noGoCommissions", noGo.filter { it.outcomeCode == "NO_GO_COMMISSION" }), count("correctInhibitions", noGo.filter { it.outcomeCode == "NO_GO_CORRECT_REJECTION" }),
                    count("anticipations", rows.filter { it.outcomeCode == "ANTICIPATION" }), count("validTrials", rows))
            }
            CognitiveTestType.VISUAL_SEARCH -> listOf(rate("accuracyPercent", rows, "HIT"), median("medianDetectionRtMs", hits)) + common +
                listOf(count("found", hits), rate("wrongCellRate", rows, "ERROR"), rate("timeoutRate", rows, "OMISSION"))
            CognitiveTestType.DSST -> listOf(count("correct", hits), rate("accuracyPercent", rows, "HIT"), count("attempted", rows),
                count("errors", rows.filter { it.outcomeCode == "ERROR" }), MetricResult("correctPerMinute", hits.size.toDouble(), "count/min", rows.size))
            CognitiveTestType.N_BACK -> {
                val targets = rows.filter { it.expectedResponse == "SAME" }; val others = rows.filter { it.expectedResponse == "DIFFERENT" }
                listOf(rate("hitRate", targets, "NBACK_HIT"), median("medianHitRtMs", rows.filter { it.outcomeCode == "NBACK_HIT" }),
                    rate("falseAlarmRate", others, "FALSE_ALARM"), rate("omissionRate", rows, "OMISSION"),
                    count("correct", rows.filter { it.outcomeCode in setOf("NBACK_HIT", "CORRECT_REJECTION") }),
                    count("falseAlarms", rows.filter { it.outcomeCode == "FALSE_ALARM" }), count("misses", rows.filter { it.outcomeCode == "MISS" }),
                    count("omissions", rows.filter { it.outcomeCode == "OMISSION" }), count("nTargets", targets), count("nNonTargets", others))
            }
        }
    }

    fun calculate(type: CognitiveTestType, all: List<CognitiveTrialRecord>) = CognitiveMetrics(describe(type, all).associate { it.key to it.value })
}

enum class InstrumentId(val label: String) { KSS("Somnolencia actual · KSS"), BORG("Esfuerzo físico · Borg 6–20"), CHALDER("Fatiga retrospectiva · Chalder") }
data class AnswerOption(val code: String, val label: String, val ordinal: Int)
data class InstrumentQuestion(val id: String, val text: String, val options: List<AnswerOption>)
data class InstrumentDefinition(
    val id: InstrumentId, val version: String, val language: String, val instructions: String,
    val recallPeriod: String, val questions: List<InstrumentQuestion>, val authorized: Boolean,
    val permissionReference: String?, val approvalReference: String?,
    val scoringMethod: String = "ORDINAL_ONLY",
) {
    val available: Boolean get() = authorized && language == "es-PE" && version.isNotBlank() &&
        !permissionReference.isNullOrBlank() && !approvalReference.isNullOrBlank() && instructions.isNotBlank() && recallPeriod.isNotBlank() &&
        questions.isNotEmpty() && questions.map { it.id }.distinct().size == questions.size &&
        questions.all { it.text.isNotBlank() && it.options.size >= 2 && it.options.all { o -> o.label.isNotBlank() } && it.options.map { o -> o.code }.distinct().size == it.options.size } &&
        (id != InstrumentId.BORG || questions.size == 1 && questions.first().options.map { it.ordinal } == (6..20).toList()) &&
        (id != InstrumentId.KSS || questions.size == 1 && questions.first().options.map { it.ordinal } == (1..9).toList()) &&
        (id != InstrumentId.CHALDER || scoringMethod in setOf("AUTHORIZED_ORDINAL_SUM", "ORDINAL_ONLY"))
}

/** Borg is retained in InstrumentId for historical records, but is outside the current study scope. */
object InstrumentCatalog {
    val definitions: List<InstrumentDefinition> = InstrumentId.entries.filterNot { it == InstrumentId.BORG }.map {
        InstrumentDefinition(it, "", "es-PE", "", "", emptyList(), false, null, null)
    }
}

enum class ModuleKind { COGNITIVE, QUESTIONNAIRE }
data class BatteryModule(val id: String, val title: String, val description: String, val kind: ModuleKind, val protocolVersion: String?, val available: Boolean)
object BatteryCatalog {
    fun modules(instruments: List<InstrumentDefinition> = InstrumentCatalog.definitions): List<BatteryModule> =
        listOf(BatteryModule("PVT", "Prueba de reacción", "Tres señales para practicar tu atención", ModuleKind.COGNITIVE, "demo-pvt-v1", true)) +
        CognitiveTestType.entries.map { BatteryModule(it.name, it.label, when (it) {
            CognitiveTestType.FLANKER -> "Responde según la flecha del centro"
            CognitiveTestType.GO_NO_GO -> "Toca ante el círculo; espera ante el cuadrado"
            CognitiveTestType.VISUAL_SEARCH -> "Encuentra la T entre las L"
            CognitiveTestType.DSST -> "Asocia seis símbolos con sus dígitos durante 60 segundos"
            CognitiveTestType.N_BACK -> "Compara cada símbolo con el anterior"
        }, ModuleKind.COGNITIVE, it.version, true) } + instruments.filterNot { it.id == InstrumentId.BORG }.map {
            BatteryModule(it.id.name, it.id.label, if (it.available) it.recallPeriod else "Pendiente de versión autorizada en español", ModuleKind.QUESTIONNAIRE, it.version, it.available)
        }
}

data class AssessmentSession(val id: String, val workerId: String, val shift: Shift, val startedAtMillis: Long,
    val status: String = "IN_PROGRESS", val pvtId: String? = null, val finishedAtMillis: Long? = null, val reason: String? = null,
    val bundleVersion: String = "demo-brief-v1")
data class SelfReport(val id: String, val workerId: String, val shift: Shift, val assessmentId: String?, val instrumentId: InstrumentId,
    val instrumentVersion: String, val language: String, val answeredAtMillis: Long, val answers: Map<String, Int>,
    val omitted: Boolean, val context: String?, val moment: String?, val total: Int? = null, val scoringMethod: String = "ORDINAL_ONLY")

object EducationalContent {
    const val VERSION = "general-v1"
    const val BODY = "Sigue las pausas y listas de verificación de tu organización. Si tienes dudas sobre tu bienestar, solicita orientación a Salud Ocupacional. Esta actividad no sustituye una evaluación profesional."
}
