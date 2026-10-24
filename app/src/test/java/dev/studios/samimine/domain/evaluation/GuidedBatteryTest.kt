package dev.studios.samimine.domain.evaluation

import dev.studios.samimine.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GuidedBatteryTest {
    private val definition = BatteryDefinition("default", "Demo", 1, "mine", listOf("PVT", "DSST"),
        mapOf("PVT" to "demo-pvt-v1", "DSST" to "experimental-dsst-v1"))
    @Test fun definitionsRejectUnavailableDuplicateUnknownAndIncompatibleModules() {
        definition.validate()
        assertTrue(runCatching { definition.copy(modules = listOf("PVT", "PVT")).validate() }.isFailure)
        assertTrue(runCatching { definition.copy(schemaVersion = 2).validate() }.isFailure)
        assertTrue(runCatching { definition.copy(protocols = definition.protocols + ("DSST" to "other")).validate() }.isFailure)
        assertTrue(runCatching { definition.copy(modules = listOf("KSS"), protocols = mapOf("KSS" to "")).validate() }.isFailure)
        assertTrue(runCatching { definition.copy(modules = emptyList(), protocols = emptyMap()).validate() }.isFailure)
    }
    @Test fun completedStepsArePreservedAndInterruptedStepsRemainNext() {
        val run = GuidedBatteryRun("r", "a", Shift.DAY, 100, definition,
            steps = listOf(BatteryStep("PVT", BatteryStepStatus.COMPLETED), BatteryStep("DSST", BatteryStepStatus.INTERRUPTED)),
            status = BatteryRunStatus.PAUSED)
        run.validate()
        assertEquals(1, run.nextStep)
        assertTrue(runCatching { run.copy(status = BatteryRunStatus.COMPLETED).validate() }.isFailure)
        assertTrue(runCatching { run.copy(steps = listOf(BatteryStep("PVT", BatteryStepStatus.OMITTED), BatteryStep("DSST"))).validate() }.isFailure)
        val changed = definition.copy(version = 2, modules = definition.modules.reversed())
        assertEquals(listOf("PVT", "DSST"), run.definition.modules)
        assertNotEquals(changed, run.definition)
    }
}
