package dev.studios.samimine.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import dev.studios.samimine.data.local.database.EncryptedOpenHelperFactory
import dev.studios.samimine.data.local.database.PlaintextDatabaseEncryptor
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.local.entity.SyncEntry
import dev.studios.samimine.data.local.security.LocalCipher
import dev.studios.samimine.data.remote.WorkerSessionCache
import dev.studios.samimine.domain.model.UserProfile
import dev.studios.samimine.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID

/** Verifica el cifrado local en un dispositivo real o emulador (Keystore + SQLCipher). */
class LocalEncryptionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cipher = LocalCipher(context)
    private val names = mutableListOf<String>()

    @After fun cleanUp() {
        names.forEach { context.deleteDatabase(it) }
        context.getSharedPreferences("verified-worker", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun cipherRoundTripsAndRejectsTamperedValues() {
        val sealed = cipher.encrypt("dato sensible".toByteArray())
        assertFalse(sealed.contains("dato sensible"))
        assertEquals("dato sensible", String(cipher.decrypt(sealed)))
        assertNotEquals("Cada cifrado usa un IV nuevo", sealed, cipher.encrypt("dato sensible".toByteArray()))

        val tampered = sealed.dropLast(2) + if (sealed.endsWith("AA")) "BB" else "AA"
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(tampered) }
    }

    @Test fun databasePassphraseIsStableAndNeverStoredInPlaintext() {
        val first = cipher.databasePassphrase()
        assertArrayEquals("Otra instancia recupera la misma frase desde Keystore", first, LocalCipher(context).databasePassphrase())
        val stored = File(context.applicationInfo.dataDir, "shared_prefs/samimine-local-keys.xml").readText()
        assertFalse(stored.contains(String(first, Charsets.US_ASCII)))
    }

    @Test fun newDatabaseIsEncryptedOnDisk() = runBlocking {
        val name = newName()
        encrypted(name).use { it.sync().enqueue(SyncEntry("PVT", "run-secreto", "worker-secreto")) }

        val file = context.getDatabasePath(name)
        assertFalse(PlaintextDatabaseEncryptor.isPlaintext(file))
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("worker-secreto"))
        encrypted(name).use { assertEquals(listOf("run-secreto"), it.sync().pending("worker-secreto").map(SyncEntry::runId)) }
    }

    @Test fun legacyPlaintextDatabaseIsEncryptedWithoutLosingData() = runBlocking {
        val name = newName()
        Room.databaseBuilder(context, SamiMineDatabase::class.java, name).build()
            .use { it.sync().enqueue(SyncEntry("PVT", "run-previo", "worker-previo")) }
        assertTrue(PlaintextDatabaseEncryptor.isPlaintext(context.getDatabasePath(name)))

        // Si user_version no se conservara, Room intentaría crear tablas existentes y fallaría aquí.
        encrypted(name).use { assertEquals(listOf("run-previo"), it.sync().pending("worker-previo").map(SyncEntry::runId)) }
        val file = context.getDatabasePath(name)
        assertFalse(PlaintextDatabaseEncryptor.isPlaintext(file))
        assertFalse(File(file.path + ".encrypting").exists())
    }

    @Test fun workerProfileIsStoredEncryptedAndLegacyCacheIsMigrated() {
        val preferences = context.getSharedPreferences("verified-worker", Context.MODE_PRIVATE)
        val profile = UserProfile("uid-1", "Nombre Reservado", "org-1", UserRole.WORKER, active = true, area = "Mina")
        preferences.edit().putString("profile", Gson().toJson(profile)).commit()

        val cache = WorkerSessionCache(context, cipher)
        assertEquals(profile, cache.restore("uid-1"))
        val migrated = checkNotNull(preferences.getString("profile", null))
        assertTrue(migrated.startsWith("v1:"))
        assertFalse(migrated.contains("Nombre Reservado"))
        assertEquals(profile, cache.restore("uid-1"))
        assertNull(cache.restore("otro-uid"))

        preferences.edit().putString("profile", "v1:no-es-un-valor-valido").commit()
        assertNull(cache.restore("uid-1"))
        assertNull("Un valor ilegible se descarta", preferences.getString("profile", null))
    }

    private fun newName() = "encryption-${UUID.randomUUID()}.db".also(names::add)

    private fun encrypted(name: String) = Room.databaseBuilder(context, SamiMineDatabase::class.java, name)
        .openHelperFactory(EncryptedOpenHelperFactory(context) { cipher.databasePassphrase() }).build()

    private inline fun <T> SamiMineDatabase.use(block: (SamiMineDatabase) -> T): T = try { block(this) } finally { close() }
}
