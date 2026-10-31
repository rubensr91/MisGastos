package com.mfreya.misgastos.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clave con la que SQLCipher cifra la base de datos.
 *
 * La clave de la base de datos es aleatoria y distinta en cada instalacion. Se guarda cifrada
 * con una clave AES-256 del Android Keystore: el Keystore no exporta la clave maestra, asi que
 * una copia del fichero de preferencias (o de la propia base de datos) no permite leer nada
 * fuera de este dispositivo. Al borrar los datos de la app, Android elimina a la vez la clave
 * del Keystore y la base, asi que no queda nada recuperable.
 */
internal object DbPassphrase {

    private const val PREFS = "mis_gastos_db_secret"
    private const val KEY_WRAPPED = "wrapped_passphrase"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "mis_gastos_db_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    /**
     * Devuelve la clave de la base de datos, creandola la primera vez. El resultado son 64
     * caracteres ASCII en hexadecimal que SQLCipher usa como contrasena.
     */
    fun getOrCreate(context: Context): ByteArray = synchronized(this) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_WRAPPED, null)
        if (stored != null) return@synchronized unwrap(stored)

        val random = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val passphrase = random.joinToString(separator = "") {
            "%02x".format(Locale.ROOT, it)
        }.toByteArray()
        // commit() y no apply(): la clave tiene que estar en disco antes de crear la base.
        check(prefs.edit().putString(KEY_WRAPPED, wrap(passphrase)).commit()) {
            "No se pudo guardar la clave de la base de datos"
        }
        passphrase
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(passphrase)
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun unwrap(stored: String): ByteArray {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            // Sin la clave del Keystore la base es ilegible a proposito. No se genera otra:
            // eso dejaria los gastos encerrados en un fichero que ya no se puede abrir.
            throw IllegalStateException("No se pudo recuperar la clave de la base de datos", error)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private const val GCM_IV_BYTES = 12
}