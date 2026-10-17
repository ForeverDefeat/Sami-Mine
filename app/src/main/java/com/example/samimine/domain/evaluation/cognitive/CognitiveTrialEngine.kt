package com.example.samimine.domain.evaluation.cognitive

import com.example.samimine.domain.model.*

/** Resolves exactly one stimulus from monotonic timestamps; UI timers only request timeout checks. */
class CognitiveTrialEngine(
    private val type: CognitiveTestType,
    private val trial: CognitiveTrial,
    private val responseWindowMillis: Long = CognitiveTestConfig.forType(type).responseWindowMillis,
) {
    private var onsetMillis: Long? = null
    private var resolved = false

    init { require(responseWindowMillis > 0) }

    fun stimulusDrawn(uptimeMillis: Long): Boolean {
        if (resolved || onsetMillis != null) return false
        onsetMillis = uptimeMillis
        return true
    }

    fun respond(answer: String, uptimeMillis: Long): CognitiveTrialRecord? {
        if (resolved) return null
        val validAnswer = when (type) {
            CognitiveTestType.FLANKER -> answer == "LEFT" || answer == "RIGHT"
            CognitiveTestType.GO_NO_GO -> answer == "RESPOND"
            CognitiveTestType.VISUAL_SEARCH -> answer.toIntOrNull() in 0..15
        }
        if (!validAnswer) return null
        val onset = onsetMillis
        if (onset == null) {
            if (type != CognitiveTestType.GO_NO_GO) return null
            return finish(answer, "ANTICIPATION", null, null, uptimeMillis)
        }
        val elapsed = uptimeMillis - onset
        if (elapsed < 0) return null
        if (elapsed >= responseWindowMillis) return timeoutRecord()
        val outcome = when {
            type == CognitiveTestType.GO_NO_GO && trial.stimulusCode == "NO_GO" -> "NO_GO_COMMISSION"
            type == CognitiveTestType.GO_NO_GO -> "GO_HIT"
            answer == trial.expectedResponse -> "HIT"
            else -> "ERROR"
        }
        return finish(answer, outcome, elapsed, onset, uptimeMillis)
    }

    fun timeout(uptimeMillis: Long): CognitiveTrialRecord? {
        val onset = onsetMillis ?: return null
        if (resolved || uptimeMillis - onset < responseWindowMillis) return null
        return timeoutRecord()
    }

    fun interrupt() { resolved = true }

    private fun timeoutRecord(): CognitiveTrialRecord {
        val onset = checkNotNull(onsetMillis)
        val outcome = if (type == CognitiveTestType.GO_NO_GO) {
            if (trial.stimulusCode == "NO_GO") "NO_GO_CORRECT_REJECTION" else "GO_OMISSION"
        } else "OMISSION"
        return finish(null, outcome, null, onset, null)
    }

    private fun finish(answer: String?, outcome: String, reaction: Long?, onset: Long?, responseAt: Long?): CognitiveTrialRecord {
        resolved = true
        return record(answer, outcome, reaction, onset, responseAt)
    }

    private fun record(answer: String?, outcome: String, reaction: Long?, onset: Long?, responseAt: Long?) =
        CognitiveTrialRecord(trial.index, false, trial.stimulusCode, trial.expectedResponse, answer,
            outcome, reaction, onset, responseAt, trial.interTrialDelayMillis)
}
