package dev.studios.samimine.domain.model

import kotlin.random.Random

enum class CognitiveTestType(val label: String, val version: String) {
    FLANKER("Flanker", "demo-flanker-v2"),
    GO_NO_GO("GO/NO-GO", "demo-gonogo-v2"),
    VISUAL_SEARCH("Búsqueda Visual", "demo-visual-search-v1"),
    DSST("Asociación símbolo–dígito", "experimental-dsst-v1"),
    N_BACK("Memoria 1-back", "experimental-1back-v1"),
}

enum class CognitiveSessionStatus { IN_PROGRESS, COMPLETED, INCOMPLETE, INVALID }
enum class CognitivePhase { INSTRUCTIONS, PRACTICE, READY, REFERENCE, FIXATION, STIMULUS, INTER_TRIAL, COMPLETED, INCOMPLETE }

/** Parámetros demostrativos versionados; forVersion conserva las ventanas de los protocolos v1. */
data class CognitiveTestConfig(
    val type: CognitiveTestType,
    val practiceTrials: Int,
    val scoredTrials: Int,
    val fixationMillis: Long,
    val responseWindowMillis: Long,
    val interTrialMinMillis: Long,
    val interTrialMaxMillis: Long,
    val protocolVersion: String = type.version,
) {
    init {
        require(practiceTrials > 0 && scoredTrials > 0)
        require(fixationMillis > 0 && responseWindowMillis > 0)
        require(interTrialMinMillis >= 0 && interTrialMaxMillis >= interTrialMinMillis)
        require(protocolVersion == type.version || protocolVersion == legacyVersion(type))
    }

    companion object {
        private fun legacyVersion(type: CognitiveTestType) = when (type) {
            CognitiveTestType.FLANKER -> "demo-flanker-v1"
            CognitiveTestType.GO_NO_GO -> "demo-gonogo-v1"
            CognitiveTestType.VISUAL_SEARCH -> "demo-visual-search-v1"
            CognitiveTestType.DSST, CognitiveTestType.N_BACK -> type.version
        }

        fun forVersion(type: CognitiveTestType, version: String): CognitiveTestConfig {
            val current = forType(type)
            if (version == type.version) return current
            require(version == legacyVersion(type)) { "Protocolo desconocido: $version" }
            return current.copy(protocolVersion = version, responseWindowMillis = when (type) {
                CognitiveTestType.FLANKER -> 1_200
                CognitiveTestType.GO_NO_GO -> 800
                CognitiveTestType.VISUAL_SEARCH -> 4_000
                CognitiveTestType.DSST -> 60_000
                CognitiveTestType.N_BACK -> 2_500
            })
        }

        fun forType(type: CognitiveTestType) = when (type) {
            CognitiveTestType.FLANKER -> CognitiveTestConfig(type, 2, 12, 400, 3_000, 400, 700)
            CognitiveTestType.GO_NO_GO -> CognitiveTestConfig(type, 2, 20, 400, 3_000, 300, 700)
            CognitiveTestType.VISUAL_SEARCH -> CognitiveTestConfig(type, 1, 8, 300, 4_000, 400, 400)
            CognitiveTestType.DSST -> CognitiveTestConfig(type, 6, 1, 1, 60_000, 0, 0)
            CognitiveTestType.N_BACK -> CognitiveTestConfig(type, 6, 24, 400, 2_500, 400, 400)
        }
    }
}

/** Generic, auditable trial representation. Codes retain each test's stimulus semantics. */
data class CognitiveTrial(
    val index: Int,
    val isPractice: Boolean,
    val stimulusCode: String,
    val expectedResponse: String?,
    val targetIndex: Int? = null,
    val interTrialDelayMillis: Long? = null,
    val referenceSymbol: Int? = null,
)

/** Registro persistible: marcas uptime y RT en ms; práctica y ausencia de RT usan valores nulos. */
data class CognitiveTrialRecord(
    val index: Int,
    val isPractice: Boolean,
    val stimulusCode: String,
    val expectedResponse: String?,
    val actualResponse: String?,
    val outcomeCode: String,
    val reactionTimeMillis: Long?,
    val onsetUptimeMillis: Long?,
    val responseUptimeMillis: Long?,
    val interTrialDelayMillis: Long? = null,
)

data class CognitiveMetrics(val values: Map<String, Double?>)

/**
 * Sesión auditable con copia del perfil, configuración JSON y semilla.
 * Las fechas son epoch en ms. metrics se deriva de los ensayos solo al estar COMPLETED;
 * las sesiones incompletas no producen un resumen de métricas finales.
 */
