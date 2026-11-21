package dev.studios.samimine.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** El dispositivo ya no puede descifrar sus datos locales (clave de Keystore perdida o alterada). */
class LocalKeyUnavailable(cause: Throwable) :
    IllegalStateException("No se pudo abrir el almacenamiento cifrado de este dispositivo.", cause)

/**
 * Cifrado local con una clave AES-256-GCM no exportable guardada en Android Keystore.
 *
 * - [encrypt]/[decrypt] protegen valores pequeños, como el perfil del trabajador verificado.
 * - [databasePassphrase] entrega la frase de SQLCipher: 32 bytes aleatorios en hexadecimal,
 *   guardados envueltos (cifrados) con la clave de Keystore; nunca se escriben en claro.
 *
 * La clave no exige autenticación del usuario ni dispositivo desbloqueado: WorkManager debe
 * poder abrir la base con la pantalla bloqueada para sincronizar pendientes (RNF-06).
 */
class LocalCipher(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var cachedPassphrase: ByteArray? = null

    fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "IV inesperado: ${iv.size} bytes" }
        return FORMAT_PREFIX + Base64.encodeToString(iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    /** Lanza [GeneralSecurityException] si el valor fue alterado o la clave ya no coincide. */
    fun decrypt(token: String): ByteArray {
        if (!token.startsWith(FORMAT_PREFIX)) throw GeneralSecurityException("Formato cifrado desconocido")
        val sealed = try {
            Base64.decode(token.removePrefix(FORMAT_PREFIX), Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("Base64 inválido", error)
        }
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("Valor cifrado incompleto")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    /** Devuelve una copia nueva en cada llamada para que nadie altere la frase en memoria. */
    @Synchronized
    fun databasePassphrase(): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }
        val stored = preferences.getString(DATABASE_PASSPHRASE, null)
        val passphrase = if (stored == null) {
            val random = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
            val hex = random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
            random.fill(0)
            check(preferences.edit().putString(DATABASE_PASSPHRASE, encrypt(hex)).commit()) {
                "No se pudo guardar la clave de la base local"
            }
            hex
        } else {
            // Si la clave de Keystore se perdió, no se regenera: eso dejaría ilegibles los pendientes.
            try { decrypt(stored) } catch (error: GeneralSecurityException) { throw LocalKeyUnavailable(error) }
        }
        cachedPassphrase = passphrase
        return passphrase.copyOf()
    }

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }.generateKey()
    }

    private companion object {
        val KEY_LOCK = Any()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "samimine-local-data-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val PASSPHRASE_BYTES = 32
        const val FORMAT_PREFIX = "v1:"
        const val PREFERENCES = "samimine-local-keys"
        const val DATABASE_PASSPHRASE = "database-passphrase"
    }
}
