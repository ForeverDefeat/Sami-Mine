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
            FirebaseAuth.getInstance(app).useEmulator("127.0.0.1", 9099)
            FirebaseFirestore.getInstance(app).useEmulator("127.0.0.1", 8080); configured = true
        }
        container.auth.signOut(); container.sessionCache.clear()
    }
    @After fun cleanup() { activity?.close(); if (PilotEnvironment.firebaseApp != null) container.auth.signOut(); PilotEnvironment.demoTests = true }
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun login(uid: String) {
        waitText("Correo electrónico")
        compose.onNodeWithText("Correo electrónico").performTextInput("$uid@samimine.example")
        compose.onNodeWithText("Contraseña").performTextInput("Pilot-Emulator-2026!")
        compose.onNodeWithText("Ingresar").performClick()
    }
    private fun logout() {
        compose.onAllNodesWithText("Cerrar sesión").onFirst().performClick()
        waitText("Correo electrónico")
    }
    @Test fun workerEvaluationSynchronizesAndOtherRolesOnlySeeTheirPermissions() = runBlocking {
        activity = ActivityScenario.launch(MainActivity::class.java)
        login("pilot-worker-a")
        waitText("Turno Día")
        compose.onNodeWithText("Turno Día").performClick()
        compose.onNodeWithText("Iniciar prueba de reacción").performScrollTo().performClick()
        compose.onNodeWithText("Comenzar prueba").performClick()
        compose.waitUntil(40_000) { compose.onAllNodesWithText("Demostración completada").fetchSemanticsNodes().isNotEmpty() }
        val uid = container.auth.currentUid!!
        assertEquals("pilot-worker-a", uid)
        assertTrue(container.sync(uid).sendPending(uid))
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
        compose.onAllNodesWithText("Ver resultado y respuestas").onFirst().performClick()
        waitText("Respuestas registradas")
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
    @Test fun accountSwitchDoesNotSendOrExposeAnotherWorkersQueue() = runBlocking {
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
    @Test fun verifiedWorkerRestoresOfflineAndRetainsPendingResultsAcrossActivityRestart() = runBlocking {
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
    @Test fun incorrectCredentialsMissingProfileAndDeactivatedProfileAreRejected() = runBlocking {
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
    companion object { private var configured = false }
}
