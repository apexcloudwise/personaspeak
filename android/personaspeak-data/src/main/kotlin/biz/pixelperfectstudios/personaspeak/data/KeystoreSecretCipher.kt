package biz.pixelperfectstudios.personaspeak.data

import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log

/**
 * Thrown when the KeyStore itself is unusable (device keystore broken, key
 * deleted by OEM "cleanup"). The store maps this to `Unavailable` — a
 * transient device condition, not proof of corruption.
 */
class CipherUnavailableException(cause: Throwable) : RuntimeException(cause)

/** Fixed-size blob header: magic ‖ version ‖ 16-byte generation UUID. */
object BlobFormat {
    val MAGIC: ByteArray = byteArrayOf('P'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    const val VERSION: Byte = 1
    const val GENERATION_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    val HEADER_BYTES: Int = MAGIC.size + 1 + GENERATION_BYTES + IV_BYTES
    const val GCM_TAG_BITS: Int = 128

    /** Extracts the generation UUID bytes, or null if the blob is not ours. */
    fun generationOf(blob: ByteArray): ByteArray? {
        if (blob.size < HEADER_BYTES) return null
        for (i in MAGIC.indices) {
            if (blob[i] != MAGIC[i]) return null
        }
        if (blob[MAGIC.size] != VERSION) return null
        return blob.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + GENERATION_BYTES)
    }
}

/** Encryption seam so the store's recovery matrix is testable without hardware. */
interface SecretCipher {
    fun encrypt(secret: SecretBytes, generation: ByteArray): ByteArray

    /**
     * Decrypts a blob. Returns null for corrupt/tampered/unparseable blobs
     * (the fail-closed `InvalidCredentials` path); throws
     * [CipherUnavailableException] only when the KeyStore itself is broken.
     */
    fun decrypt(blob: ByteArray): SecretBytes?
}

/**
 * AndroidKeyStore AES-256-GCM cipher. The key never leaves the keystore;
 * plaintext exists only inside [decrypt]'s return value and the caller's
 * request-scoped use.
 *
 * StrongBox is requested only on API >= 28 ([KeyStrengthPolicy]) and falls
 * back to the default TEE-backed key when unavailable. Logging is fixed-code
 * only — no key material, no config values, ever.
 */
class KeystoreSecretCipher(
    private val sdkInt: Int,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val keyStoreProvider: () -> KeyStore = { KeyStore.getInstance(ANDROID_KEYSTORE) },
) : SecretCipher {

    override fun encrypt(secret: SecretBytes, generation: ByteArray): ByteArray {
        require(generation.size == BlobFormat.GENERATION_BYTES)
        val iv = ByteArray(BlobFormat.IV_BYTES).also { SecureRandom().nextBytes(it) }
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(secret.value)
        return BlobFormat.MAGIC + byteArrayOf(BlobFormat.VERSION) + generation + iv + ciphertext
    }

    override fun decrypt(blob: ByteArray): SecretBytes? {
        val generation = BlobFormat.generationOf(blob) ?: return null
        val iv = blob.copyOfRange(
            BlobFormat.MAGIC.size + 1 + BlobFormat.GENERATION_BYTES,
            BlobFormat.HEADER_BYTES,
        )
        val ciphertext = blob.copyOfRange(BlobFormat.HEADER_BYTES, blob.size)
        return try {
            val plain = cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext)
            SecretBytes(plain)
        } catch (e: CipherUnavailableException) {
            throw e
        } catch (e: Exception) {
            // AEADBadTagException and friends: the blob is corrupt or was not
            // encrypted by this key. That is InvalidCredentials, not Unavailable.
            Log.w(TAG, "DECRYPT_FAILED")
            null
        }
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher {
        val key = obtainKey()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, key, GCMParameterSpec(BlobFormat.GCM_TAG_BITS, iv))
        }
    }

    private fun obtainKey(): SecretKey {
        // Fast path: the steady-state retrieval once a key exists. Any
        // KeyStore/Provider failure here is a device condition (broken or
        // locked keystore, OEM key cleanup), not proof of corruption — it must
        // surface as CipherUnavailableException so the store reports
        // Unavailable without mutating artifacts.
        val keyStore = try {
            keyStoreProvider().apply { load(null) }
        } catch (e: Exception) {
            throw CipherUnavailableException(e)
        }
        val existing = try {
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (e: Exception) {
            throw CipherUnavailableException(e)
        }
        if (existing != null) return existing

        val wantStrongBox = KeyStrengthPolicy.requestStrongBox(sdkInt)
        return try {
            if (wantStrongBox) generateKey(strongBox = true) else generateKey(strongBox = false)
        } catch (e: Exception) {
            if (!wantStrongBox) throw CipherUnavailableException(e)
            Log.w(TAG, "STRONGBOX_UNAVAILABLE_FALLBACK")
            try {
                // Fresh spec: the failed attempt must not leave its StrongBox
                // request behind on the retry.
                generateKey(strongBox = false)
            } catch (e2: Exception) {
                throw CipherUnavailableException(e2)
            }
        }
    }

    private fun newSpecBuilder(): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(false)

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)
    private fun generateKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = newSpecBuilder()
        if (strongBox) {
            // Only reachable when KeyStrengthPolicy.requestStrongBox(sdkInt)
            // is true, i.e. sdkInt >= 28.
            builder.setIsStrongBoxBacked(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS: String = "personaspeak_provider_credential_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG = "KeystoreSecretCipher"
    }
}
