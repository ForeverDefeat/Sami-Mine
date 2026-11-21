package dev.studios.samimine.data.local.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.IOException

/**
 * Abre Room sobre SQLCipher.
 *
 * Room llama a [create] al construir la base, a menudo en el hilo principal. Por eso la frase
 * (que pasa por Keystore) y el cifrado de bases antiguas se resuelven recién en la primera
 * apertura, que Room hace en su hilo de E/S.
 */
class EncryptedOpenHelperFactory(
    private val context: Context,
    private val passphrase: () -> ByteArray,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        LazyEncryptedOpenHelper(context.applicationContext, configuration, passphrase)
}

private class LazyEncryptedOpenHelper(
    private val context: Context,
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val passphrase: () -> ByteArray,
) : SupportSQLiteOpenHelper {
    @Volatile private var writeAheadLogging: Boolean? = null

    private val delegate = lazy {
        SqlCipherLibrary.load()
        val key = SqlCipherKey.raw(passphrase())
        configuration.name?.let { PlaintextDatabaseEncryptor.encryptIfNeeded(context.getDatabasePath(it), key) }
        SupportOpenHelperFactory(key).create(configuration).also { helper ->
            writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
        }
    }

    override val databaseName: String? get() = configuration.name
    override val writableDatabase: SupportSQLiteDatabase get() = delegate.value.writableDatabase
    override val readableDatabase: SupportSQLiteDatabase get() = delegate.value.readableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        writeAheadLogging = enabled
        if (delegate.isInitialized()) delegate.value.setWriteAheadLoggingEnabled(enabled)
    }

    override fun close() {
        if (delegate.isInitialized()) delegate.value.close()
    }
}

/**
 * Convierte la frase (64 caracteres hexadecimales = 32 bytes aleatorios de Keystore) en una
 * clave raw de SQLCipher `x'…'`. Así SQLCipher usa la clave tal cual y omite PBKDF2: el
 * estiramiento solo protege contraseñas humanas y aquí costaba cientos de ms por conexión.
 */
internal object SqlCipherKey {
    private val HEX_KEY = Regex("[0-9a-f]{64}")

    fun raw(hexPassphrase: ByteArray): ByteArray {
        val hex = String(hexPassphrase, Charsets.US_ASCII)
        require(HEX_KEY.matches(hex)) { "La clave local debe tener 64 caracteres hexadecimales" }
        return "x'$hex'".toByteArray(Charsets.US_ASCII)
    }
}

internal object SqlCipherLibrary {
    private val loaded = lazy { System.loadLibrary("sqlcipher") }
    fun load() = loaded.value
}

/**
 * Convierte una base Room creada sin cifrar (versiones anteriores de la app) en una base
 * SQLCipher con `sqlcipher_export`, conservando tablas, datos y `user_version`.
 *
 * El original solo se borra cuando la copia cifrada está completa. Si el proceso muere entre
 * el borrado y el renombre, la siguiente apertura termina el renombre.
 */
internal object PlaintextDatabaseEncryptor {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val SIDE_FILES = listOf("-wal", "-shm", "-journal")

    fun isPlaintextHeader(header: ByteArray): Boolean =
        header.size >= SQLITE_HEADER.size && SQLITE_HEADER.indices.all { header[it] == SQLITE_HEADER[it] }

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile) return false
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { input ->
            var total = 0
            while (total < header.size) {
                val count = input.read(header, total, header.size - total)
                if (count < 0) break
                total += count
            }
            total
        }
        return read == header.size && isPlaintextHeader(header)
    }

    fun encryptIfNeeded(file: File, passphrase: ByteArray) {
        val target = File(file.parentFile, "${file.name}.encrypting")
        if (!file.exists() && target.isFile) {
            // Exportación completa de un intento anterior interrumpido justo antes del renombre.
            rename(target, file)
            return
        }
        if (!isPlaintext(file)) return

        deleteWithSideFiles(target)
        SqlCipherLibrary.load()
        try {
            // Contraseña vacía = abrir sin clave, que es como Room creó las bases anteriores.
            val plain = SQLiteDatabase.openDatabase(file.path, "", null, SQLiteDatabase.OPEN_READWRITE, null, null)
            val version = try { plain.version } finally { plain.close() }

            // Se crea primero la base cifrada (main) y se le adjunta la antigua con clave vacía;
            // así el archivo destino existe aunque la exportación falle y el error es explícito.
            val encrypted = SQLiteDatabase.openOrCreateDatabase(target, passphrase, null, null)
            try {
                // ATTACH no admite parámetros enlazados aquí: la ruta va como literal escapado.
                encrypted.execSQL("ATTACH DATABASE ${sqlLiteral(file.path)} AS plaintext KEY ''")
                encrypted.rawQuery("SELECT sqlcipher_export('main', 'plaintext')", emptyArray<String>()).use { it.moveToFirst() }
                encrypted.execSQL("DETACH DATABASE plaintext")
                // sqlcipher_export no copia user_version; sin él Room creería que la base es nueva.
                encrypted.version = version
                val tables = encrypted.rawQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table'", emptyArray<String>())
                    .use { if (it.moveToFirst()) it.getInt(0) else 0 }
                if (tables == 0) throw IOException("La copia cifrada quedó sin tablas")
            } finally {
                encrypted.close()
            }
        } catch (error: Exception) {
            deleteWithSideFiles(target)
            throw IOException("No se pudo cifrar la base local ${file.name}", error)
        }

        deleteWithSideFiles(file)
        File(target.path + "-journal").delete()
        rename(target, file)
    }

    internal fun sqlLiteral(value: String): String = "'" + value.replace("'", "''") + "'"

    private fun deleteWithSideFiles(file: File) {
        file.delete()
        SIDE_FILES.forEach { File(file.path + it).delete() }
    }

    private fun rename(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("No se pudo reemplazar la base local ${to.name}")
    }
}