data class CognitiveTestSession(
    val id: String,
    val workerId: String,
    val workerName: String,
    val area: String,
    val employment: String,
    val shift: Shift,
    val type: CognitiveTestType,
    val protocolVersion: String,
    val configSnapshot: String,
    val randomSeed: Long,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val status: CognitiveSessionStatus = CognitiveSessionStatus.IN_PROGRESS,
    val interruptionReason: String? = null,
    val trials: List<CognitiveTrialRecord> = emptyList(),
    val calculationVersion: String = "legacy-v1",
    val executionStartedUptimeMillis: Long? = null,
    val executionFinishedUptimeMillis: Long? = null,
    val storedMetrics: List<MetricResult>? = null,
) {
    val metrics: CognitiveMetrics? get() = if (status == CognitiveSessionStatus.COMPLETED) {
        if (storedMetrics != null) CognitiveMetrics(storedMetrics.associate { it.key to it.value })
        else if (calculationVersion == "descriptive-v2") BatteryScoring.calculate(type, trials)
        else CognitiveScoring.calculate(type, trials)
    } else null
}

/** Genera práctica y ensayos puntuables con índices consecutivos y aleatoriedad por semilla. */
object CognitiveTrials {
    fun generate(config: CognitiveTestConfig, seed: Long): List<CognitiveTrial> {
        val random = Random(seed)
        val practice = when (config.type) {
            CognitiveTestType.FLANKER -> listOf(trial("CONGRUENT_LEFT"), trial("INCONGRUENT_RIGHT"))
            CognitiveTestType.GO_NO_GO -> listOf(trial("GO"), trial("NO_GO"))
            CognitiveTestType.VISUAL_SEARCH -> listOf(searchTrial(0))
            CognitiveTestType.DSST -> (0..5).map { dsstTrial(it, seed) }
            CognitiveTestType.N_BACK -> nBack(random, 6, 2)
        }.mapIndexed { index, item -> item.copy(index = index + 1, isPractice = true) }
        val scored = when (config.type) {
            CognitiveTestType.FLANKER -> balancedFlanker(random)
            CognitiveTestType.GO_NO_GO -> balancedGoNoGo(random)
            CognitiveTestType.VISUAL_SEARCH -> balancedSearch(random)
            CognitiveTestType.DSST -> listOf(dsstTrial(6, seed))
            CognitiveTestType.N_BACK -> nBack(random, 24, 8)
        }.mapIndexed { index, item -> item.copy(index = index + practice.size + 1, isPractice = false) }
        return practice + scored.mapIndexed { index, item ->
            val delay = if (index == scored.lastIndex) null else if (config.interTrialMinMillis == config.interTrialMaxMillis)
                config.interTrialMinMillis else random.nextLong(config.interTrialMinMillis, config.interTrialMaxMillis + 1)
            item.copy(interTrialDelayMillis = delay)
        }
    }

    private fun balancedFlanker(random: Random): List<CognitiveTrial> {
        val items = listOf("CONGRUENT_LEFT", "CONGRUENT_RIGHT", "INCONGRUENT_LEFT", "INCONGRUENT_RIGHT").flatMap { code -> List(3) { trial(code) } }.toMutableList()
        items.shuffle(random)
        // Deterministic retry keeps the four-way balance and limits response runs to three.
        repeat(100) {
            if (items.windowed(4).none { group -> group.map { it.expectedResponse }.distinct().size == 1 }) return items
            items.shuffle(random)
        }
        return items
    }

    private fun balancedGoNoGo(random: Random): List<CognitiveTrial> {
        val items = (List(16) { trial("GO") } + List(4) { trial("NO_GO") }).toMutableList()
        repeat(100) {
            items.shuffle(random)
            if (items.windowed(3).none { group -> group.all { it.stimulusCode == "NO_GO" } }) return items
        }
        return items
    }

    private fun balancedSearch(random: Random): List<CognitiveTrial> {
        val positions = (0..15).toMutableList().also { it.shuffle(random) }
        // Each quadrant is represented twice; seed also determines their order.
        val selected = mutableListOf<Int>()
        for (quadrant in (0..3).shuffled(random)) {
            selected += positions.first { it / 4 in quadrant / 2 * 2..quadrant / 2 * 2 + 1 && it % 4 in quadrant % 2 * 2..quadrant % 2 * 2 + 1 }
            positions.remove(selected.last())
            selected += positions.first { it / 4 in quadrant / 2 * 2..quadrant / 2 * 2 + 1 && it % 4 in quadrant % 2 * 2..quadrant % 2 * 2 + 1 }
            positions.remove(selected.last())
        }
        return selected.map(::searchTrial)
    }

    private fun trial(code: String) = CognitiveTrial(0, false, code, when {
        code.endsWith("LEFT") -> "LEFT"
        code.endsWith("RIGHT") -> "RIGHT"
        code == "GO" -> "RESPOND"
        else -> null
    })
    private fun searchTrial(target: Int) = CognitiveTrial(0, false, "GRID_4X4", target.toString(), target)

