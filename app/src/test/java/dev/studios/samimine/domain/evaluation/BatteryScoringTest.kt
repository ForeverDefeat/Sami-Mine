package dev.studios.samimine.domain.evaluation

import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.evaluation.cognitive.*
import dev.studios.samimine.domain.evaluation.pvt.PvtScoring
import org.junit.Assert.*
import org.junit.Test

class BatteryScoringTest {
    private fun record(index: Int, code: String, outcome: String, rt: Long? = null, expected: String? = null, practice: Boolean = false) =
        CognitiveTrialRecord(index, practice, code, expected, null, outcome, rt, null, null)

    @Test fun flankerMediansUseCorrectResponsesAndRequireThreePerConditionForCost() {
        val rows = listOf(record(1, "CONGRUENT_LEFT", "HIT", 100), record(2, "CONGRUENT_LEFT", "HIT", 300),
            record(3, "CONGRUENT_RIGHT", "HIT", 200), record(4, "INCONGRUENT_LEFT", "HIT", 800),
            record(5, "INCONGRUENT_LEFT", "HIT", 400), record(6, "INCONGRUENT_RIGHT", "ERROR", 50),
            record(7, "INCONGRUENT_RIGHT", "HIT", 1, practice = true))
        val sparse = BatteryScoring.describe(CognitiveTestType.FLANKER, rows).associateBy { it.key }
        assertEquals(200.0, sparse["congruentMedianRtMs"]!!.value!!, 0.0)
        assertEquals(600.0, sparse["incongruentMedianRtMs"]!!.value!!, 0.0)
        assertNull(sparse["flankerCostMs"]!!.value)
        assertEquals(3, sparse["accuracyIncongruent"]!!.denominator)
        val full = BatteryScoring.calculate(CognitiveTestType.FLANKER, rows + record(8, "INCONGRUENT_RIGHT", "HIT", 500))
        assertEquals(300.0, full.values["flankerCostMs"]!!, 0.0)
    }

    @Test fun goRatesSeparateCommissionsAnticipationsAndOmissions() {
        val metrics = BatteryScoring.describe(CognitiveTestType.GO_NO_GO, listOf(
            record(1, "GO", "GO_HIT", 250), record(2, "GO", "ANTICIPATION"), record(3, "GO", "GO_OMISSION"),
            record(4, "NO_GO", "NO_GO_COMMISSION", 100), record(5, "NO_GO", "NO_GO_CORRECT_REJECTION"))).associateBy { it.key }
        assertEquals(50.0, metrics["noGoCommissionRate"]!!.value!!, 0.0)
        assertEquals(2, metrics["noGoCommissionRate"]!!.denominator)
        assertEquals(250.0, metrics["medianGoRtMs"]!!.value!!, 0.0)
        assertEquals(1.0, metrics["anticipations"]!!.value!!, 0.0)
        assertEquals(100.0 / 3, metrics["goHitRate"]!!.value!!, .0001)
    }

    @Test fun emptyAndAllOmittedSamplesNeverProduceZeroReactionTimes() {
        for (type in CognitiveTestType.entries) {
            assertTrue(BatteryScoring.describe(type, emptyList()).filter { it.unit == "%" || it.unit == "ms" }.all { it.value == null })
        }
        val metrics = BatteryScoring.calculate(CognitiveTestType.VISUAL_SEARCH, listOf(record(1, "GRID_4X4", "OMISSION")))
        assertEquals(0.0, metrics.values["accuracyPercent"]!!, 0.0)
        assertNull(metrics.values["medianDetectionRtMs"])
        assertEquals(100.0, metrics.values["timeoutRate"]!!, 0.0)
    }

    @Test fun newMetricsDoNotChangeLegacyCalculation() {
        val rows = listOf(record(1, "CONGRUENT_LEFT", "HIT", 100), record(2, "CONGRUENT_LEFT", "HIT", 100), record(3, "CONGRUENT_LEFT", "HIT", 1000))
        val session = CognitiveTestSession("id", "worker", "Demo", "Area", "Demo", Shift.DAY, CognitiveTestType.FLANKER,
            "demo-flanker-v1", "{}", 42, 100, status = CognitiveSessionStatus.COMPLETED, trials = rows)
        assertEquals(400.0, session.metrics!!.values["congruentMeanRtMs"]!!, 0.0)
        assertFalse(session.metrics!!.values.containsKey("congruentMedianRtMs"))
        assertEquals(100.0, session.copy(calculationVersion = "descriptive-v2").metrics!!.values["congruentMedianRtMs"]!!, 0.0)
        assertNull(session.copy(status = CognitiveSessionStatus.INVALID).metrics)
    }

    @Test fun symbolMappingAndAssociationStreamAreBalancedAndReproducible() {
        assertEquals((1..6).toList(), CognitiveTrials.symbolMapping(42).sorted())
        val stream = (0..29).map { CognitiveTrials.dsstTrial(it, 42) }
        assertEquals(stream, (0..29).map { CognitiveTrials.dsstTrial(it, 42) })
        assertTrue(stream.chunked(6).all { it.map { t -> t.stimulusCode }.distinct().size == 6 })
        assertEquals(6, stream.count { it.isPractice })
    }

