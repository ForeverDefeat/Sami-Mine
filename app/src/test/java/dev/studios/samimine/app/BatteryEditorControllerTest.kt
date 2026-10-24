package dev.studios.samimine.app

import dev.studios.samimine.data.remote.BatteryDraft
import dev.studios.samimine.domain.model.BatteryDefinition
import dev.studios.samimine.ui.screens.access.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatteryEditorControllerTest {
    private val original = BatteryDraft("Batería de prueba", listOf("PVT", "DSST"), 1)
    private fun published(draft: BatteryDraft) = BatteryDefinition("default", draft.name, 2, "demo", draft.modules,
        mapOf("PVT" to "demo-pvt-v1", "DSST" to "experimental-dsst-v1"))
    @Test fun failedSaveKeepsChangesAndRetryCreatesASavedPublishableSnapshot() = runTest {
        var fail = true
        val controller = BatteryEditorController(this, { original }, { if (fail) error("network"); it.copy(revision = 2) }, ::published)
        controller.loadIfNeeded(); runCurrent()
        val edited = original.copy(name = "Modificada")
        controller.change(edited); controller.save(); runCurrent()
        assertEquals(edited, controller.state.value.draft)
        assertTrue(controller.state.value.dirty)
        assertNotNull(controller.state.value.error)
        assertFalse(controller.state.value.publishable)
        fail = false; controller.save(); runCurrent()
        assertFalse(controller.state.value.dirty)
        assertTrue(controller.state.value.publishable)
        assertEquals(2L, controller.state.value.draft!!.revision)
    }
    @Test fun rapidClicksCannotDuplicateSaveOrPublishDuringASave() = runTest {
        var saves = 0; var publications = 0
        val controller = BatteryEditorController(this, { original }, { saves++; delay(100); it.copy(revision = 2) }, { publications++; published(it) })
        controller.loadIfNeeded(); runCurrent(); controller.change(original.copy(name = "Nueva"))
        controller.save(); controller.save(); controller.publish(); runCurrent()
        assertEquals(1, saves); assertEquals(0, publications)
        advanceUntilIdle()
        controller.publish(); controller.publish(); runCurrent()
        assertEquals(1, publications)
    }
    @Test fun returningToTheScreenDoesNotReplaceAnUnsavedDraftAndDiscardRestoresIt() = runTest {
        var loads = 0
        val controller = BatteryEditorController(this, { loads++; original }, { it }, ::published)
        controller.loadIfNeeded(); runCurrent(); controller.change(original.copy(modules = listOf("DSST", "PVT")))
        controller.loadIfNeeded(); runCurrent()
        assertEquals(1, loads); assertEquals("DSST", controller.state.value.draft!!.modules.first())
        controller.discard(); assertEquals(original, controller.state.value.draft)
    }
    @Test fun unavailableAndInvalidDraftsCannotBePublished() = runTest {
        var publications = 0; var saves = 0
        val controller = BatteryEditorController(this, { original.copy(modules = listOf("KSS")) }, { saves++; it }, { publications++; published(it) })
        controller.loadIfNeeded(); runCurrent(); controller.publish(); runCurrent()
        assertEquals(0, publications)
        controller.change(original.copy(name = " ", modules = emptyList())); controller.save(); runCurrent()
        assertEquals(0, saves)
    }
    @Test fun leavingTheSessionCancelsOutstandingWorkAndCannotReloadIt() = runTest {
        var completed = false
        val controller = BatteryEditorController(this, { delay(100); completed = true; original }, { it }, ::published)
        controller.loadIfNeeded(); runCurrent(); controller.dispose(); advanceUntilIdle(); controller.reload(); runCurrent()
        assertFalse(completed); assertNull(controller.state.value.draft)
    }
}