    fun symbolMapping(seed: Long): List<Int> = (1..6).shuffled(Random(seed))

    fun dsstTrial(position: Int, seed: Long): CognitiveTrial {
        val symbol = (0..5).shuffled(Random(seed xor (position / 6).toLong()))[position % 6]
        return CognitiveTrial(position + 1, position < 6, "SYMBOL_$symbol", symbolMapping(seed)[symbol].toString())
    }

    private fun nBack(random: Random, count: Int, matches: Int): List<CognitiveTrial> {
        var previous = random.nextInt(6)
        return (List(matches) { true } + List(count - matches) { false }).shuffled(random).map { match ->
            val symbol = if (match) previous else (0..5).filter { it != previous }.random(random)
            CognitiveTrial(0, false, "SYMBOL_$symbol", if (match) "SAME" else "DIFFERENT", referenceSymbol = previous)
                .also { previous = symbol }
        }
    }
}

/** Excluye prácticas y conserva promedios nulos cuando no existen aciertos con RT. */
object CognitiveScoring {
    fun calculate(type: CognitiveTestType, all: List<CognitiveTrialRecord>): CognitiveMetrics {
        val records = all.filterNot { it.isPractice }
        val correct = records.filter { it.outcomeCode in setOf("HIT", "GO_HIT", "NO_GO_CORRECT_REJECTION") }
        val mean = correct.mapNotNull { it.reactionTimeMillis }.takeIf { it.isNotEmpty() }?.average()
        val v = when (type) {
            CognitiveTestType.FLANKER -> mapOf(
                "accuracyPercent" to percent(correct.size, 12), "correct" to correct.size.toDouble(),
                "omissions" to records.count { it.outcomeCode == "OMISSION" }.toDouble(),
                "errors" to records.count { it.outcomeCode == "ERROR" }.toDouble(),
                "congruentErrors" to records.count { it.stimulusCode.startsWith("CONGRUENT") && it.outcomeCode == "ERROR" }.toDouble(),
                "incongruentErrors" to records.count { it.stimulusCode.startsWith("INCONGRUENT") && it.outcomeCode == "ERROR" }.toDouble(),
                "congruentMeanRtMs" to average(records.filter { it.stimulusCode.startsWith("CONGRUENT") && it.outcomeCode == "HIT" }),
                "incongruentMeanRtMs" to average(records.filter { it.stimulusCode.startsWith("INCONGRUENT") && it.outcomeCode == "HIT" }),
                "interferenceMs" to difference(records.filter { it.stimulusCode.startsWith("INCONGRUENT") && it.outcomeCode == "HIT" }, records.filter { it.stimulusCode.startsWith("CONGRUENT") && it.outcomeCode == "HIT" }),
                "meanCorrectRtMs" to mean,
            )
            CognitiveTestType.GO_NO_GO -> mapOf(
                "goHits" to records.count { it.outcomeCode == "GO_HIT" }.toDouble(),
                "goOmissions" to records.count { it.outcomeCode == "GO_OMISSION" }.toDouble(),
                "noGoCommissions" to records.count { it.outcomeCode == "NO_GO_COMMISSION" }.toDouble(),
                "correctInhibitions" to records.count { it.outcomeCode == "NO_GO_CORRECT_REJECTION" }.toDouble(),
                "anticipations" to records.count { it.outcomeCode == "ANTICIPATION" }.toDouble(),
                "meanGoRtMs" to average(records.filter { it.outcomeCode == "GO_HIT" }),
            )
            CognitiveTestType.VISUAL_SEARCH -> mapOf(
                "found" to records.count { it.outcomeCode == "HIT" }.toDouble(),
                "accuracyPercent" to percent(correct.size, 8),
                "errors" to records.count { it.outcomeCode == "ERROR" }.toDouble(),
                "omissions" to records.count { it.outcomeCode == "OMISSION" }.toDouble(),
                "meanCorrectDetectionMs" to mean,
            )
            CognitiveTestType.DSST, CognitiveTestType.N_BACK -> return BatteryScoring.calculate(type, all)
        }
        return CognitiveMetrics(v)
    }

    private fun percent(count: Int, total: Int) = count * 100.0 / total
    private fun average(records: List<CognitiveTrialRecord>): Double? = records.filter { it.outcomeCode == "HIT" || it.outcomeCode == "GO_HIT" }.mapNotNull { it.reactionTimeMillis }.takeIf { it.isNotEmpty() }?.average()
    private fun difference(a: List<CognitiveTrialRecord>, b: List<CognitiveTrialRecord>): Double? = average(a)?.let { aa -> average(b)?.let { aa - it } }
}