    @Test fun oneBackHasEightMatchesAndConsistentPreviousSymbolsAcrossSeeds() {
        repeat(100) { seed ->
            val config = CognitiveTestConfig.forType(CognitiveTestType.N_BACK)
            val generated = CognitiveTrials.generate(config, seed.toLong())
            assertEquals(generated, CognitiveTrials.generate(config, seed.toLong()))
            assertEquals(6, generated.count { it.isPractice })
            val scored = generated.filterNot { it.isPractice }
            assertEquals(24, scored.size)
            assertEquals(8, scored.count { it.expectedResponse == "SAME" })
            scored.forEach { trial -> assertEquals(trial.expectedResponse == "SAME", trial.stimulusCode == "SYMBOL_${trial.referenceSymbol}") }
            scored.zipWithNext().forEach { (a, b) -> assertEquals(a.stimulusCode, "SYMBOL_${b.referenceSymbol}") }
        }
    }

    @Test fun oneBackSeparatesWrongComparisonsFromMissingResponsesAndRejectsDuplicates() {
        val trial = CognitiveTrial(1, false, "SYMBOL_2", "SAME", referenceSymbol = 2)
        val engine = CognitiveTrialEngine(CognitiveTestType.N_BACK, trial)
        engine.stimulusDrawn(100)
        assertEquals("MISS", engine.respond("DIFFERENT", 200)!!.outcomeCode)
        assertNull(engine.respond("SAME", 201))
        val omitted = CognitiveTrialEngine(CognitiveTestType.N_BACK, trial)
        omitted.stimulusDrawn(100)
        assertNull(omitted.timeout(2599))
        assertEquals("OMISSION", omitted.respond("SAME", 2600)!!.outcomeCode)
        val falseAlarm = CognitiveTrialEngine(CognitiveTestType.N_BACK, trial.copy(expectedResponse = "DIFFERENT"))
        falseAlarm.stimulusDrawn(100)
        assertEquals("FALSE_ALARM", falseAlarm.respond("SAME", 200)!!.outcomeCode)
    }

    @Test fun associationDeadlineDoesNotRestartAndExcludesBoundaryResponse() {
        val timer = TimedAssociationEngine()
        assertFalse(timer.accepts(1))
        assertTrue(timer.presented(100))
        assertFalse(timer.presented(20_000))
        assertFalse(timer.accepts(99))
        assertTrue(timer.accepts(60_099))
        assertFalse(timer.accepts(60_100))
        assertFalse(timer.expire(60_099))
        assertTrue(timer.expire(60_200))
        assertEquals(60_100L, timer.finishedAt)
        assertFalse(timer.expire(60_300))
    }

    @Test fun associationScoringUsesFixedMinuteAndExcludesPractice() {
        val metrics = BatteryScoring.calculate(CognitiveTestType.DSST, listOf(record(1, "SYMBOL_1", "HIT", 30, practice = true),
            record(2, "SYMBOL_1", "HIT", 300), record(3, "SYMBOL_2", "ERROR", 400)))
        assertEquals(1.0, metrics.values["correctPerMinute"]!!, 0.0)
        assertEquals(2.0, metrics.values["attempted"]!!, 0.0)
        assertEquals(50.0, metrics.values["accuracyPercent"]!!, 0.0)
    }

    @Test fun pvtZeroReactionNeverDividesByZeroAndMedianRemainsDescriptive() {
        val metrics = PvtScoring.calculate(listOf(Attempt(1, AttemptOutcome.RESPONSE, 0), Attempt(2, AttemptOutcome.RESPONSE, 200_000_000)))
        assertEquals(100.0, metrics.medianReactionMillis!!, 0.0)
        assertEquals(5.0, metrics.meanResponseSpeed!!, 0.0)
        assertNull(PvtScoring.calculate(listOf(Attempt(1, AttemptOutcome.RESPONSE, 0))).meanResponseSpeed)
    }

    @Test fun questionnairesCannotBeEnabledByAuthorizationFlagAlone() {
        assertEquals(8, BatteryCatalog.modules().size)
        assertFalse(BatteryCatalog.modules().any { it.id == "BORG" })
        assertTrue(InstrumentCatalog.definitions.all { !it.available })
        assertTrue(InstrumentCatalog.definitions.all { !it.copy(authorized = true).available })
        val fixture = InstrumentDefinition(InstrumentId.KSS, "fixture-v1", "es-PE", "Synthetic test instructions", "Synthetic test period",
            listOf(InstrumentQuestion("q", "Synthetic test question", (1..9).map { AnswerOption("$it", "Fixture $it", it) })), true, "fixture permission", "fixture approval")
        assertTrue(fixture.available)
        assertFalse(fixture.copy(permissionReference = null).available)
        assertFalse(fixture.copy(questions = emptyList()).available)
        assertFalse(fixture.copy(language = "en").available)
    }
}
