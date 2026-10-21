package dev.studios.samimine.domain.evaluation

import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.evaluation.cognitive.CognitiveTrialEngine
import org.junit.Assert.*
import org.junit.Test

class CognitiveTestsTest {
    @Test fun v2WindowsAllowResponsesUntilThreeSecondsAndKeepLegacyRules() {
        for (type in listOf(CognitiveTestType.FLANKER, CognitiveTestType.GO_NO_GO)) {
            val config = CognitiveTestConfig.forType(type)
            assertEquals(3_000L, config.responseWindowMillis)
            assertTrue(config.protocolVersion.endsWith("-v2"))
            val trial = CognitiveTrial(1, false, if (type == CognitiveTestType.FLANKER) "CONGRUENT_LEFT" else "GO", if (type == CognitiveTestType.FLANKER) "LEFT" else "RESPOND")
            val before = CognitiveTrialEngine(type, trial)
            before.stimulusDrawn(100)
            assertNull(before.respond(trial.expectedResponse!!, 99))
            assertNotNull(before.respond(trial.expectedResponse!!, 3_099)?.reactionTimeMillis)
            val edge = CognitiveTrialEngine(type, trial)
            edge.stimulusDrawn(100)
            assertNull(edge.timeout(3_099))
            assertNull(edge.respond("INVALID", 2_000))
            assertNull(edge.respond(trial.expectedResponse!!, 3_100)?.reactionTimeMillis)
            assertNull(edge.respond(trial.expectedResponse!!, 3_101))
            val v1 = CognitiveTestConfig.forVersion(type, config.protocolVersion.replace("v2", "v1"))
            assertEquals(if (type == CognitiveTestType.FLANKER) 1_200L else 800L, v1.responseWindowMillis)
        }
        assertEquals(4_000L, CognitiveTestConfig.forType(CognitiveTestType.VISUAL_SEARCH).responseWindowMillis)
    }
    @Test fun flankerHasTwelveBalancedScoredTrialsAndReplaysSeed() {
        val config = CognitiveTestConfig.forType(CognitiveTestType.FLANKER)
        val first = CognitiveTrials.generate(config, 42L)
        val second = CognitiveTrials.generate(config, 42L)
        assertEquals(first, second)
        val scored = first.filterNot { it.isPractice }
        assertEquals((3..14).toList(), scored.map { it.index })
        assertEquals(12, scored.size)
        assertEquals(mapOf("CONGRUENT_LEFT" to 3, "CONGRUENT_RIGHT" to 3, "INCONGRUENT_LEFT" to 3, "INCONGRUENT_RIGHT" to 3), scored.groupingBy { it.stimulusCode }.eachCount())
        assertTrue(scored.windowed(4).none { group -> group.map { it.expectedResponse }.distinct().size == 1 })
    }

    @Test fun goNoGoHasSixteenGoAndFourNoGoWithNoLongNoGoRun() {
        val scored = CognitiveTrials.generate(CognitiveTestConfig.forType(CognitiveTestType.GO_NO_GO), 7L).filterNot { it.isPractice }
        assertEquals(20, scored.size)
        assertEquals(16, scored.count { it.stimulusCode == "GO" })
        assertEquals(4, scored.count { it.stimulusCode == "NO_GO" })
        assertTrue(scored.windowed(3).none { it.all { trial -> trial.stimulusCode == "NO_GO" } })
    }

    @Test fun visualSearchHasEightBoardsWithBalancedQuadrantsAndUniqueTarget() {
        val scored = CognitiveTrials.generate(CognitiveTestConfig.forType(CognitiveTestType.VISUAL_SEARCH), 99L).filterNot { it.isPractice }
        assertEquals(8, scored.size)
        assertEquals((2..9).toList(), scored.map { it.index })
        assertTrue(scored.all { it.stimulusCode == "GRID_4X4" && it.targetIndex in 0..15 })
        val quadrants = scored.groupingBy { (it.targetIndex!! / 4 / 2) * 2 + (it.targetIndex % 4 / 2) }.eachCount()
        assertEquals(listOf(2, 2, 2, 2), quadrants.values.sorted())
    }

