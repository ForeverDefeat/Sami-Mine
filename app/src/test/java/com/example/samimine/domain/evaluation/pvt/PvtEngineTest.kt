package com.example.samimine.domain.evaluation.pvt

import com.example.samimine.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PvtEngineTest {
    private var nanos = 0L
    private val clock = MonotonicClock { nanos }

    @Test fun responseUsesDrawOnsetAndCannotBeCountedTwice() {
        val engine = PvtEngine(clock, 3_000)
        engine.begin(1)
        nanos = 5_000_000_000
        engine.stimulusPresented()
        nanos += 250_000_000
        engine.stimulusPresented() // redraw must not reset the timestamp
        assertEquals(Attempt(1, AttemptOutcome.RESPONSE, 250_000_000), engine.respond())
        assertNull(engine.respond())
        assertNull(engine.expire())
    }

    @Test fun anticipationEndsAttemptAndLateStimulusDoesNotReopenIt() {
        val engine = PvtEngine(clock, 3_000)
        engine.begin(1)
        assertEquals(AttemptOutcome.ANTICIPATION, engine.respond()?.outcome)
        engine.stimulusPresented()
        assertNull(engine.respond())
        engine.begin(2)
        engine.stimulusPresented()
        nanos += 100_000_000
        assertEquals(2, engine.respond()?.index)
    }

    @Test fun responseAtDeadlineIsOmissionAndEarlyTimeoutDoesNothing() {
        val engine = PvtEngine(clock, 3_000)
        engine.begin(1)
        assertNull(engine.expire())
        engine.stimulusPresented()
        nanos = 2_999_999_999
        assertNull(engine.expire())
        nanos = 3_000_000_000
        assertEquals(AttemptOutcome.OMISSION, engine.respond()?.outcome)
        assertNull(engine.expire())
    }

    @Test fun timeoutAndInterruptionBlockFurtherResponses() {
        val engine = PvtEngine(clock, 3_000)
        engine.begin(1)
        engine.stimulusPresented()
        nanos = 3_100_000_000
        assertEquals(AttemptOutcome.OMISSION, engine.expire()?.outcome)
        assertNull(engine.respond())
        engine.begin(2)
        engine.stop()
        assertNull(engine.respond())
    }

    @Test fun scoringExcludesAnticipationsAndOmissionsFromMean() {
        val metrics = PvtScoring.calculate(listOf(
            Attempt(1, AttemptOutcome.RESPONSE, 200_000_000),
            Attempt(2, AttemptOutcome.ANTICIPATION),
            Attempt(3, AttemptOutcome.RESPONSE, 400_000_000),
            Attempt(4, AttemptOutcome.OMISSION),
        ))
        assertEquals(Metrics(300.0, 2, 1, 1), metrics)
        assertNull(PvtScoring.calculate(listOf(Attempt(1, AttemptOutcome.OMISSION))).meanReactionMillis)
        assertNull(PvtScoring.calculate(emptyList()).meanReactionMillis)
    }
}
