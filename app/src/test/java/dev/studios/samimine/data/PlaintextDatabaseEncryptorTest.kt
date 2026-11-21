package dev.studios.samimine.data

import dev.studios.samimine.data.local.database.PlaintextDatabaseEncryptor
import dev.studios.samimine.data.local.database.SqlCipherKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaintextDatabaseEncryptorTest {
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Test fun recognizesStandardSqliteHeader() {
        assertTrue(PlaintextDatabaseEncryptor.isPlaintextHeader(header + ByteArray(84)))
    }

    @Test fun treatsRandomOrShortContentAsNotPlaintext() {
        assertFalse(PlaintextDatabaseEncryptor.isPlaintextHeader(ByteArray(16) { (it * 37 + 11).toByte() }))
        assertFalse(PlaintextDatabaseEncryptor.isPlaintextHeader(header.copyOf(10)))
    }

    @Test fun buildsRawSqlCipherKeyOnlyFromHexPassphrase() {
        val hex = "0123456789abcdef".repeat(4)
        assertEquals("x'$hex'", String(SqlCipherKey.raw(hex.toByteArray(Charsets.US_ASCII)), Charsets.US_ASCII))
        assertThrows(IllegalArgumentException::class.java) { SqlCipherKey.raw("corta".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { SqlCipherKey.raw("Z".repeat(64).toByteArray()) }
    }

    @Test fun escapesSqlLiterals() {
        assertEquals("'/data/db/a.db'", PlaintextDatabaseEncryptor.sqlLiteral("/data/db/a.db"))
        assertEquals("'it''s'", PlaintextDatabaseEncryptor.sqlLiteral("it's"))
    }

    @Test fun inspectsFilesOnDisk() {
        val plain = File.createTempFile("plain", ".db").apply { writeBytes(header + ByteArray(100)); deleteOnExit() }
        val encrypted = File.createTempFile("cipher", ".db").apply { writeBytes(ByteArray(116) { (it * 13).toByte() }); deleteOnExit() }
        val empty = File.createTempFile("empty", ".db").apply { deleteOnExit() }
        assertTrue(PlaintextDatabaseEncryptor.isPlaintext(plain))
        assertFalse(PlaintextDatabaseEncryptor.isPlaintext(encrypted))
        assertFalse(PlaintextDatabaseEncryptor.isPlaintext(empty))
        assertFalse(PlaintextDatabaseEncryptor.isPlaintext(File(plain.parentFile, "no-existe-${System.nanoTime()}.db")))
    }
}