    @Test fun flankerScoringUsesCorrectResponsesOnlyAndKeepsConditionEffectsSeparate() {
        val records = listOf(
            record(1, "CONGRUENT_LEFT", "HIT", 300), record(2, "INCONGRUENT_LEFT", "HIT", 500),
            record(3, "INCONGRUENT_RIGHT", "ERROR", null), record(4, "CONGRUENT_RIGHT", "OMISSION", null),
            record(5, "CONGRUENT_LEFT", "PRACTICE_CORRECT", 100, true),
        )
        val metrics = CognitiveScoring.calculate(CognitiveTestType.FLANKER, records).values
        assertEquals(2.0, metrics["correct"])
        assertEquals(1.0, metrics["errors"])
        assertEquals(1.0, metrics["omissions"])
        assertEquals(300.0, metrics["congruentMeanRtMs"]!!, 0.0)
        assertEquals(500.0, metrics["incongruentMeanRtMs"]!!, 0.0)
        assertEquals(200.0, metrics["interferenceMs"]!!, 0.0)
    }

    @Test fun f01F02FlankerIncongruentTrialsUseOnlyTheCentralArrow() {
        val f01 = CognitiveTrialEngine(CognitiveTestType.FLANKER, CognitiveTrial(1, false, "INCONGRUENT_LEFT", "LEFT"))
        f01.stimulusDrawn(1_000)
        val correct = f01.respond("LEFT", 1_350)
        assertEquals("HIT", correct?.outcomeCode)
        assertEquals(350L, correct?.reactionTimeMillis)

        val f02 = CognitiveTrialEngine(CognitiveTestType.FLANKER, CognitiveTrial(2, false, "INCONGRUENT_RIGHT", "RIGHT"))
        f02.stimulusDrawn(2_000)
        val wrong = f02.respond("LEFT", 2_350)
        assertEquals("ERROR", wrong?.outcomeCode)
        assertEquals("RIGHT", wrong?.expectedResponse)
    }

    @Test fun goNoGoSeparatesOmissionsCommissionsInhibitionsAndAnticipations() {
        val records = listOf(
            record(1, "GO", "GO_HIT", 250), record(2, "GO", "GO_OMISSION", null),
            record(3, "NO_GO", "NO_GO_COMMISSION", null), record(4, "NO_GO", "NO_GO_CORRECT_REJECTION", null),
            record(5, "GO", "ANTICIPATION", null),
        )
        val metrics = CognitiveScoring.calculate(CognitiveTestType.GO_NO_GO, records).values
        assertEquals(1.0, metrics["goHits"])
        assertEquals(1.0, metrics["goOmissions"])
        assertEquals(1.0, metrics["noGoCommissions"])
        assertEquals(1.0, metrics["correctInhibitions"])
        assertEquals(1.0, metrics["anticipations"])
        assertEquals(250.0, metrics["meanGoRtMs"]!!, 0.0)
    }

    @Test fun visualSearchSeparatesErrorsAndOmissionsAndHasNoZeroMean() {
        val metrics = CognitiveScoring.calculate(CognitiveTestType.VISUAL_SEARCH, listOf(
            record(1, "GRID_4X4", "HIT", 800), record(2, "GRID_4X4", "ERROR", null), record(3, "GRID_4X4", "OMISSION", null),
        )).values
        assertEquals(1.0, metrics["found"])
        assertEquals(1.0, metrics["errors"])
        assertEquals(1.0, metrics["omissions"])
        assertEquals(800.0, metrics["meanCorrectDetectionMs"]!!, 0.0)
        assertNull(CognitiveScoring.calculate(CognitiveTestType.VISUAL_SEARCH, listOf(record(1, "GRID_4X4", "OMISSION", null))).values["meanCorrectDetectionMs"])
    }

    @Test fun configurationRejectsInvalidWindowsAndVersions() {
        assertTrue(runCatching { CognitiveTestConfig(CognitiveTestType.FLANKER, 2, 12, 0, 1200, 400, 700) }.isFailure)
        assertTrue(runCatching { CognitiveTestConfig(CognitiveTestType.FLANKER, 2, 12, 400, 1200, 700, 400) }.isFailure)
        assertTrue(runCatching { CognitiveTestConfig(CognitiveTestType.FLANKER, 2, 12, 400, 1200, 400, 700, "bad-version") }.isFailure)
    }

