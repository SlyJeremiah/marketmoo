package zw.marketmoo.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Produces the SQLCipher passphrase. A random 32-byte secret is generated once, encrypted with an
 * AES-GCM key that never leaves the Android Keystore, and stored in private preferences.
 */
object KeystoreBackedPassphrase {
    private const val ALIAS = "marketmoo_db_key"
    private const val PREFS = "marketmoo_secure"
    private const val KEY_BLOB = "db_pass_blob"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    fun get(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val blob = prefs.getString(KEY_BLOB, null)
        if (blob != null) return decrypt(Base64.decode(blob, Base64.NO_WRAP))
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(encrypt(secret), Base64.NO_WRAP)).apply()
        return secret
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain) // 12-byte IV prefix
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}
