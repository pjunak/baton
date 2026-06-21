package eu.junak.baton.core.network.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts small secrets (the session cookie) with an AES-256/GCM key kept in
 * the Android Keystore — the key never leaves secure hardware and only ciphertext
 * is persisted. This replaces the deprecated EncryptedSharedPreferences.
 *
 * Both calls are tolerant: a corrupt blob or a key that was invalidated (e.g. the
 * user reset their lock screen) yields null, which the cookie jar treats as
 * "no stored session" — i.e. the user is asked to sign in again.
 */
@Singleton
class SecureStore @Inject constructor() {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /** Encrypts [plaintext] to base64(iv ‖ ciphertext); null on failure. */
    fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val combined = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }.getOrNull()

    /** Reverses [encrypt]; null if the blob is missing/corrupt or the key rotated. */
    fun decrypt(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "baton_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