    @Test fun flankerResponseTimeoutAndDuplicateEventsResolveOnlyOnce() {
        val trial = CognitiveTrials.generate(CognitiveTestConfig.forType(CognitiveTestType.FLANKER), 12L).first { !it.isPractice }
        val engine = CognitiveTrialEngine(CognitiveTestType.FLANKER, trial, 1_200)
        assertTrue(engine.stimulusDrawn(10_000))
        assertFalse(engine.stimulusDrawn(10_001))
        assertNull(engine.timeout(11_199))
        val response = engine.respond(trial.expectedResponse!!, 11_200)
        assertEquals("OMISSION", response?.outcomeCode)
        assertNull(engine.respond(trial.expectedResponse, 11_201))
        assertNull(engine.timeout(12_000))
    }

    @Test fun goNoGoPrematureTouchIsAnAnticipationAndNeverMovesToNextTrial() {
        val trial = CognitiveTrial(1, false, "GO", "RESPOND")
        val engine = CognitiveTrialEngine(CognitiveTestType.GO_NO_GO, trial, 800)
        val premature = engine.respond("RESPOND", 100)
        assertEquals("ANTICIPATION", premature?.outcomeCode)
        assertFalse(engine.stimulusDrawn(400))
        assertNull(engine.respond("RESPOND", 450))
    }

    @Test fun g01GoTouchIsAHitWithElapsedReactionTime() {
        val engine = CognitiveTrialEngine(CognitiveTestType.GO_NO_GO, CognitiveTrial(1, false, "GO", "RESPOND"))
        engine.stimulusDrawn(100)
        val result = engine.respond("RESPOND", 350)
        assertEquals("GO_HIT", result?.outcomeCode)
        assertEquals(250L, result?.reactionTimeMillis)
    }

    @Test fun noGoTimeoutIsCorrectInhibitionAndTouchIsCommission() {
        val trial = CognitiveTrial(1, false, "NO_GO", null)
        val timeoutEngine = CognitiveTrialEngine(CognitiveTestType.GO_NO_GO, trial, 800)
        timeoutEngine.stimulusDrawn(50)
        assertNull(timeoutEngine.timeout(849))
        assertEquals("NO_GO_CORRECT_REJECTION", timeoutEngine.timeout(850)?.outcomeCode)

        val touchEngine = CognitiveTrialEngine(CognitiveTestType.GO_NO_GO, trial, 800)
        touchEngine.stimulusDrawn(50)
        assertEquals("NO_GO_COMMISSION", touchEngine.respond("RESPOND", 300)?.outcomeCode)
    }

    @Test fun visualSearchCorrectAndIncorrectSelectionsAreSingleUse() {
        val trial = CognitiveTrial(1, false, "GRID_4X4", "7", 7)
        val correct = CognitiveTrialEngine(CognitiveTestType.VISUAL_SEARCH, trial, 4_000)
        correct.stimulusDrawn(100)
        assertEquals(300L, correct.respond("7", 400)?.reactionTimeMillis)

        val wrong = CognitiveTrialEngine(CognitiveTestType.VISUAL_SEARCH, trial, 4_000)
        wrong.stimulusDrawn(100)
        assertEquals("ERROR", wrong.respond("8", 400)?.outcomeCode)
        assertNull(wrong.respond("7", 450))
    }

    @Test fun v03VisualSearchTimeoutIsAnOmissionWithoutReactionTime() {
        val engine = CognitiveTrialEngine(CognitiveTestType.VISUAL_SEARCH, CognitiveTrial(1, false, "GRID_4X4", "7", 7), 4_000)
        engine.stimulusDrawn(100)
        assertNull(engine.timeout(4_099))
        val result = engine.timeout(4_100)
        assertEquals("OMISSION", result?.outcomeCode)
        assertNull(result?.responseUptimeMillis)
        assertNull(result?.reactionTimeMillis)
        assertNull(engine.respond("7", 4_101))
    }

    private fun record(index: Int, stimulus: String, outcome: String, rt: Long?, practice: Boolean = false) =
        CognitiveTrialRecord(index, practice, stimulus, null, null, outcome, rt, null, null)
}
