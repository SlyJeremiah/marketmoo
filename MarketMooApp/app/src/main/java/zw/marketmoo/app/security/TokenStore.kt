package zw.marketmoo.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the API token encrypted with an Android Keystore key (AES-GCM); the plain token never touches disk. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("marketmoo_session", Context.MODE_PRIVATE)

    fun save(token: String) {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val blob = c.iv + c.doFinal(token.toByteArray())
        prefs.edit().putString(KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun load(): String? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            String(c.doFinal(blob, 12, blob.size - 12))
        } catch (e: Exception) {
            clear() // key lost (for example after a restore): force a new sign-in
            null
        }
    }

    fun clear() = prefs.edit().remove(KEY).apply()

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

    private companion object {
        const val ALIAS = "marketmoo_token_key"
        const val KEY = "token_blob"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
