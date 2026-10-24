package dev.studios.samimine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import dev.studios.samimine.app.SamiMineApplication
import dev.studios.samimine.data.remote.*
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.tasks.await
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID

/** Run separately with pilotAuth=true while demo-samimine Auth/Firestore emulators are running. */
@RunWith(AndroidJUnit4::class)
class PilotFirebaseFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as SamiMineApplication).container
    private var activity: ActivityScenario<MainActivity>? = null

    @Before fun setup() {
        Assume.assumeTrue("Run the emulator acceptance suite with -Pandroid.testInstrumentationRunnerArguments.pilotAuth=true",
            InstrumentationRegistry.getArguments().getString("pilotAuth") == "true")
        PilotEnvironment.demoTests = false
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "pilot-emulator" } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder(FirebaseApp.getInstance().options).setProjectId("demo-samimine").build(), "pilot-emulator")
        PilotEnvironment.firebaseApp = app
        // Configure before AppContainer creates its Firestore instance.
        if (!configured) {
            val args = InstrumentationRegistry.getArguments()
            FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", args.getString("authPort")?.toInt() ?: 9099)
            FirebaseFirestore.getInstance(app).useEmulator("127.0.0.1", args.getString("firestorePort")?.toInt() ?: 8080); configured = true
        }
        container.auth.signOut(); container.sessionCache.clear()
    }
    @After fun cleanup() { activity?.close(); if (PilotEnvironment.firebaseApp != null) container.auth.signOut(); PilotEnvironment.demoTests = true }
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun confirmUnansweredPvt() {
        repeat(3) { index ->
            val label = if (index == 2) "Ver resultado" else "Continuar prueba"
            waitText(label)
            compose.onNodeWithText(label).performClick()
        }
    }
    private fun login(uid: String) {
        waitText("Correo electrónico")
        compose.onNodeWithText("Correo electrónico").performTextInput("$uid@samimine.example")
        compose.onNodeWithText("Contraseña").performTextInput("Pilot-Emulator-2026!")
        compose.onNodeWithText("Ingresar").performClick()
    }
    private fun logout() {
        val management = compose.onAllNodesWithTag("management-tab-account").fetchSemanticsNodes().isNotEmpty()
        if (management) {
            compose.onNodeWithTag("management-tab-account").performClick()
            compose.onAllNodesWithText("Cerrar sesión").onFirst().performScrollTo().performClick()
        } else compose.onAllNodesWithText("Cerrar sesión").onFirst().performClick()
        waitText("Correo electrónico")
    }
    @Test fun workerEvaluationSynchronizesAndOtherRolesOnlySeeTheirPermissions() = runBlocking<Unit> {
        activity = ActivityScenario.launch(MainActivity::class.java)
        login("pilot-worker-a")
        waitText("Turno Día")
        compose.onNodeWithText("Turno Día").performClick()
        compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar prueba").performClick()
        confirmUnansweredPvt()
        compose.waitUntil(40_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        val uid = container.auth.currentUid!!
        assertEquals("pilot-worker-a", uid)
        assertTrue(container.sync(uid).sendPending(uid))
        // WorkManager may have taken the same delivery; wait for its durable acknowledgment.
        withTimeout(15_000) {
            while (container.database(uid).sync().pending(uid).isNotEmpty()) delay(100)
        }
        assertTrue(container.database(uid).sync().pending(uid).isEmpty())
        val results = container.firestore.collection("users").document(uid).collection("results").whereEqualTo("complete", true).limit(25).get(Source.SERVER).await()
        assertFalse(results.isEmpty)
        val run = results.documents.first()
        logout()
        login("pilot-wellbeing")
        waitText("Personal de bienestar")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Trabajador A · Activo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Trabajador C · Activo").assertDoesNotExist()
        compose.onNodeWithText("Trabajador A · Activo").performClick()
        waitText("Ver resultado y respuestas")
        compose.onAllNodesWithText("Ver resultado y respuestas").onFirst().performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Respuestas registradas"))
        compose.onNodeWithText("Respuestas registradas").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Registro 1"))
        compose.onNodeWithText("Registro 1").assertExists()
        logout()
        login("pilot-admin")
        waitText("Administrador / supervisor")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Trabajador A · Activo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Trabajador A · Activo").performClick()
        waitText("Participación recibida")
        compose.onNodeWithText("Ver resultado y respuestas").assertDoesNotExist()
        val denied = runCatching { container.firestore.document(run.reference.path).get(Source.SERVER).await() }.exceptionOrNull()
        assertTrue(denied is FirebaseFirestoreException && denied.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        logout()
    }
    @Test fun accountSwitchDoesNotSendOrExposeAnotherWorkersQueue() = runBlocking<Unit> {
        val uid = container.auth.signIn("pilot-worker-a@samimine.example", "Pilot-Emulator-2026!")
        val database = container.database(uid)
        val repository = RoomEvaluationRepository(database)
        val id = "isolation-${UUID.randomUUID()}"
        repository.start(Evaluation(id, Worker(uid, "A", "Demo", "Demo"), Shift.DAY, 100, PvtConfiguration()))
        repository.interrupt(id, "Prueba de aislamiento")
        container.auth.signOut()
        val otherUid = container.auth.signIn("pilot-worker-b@samimine.example", "Pilot-Emulator-2026!")
        assertTrue(container.sync(uid).sendPending(uid))
        assertTrue(database.sync().pending(uid).any { it.runId == id })
        assertTrue(container.database(otherUid).sync().pending(otherUid).none { it.runId == id })
        assertNull(RoomEvaluationRepository(container.database(otherUid)).get(id, uid))
        val forbidden = runCatching { container.firestore.collection("users").document(uid).collection("results").document("PVT_$id").get(Source.SERVER).await() }.exceptionOrNull()
        assertTrue(forbidden is FirebaseFirestoreException && forbidden.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        container.auth.signOut()
        container.auth.signIn("pilot-worker-a@samimine.example", "Pilot-Emulator-2026!")
        assertTrue(container.sync(uid).sendPending(uid))
        assertTrue(database.sync().pending(uid).none { it.runId == id })
    }
    @Test fun verifiedWorkerRestoresOfflineAndRetainsPendingResultsAcrossActivityRestart() = runBlocking<Unit> {
        val uid = container.auth.signIn("pilot-worker-a@samimine.example", "Pilot-Emulator-2026!")
        container.sessionCache.save(container.users.profile(uid))
        assertNull(container.sessionCache.restore("pilot-worker-b"))
        container.firestore.disableNetwork().await()
        try {
            activity = ActivityScenario.launch(MainActivity::class.java)
            waitText("Turno Día")
            compose.onNodeWithText("Turno Día").performClick()
            compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().performClick()
            compose.onNodeWithText("Comenzar prueba").performClick()
            confirmUnansweredPvt()
            compose.waitUntil(40_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
            val pendingIds = container.database(uid).sync().pending(uid).map { it.runId }.toSet()
            assertTrue(pendingIds.isNotEmpty())
            activity!!.close()
            activity = ActivityScenario.launch(MainActivity::class.java)
            waitText("Turno Día")
            compose.onNodeWithText("Turno Día").performClick()
            compose.onNodeWithText("Historial").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Prueba de reacción · 3 de 3 señales").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(container.database(uid).sync().pending(uid).map { it.runId }.containsAll(pendingIds))
        } finally { container.firestore.enableNetwork().await() }
        assertTrue(container.sync(uid).sendPending(uid))
        assertTrue(container.database(uid).sync().pending(uid).isEmpty())
    }
    @Test fun incorrectCredentialsMissingProfileAndDeactivatedProfileAreRejected() = runBlocking<Unit> {
        val incorrect = runCatching { container.auth.signIn("pilot-worker-a@samimine.example", "incorrect-password") }.exceptionOrNull()
        assertTrue(incorrect is com.google.firebase.auth.FirebaseAuthException)
        assertNull(container.auth.currentUid)
        val orphan = container.firebaseAuth.createUserWithEmailAndPassword("orphan-${UUID.randomUUID()}@samimine.example", "Pilot-Emulator-2026!").await().user!!
        assertTrue(runCatching { container.users.profile(orphan.uid) }.exceptionOrNull() is AccessDenied)
        orphan.delete().await(); container.auth.signOut()
        container.auth.signIn("pilot-admin@samimine.example", "Pilot-Emulator-2026!")
        container.users.updateWorker("pilot-worker-c", "Trabajador C", "Demo", "Demo", false, null)
        try {
            container.auth.signOut()
            container.auth.signIn("pilot-worker-c@samimine.example", "Pilot-Emulator-2026!")
            assertTrue(runCatching { container.users.profile("pilot-worker-c") }.exceptionOrNull() is AccessDenied)
            assertNull(container.sessionCache.restore("pilot-worker-c"))
        } finally {
            container.auth.signOut()
            container.auth.signIn("pilot-admin@samimine.example", "Pilot-Emulator-2026!")
            container.users.updateWorker("pilot-worker-c", "Trabajador C", "Área de prueba", "Demostración", true, null)
        }
    }
    @Test fun administratorPublishesAndWorkerCachesRunsAndSynchronizesPinnedBattery() = runBlocking<Unit> {
        container.auth.signIn("pilot-admin@samimine.example", "Pilot-Emulator-2026!")
        val configurations = FirebaseBatteryConfigurationRepository(container.firestore, "pilot-mine")
        val originalDraft = configurations.loadDraft()
        val edited = configurations.saveDraft(originalDraft.copy(name = "Batería de aceptación", modules = listOf("PVT")))
        assertTrue(runCatching { configurations.saveDraft(originalDraft.copy(modules = listOf("DSST"))) }.isFailure)
        val published = configurations.publish(edited)
        container.auth.signOut()
        container.auth.signIn("pilot-worker-a@samimine.example", "Pilot-Emulator-2026!")
        val local = container.database("pilot-worker-a")
        val repo = RoomEvaluationRepository(local)
        repo.recoverInterrupted(); repo.recoverCognitiveTests(); repo.recoverGuidedBatteries()
        local.guided().all("pilot-worker-a").map { it.toDomain() }.filter { !it.terminal }.forEach {
            repo.setGuidedState(it.id, it.workerId, BatteryRunStatus.ABANDONED)
        }
        val downloaded = configurations.workerDefinition(local, false).first!!
        assertEquals(published, downloaded)
        assertEquals(downloaded, configurations.workerDefinition(local, true).first)
        val id = "guided-acceptance-${UUID.randomUUID()}"
        val run = GuidedBatteryRun(id, "pilot-worker-a", Shift.DAY, System.currentTimeMillis(), downloaded)
        repo.startGuided(run)
        val profile = container.users.profile("pilot-worker-a")
        val evaluation = Evaluation("pvt-$id", profile.worker(), Shift.DAY, System.currentTimeMillis(), PvtConfiguration())
        repo.startGuidedPvt(id, 0, evaluation)
        repeat(3) { repo.saveAttempt(evaluation.id, Attempt(it + 1, AttemptOutcome.OMISSION)) }
        repo.finish(evaluation.id)
        assertEquals(BatteryRunStatus.COMPLETED, repo.refreshGuided(id, "pilot-worker-a").status)
        assertTrue(container.sync("pilot-worker-a").sendPending("pilot-worker-a"))
        assertEquals("SYNCED", local.sync().state("BATTERY", id, "pilot-worker-a"))
        val resultPath = "users/pilot-worker-a/results/BATTERY_$id"
        val delivered = container.firestore.document(resultPath).get(Source.SERVER).await()
        assertEquals(true, delivered.getBoolean("complete"))
        container.auth.signOut()
        container.auth.signIn("pilot-wellbeing@samimine.example", "Pilot-Emulator-2026!")
        assertTrue(container.firestore.document(resultPath).get(Source.SERVER).await().exists())
        assertTrue(container.firestore.document("$resultPath/chunks/0").get(Source.SERVER).await().exists())
        container.auth.signOut()
        container.auth.signIn("pilot-admin@samimine.example", "Pilot-Emulator-2026!")
        assertTrue(runCatching { container.firestore.document(resultPath).get(Source.SERVER).await() }.isFailure)
        assertTrue(container.firestore.document("users/pilot-worker-a/tracking/BATTERY_$id").get(Source.SERVER).await().exists())
        val nextDraft = configurations.saveDraft(configurations.loadDraft().copy(modules = listOf("DSST")))
        val updated = configurations.publish(nextDraft)
        assertTrue(updated.version > published.version)
        assertEquals(listOf("PVT"), repo.guided(id, "pilot-worker-a")!!.definition.modules)
    }
    @Test fun administratorEditsAssignmentsAndPublishesThroughManagementScreens() = runBlocking<Unit> {
        container.auth.signIn("pilot-admin@samimine.example", "Pilot-Emulator-2026!")
        val original = container.users.profile("pilot-worker-b")
        val configurations = FirebaseBatteryConfigurationRepository(container.firestore, "pilot-mine")
        val previousVersion = configurations.download()?.version ?: 0
        val batteryName = "Batería de aceptación ${UUID.randomUUID().toString().take(8)}"
        activity = ActivityScenario.launch(MainActivity::class.java)
        waitText("Administrador / supervisor")
        try {
            compose.onNodeWithText("Trabajador B · Activo").performScrollTo().performClick()
            compose.onNodeWithText("Editar perfil y asignación").performScrollTo().performClick()
            compose.onNodeWithText("Área").performTextReplacement("Área de aceptación UI")
            compose.onNodeWithText("Cambiar asignación").performScrollTo().performClick()
            compose.onNodeWithText("Sin asignación").performClick()
            compose.onNodeWithText("Guardar cambios").performClick()
            waitText("Perfil y asignación guardados")
            val updated = container.users.profile(original.uid)
            assertEquals("Área de aceptación UI", updated.area)
            assertNull(updated.wellbeingUid)
            compose.onNodeWithTag("management-tab-batteries").performClick()
            waitText("Nombre de la batería")
            compose.onNodeWithText("Nombre de la batería").performTextReplacement(batteryName)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Guardar borrador"))
            compose.onNodeWithText("Guardar borrador").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Publicar nueva versión"))
            compose.waitUntil(15_000) { runCatching { compose.onNodeWithText("Publicar nueva versión").assertIsEnabled() }.isSuccess }
            compose.onNodeWithText("Publicar nueva versión").performClick()
            compose.onNodeWithText("¿Publicar nueva versión?").assertIsDisplayed()
            assertEquals(previousVersion, configurations.download()?.version ?: 0)
            compose.onNodeWithText("Publicar", substring = false).performClick()
            val published = withTimeout(15_000) {
                var value = configurations.download()
                while (value == null || value.version <= previousVersion) { delay(200); value = configurations.download() }
                value
            }
            assertEquals(batteryName, published.name)
        } finally {
            container.users.updateWorker(original.uid, original.name, original.area, original.employment, original.active, original.wellbeingUid)
        }
    }
    @Test fun revokedAssignmentImmediatelyRemovesAnOpenWellbeingResult() = runBlocking<Unit> {
        val uid = container.auth.signIn("pilot-worker-a@samimine.example", "Pilot-Emulator-2026!")
        val repository = RoomEvaluationRepository(container.database(uid))
        val id = "revocation-${UUID.randomUUID()}"
        repository.start(Evaluation(id, Worker(uid, "Trabajador A", "Demo", "Demo"), Shift.DAY, System.currentTimeMillis(), PvtConfiguration()))
        repeat(3) { repository.saveAttempt(id, Attempt(it + 1, AttemptOutcome.OMISSION)) }
        repository.finish(id)
        assertTrue(container.sync(uid).sendPending(uid))
        container.auth.signOut()
        container.auth.signIn("pilot-wellbeing@samimine.example", "Pilot-Emulator-2026!")
        activity = ActivityScenario.launch(MainActivity::class.java)
        waitText("Trabajador A · Activo")
        compose.onNodeWithText("Trabajador A · Activo").performScrollTo().performClick()
        waitText("Ver resultado y respuestas")
        compose.onAllNodesWithText("Ver resultado y respuestas").onFirst().performScrollTo().performClick()
        waitText("Resultado de actividad")
        val options = FirebaseOptions.Builder(FirebaseApp.getInstance().options).setProjectId("demo-samimine").build()
        val control = FirebaseApp.initializeApp(context, options, "revocation-control")
        val auth = FirebaseAuth.getInstance(control)
        val firestore = FirebaseFirestore.getInstance(control)
        val args = InstrumentationRegistry.getArguments()
        auth.useEmulator("127.0.0.1", args.getString("authPort")?.toInt() ?: 9099)
        firestore.useEmulator("127.0.0.1", args.getString("firestorePort")?.toInt() ?: 8080)
        auth.signInWithEmailAndPassword("pilot-admin@samimine.example", "Pilot-Emulator-2026!").await()
        try {
            firestore.document("users/$uid").update("wellbeingUid", null).await()
            waitText("La asignación cambió. Actualiza la lista de trabajadores.")
            compose.onNodeWithText("Resultado de actividad").assertDoesNotExist()
            compose.onNodeWithText("Trabajador A · Activo").assertDoesNotExist()
            compose.onNodeWithText("Trabajadores asignados").assertExists()
        } finally {
            firestore.document("users/$uid").update("wellbeingUid", "pilot-wellbeing").await()
            auth.signOut(); firestore.terminate().await(); control.delete()
        }
    }
    companion object { private var configured = false }
}
