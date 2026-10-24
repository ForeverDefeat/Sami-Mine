package dev.studios.samimine.app

import dev.studios.samimine.ui.screens.access.*
import org.junit.Assert.*
import org.junit.Test

class ManagementPresentationTest {
    @Test fun authorizedHistoricalQuestionnaireTotalIsVisibleWithoutAnInventedGlobalScore() {
        val value = remoteResultData(RemoteRun("r", "REPORT", "COMPLETED", 100, """{"total":7,"instrumentId":"KSS"}"""), emptyList())
        assertEquals("Total registrado", value.metrics.single().label)
        assertEquals("7", value.metrics.single().value)
        assertTrue(remoteResultData(RemoteRun("r", "REPORT", "OMITTED", 100, """{"total":7}"""), emptyList()).metrics.isEmpty())
    }
    @Test fun storedMetricsKeepUnitsAndMissingValuesWithoutInventingZero() {
        val run = RemoteRun("r", "COGNITIVE", "COMPLETED", 100, """{"type":"FLANKER","shift":"NIGHT","storedMetrics":[{"key":"medianCorrectRtMs","value":null,"unit":"ms"},{"key":"correct","value":8,"unit":"count"}],"metrics":{"correct":999}}""")
        val value = remoteResultData(run, emptyList())
        assertEquals("Noche", value.shift)
        assertEquals("Sin datos", value.metrics.first().value)
        assertEquals("ms", value.metrics.first().unit)
        assertEquals("8", value.metrics.last().value)
        assertFalse(value.malformed)
    }
    @Test fun incompleteRecordNeverPresentsFinalMetricsEvenIfPayloadContainsThem() {
        val value = remoteResultData(RemoteRun("r", "PVT", "INVALID", 0, """{"metrics":{"responses":12}}"""), emptyList())
        assertTrue(value.metrics.isEmpty())
    }
    @Test fun brokenChunkDoesNotHideValidResponsesOrCrashTheScreen() {
        val value = remoteResultData(RemoteRun("r", "PVT", "COMPLETED", 100, "{}"), listOf("[{\"index\":1}]", "broken"))
        assertTrue(value.malformed)
        assertEquals(1, value.answers.size)
        assertTrue(value.metrics.isEmpty())
    }
    @Test fun historicalNestedMetricsAndRepeatedKeysRemainReadable() {
        val old = remoteResultData(RemoteRun("r", "COGNITIVE", "COMPLETED", 100, """{"metrics":{"values":{"correct":3}}}"""), emptyList())
        assertEquals("3", old.metrics.single().value)
        val duplicate = remoteResultData(RemoteRun("r", "COGNITIVE", "COMPLETED", 100, """{"storedMetrics":[{"key":"correct","value":3},{"key":"correct","value":4}]}"""), emptyList())
        assertEquals(1, duplicate.metrics.size)
    }
    @Test fun invalidSummaryAndNonFiniteMetricsHaveAnHonestFallback() {
        assertTrue(remoteResultData(RemoteRun("r", "PVT", "COMPLETED", 0, "[]"), emptyList()).malformed)
        val value = remoteResultData(RemoteRun("r", "PVT", "COMPLETED", 0, """{"metrics":{"responses":"NaN"}}"""), emptyList())
        assertEquals("Sin datos", value.metrics.single().value)
    }
}
