package biz.pixelperfectstudios.personaspeak.data

import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.UnrecoverableKeyException
import java.util.Collections
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cipher-level tests for [KeystoreSecretCipher] itself — not a fake standing
 * in for it. Uses an injectable KeyStore seam to prove the real implementation
 * upholds the store contract: every KeyStore/Provider failure maps to
 * [CipherUnavailableException] (→ `Unavailable`, no mutation), on both the
 * existing-key fast path and key generation, on encrypt and decrypt.
 *
 * The success path exercises real software AES-GCM (JCE default provider),
 * so round-trip coverage here is genuine crypto, unlike the XOR fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreSecretCipherTest {

    private val secret = SecretBytes("real-cipher-credential-42".toByteArray())
    private val generation = DataStoreProviderConfigStore.generationUuidBytes(
        "00000000-0000-0000-0000-00000000abcd",
    )

    @Test
    fun `keystore load failure maps to CipherUnavailableException on encrypt`() {
        val cipher = KeystoreSecretCipher(sdkInt = 34) { stubKeyStore(loadFailure = IOException("keystore locked")) }

        val ex = assertThrows(CipherUnavailableException::class.java) {
            cipher.encrypt(secret, generation)
        }
        assertTrue(ex.cause is IOException)
        assertEquals("keystore locked", ex.cause!!.message)
    }

    @Test
    fun `keystore load failure maps to CipherUnavailableException on decrypt`() {
        val cipher = KeystoreSecretCipher(sdkInt = 34) { stubKeyStore(loadFailure = IOException("keystore locked")) }

        val ex = assertThrows(CipherUnavailableException::class.java) {
            cipher.decrypt(wellFormedHeaderBlob())
        }
        assertEquals("keystore locked", ex.cause!!.message)
    }

    @Test
    fun `getKey failure on the existing-key fast path maps to CipherUnavailableException`() {
        val cipher = KeystoreSecretCipher(sdkInt = 34) {
            stubKeyStore(getKeyFailure = UnrecoverableKeyException("key swept by OEM cleanup"))
        }

        // The fast-path failure fires before key generation is ever attempted.
        val ex = assertThrows(CipherUnavailableException::class.java) {
            cipher.encrypt(secret, generation)
        }
        assertTrue(ex.cause is UnrecoverableKeyException)
    }

    @Test
    fun `existing key fast path round-trips through real AES-GCM`() {
        val aesKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val cipher = KeystoreSecretCipher(sdkInt = 26) { stubKeyStore(existingKey = aesKey) }

        val blob = cipher.encrypt(secret, generation)
        val decrypted = cipher.decrypt(blob)

        assertArrayEquals(secret.value, decrypted!!.value)
    }

    @Test
    fun `decrypt of a tampered blob fails closed without throwing`() {
        val aesKey = SecretKeySpec(ByteArray(32) { 5 }, "AES")
        val cipher = KeystoreSecretCipher(sdkInt = 26) { stubKeyStore(existingKey = aesKey) }
        val blob = cipher.encrypt(secret, generation).also { it[it.size - 1] = 0 }

        assertNull(cipher.decrypt(blob))
    }

    // ---- helpers -------------------------------------------------------------

    /** A syntactically valid header-only blob for decrypt-entry-point tests. */
    private fun wellFormedHeaderBlob(): ByteArray =
        BlobFormat.MAGIC + byteArrayOf(BlobFormat.VERSION) + generation +
            ByteArray(BlobFormat.IV_BYTES) + ByteArray(16)

    private fun stubKeyStore(
        existingKey: SecretKey? = null,
        loadFailure: Exception? = null,
        getKeyFailure: Exception? = null,
    ): KeyStore = StubKeyStore(StubKeyStoreSpi(existingKey, loadFailure, getKeyFailure))
}

/**
 * Protected-constructor wrapper so the test can hand the cipher a KeyStore
 * backed by our SPI (all behavior lives in the SPI; KeyStore delegates).
 */
private class StubKeyStore(spi: KeyStoreSpi) : KeyStore(spi, null as java.security.Provider?, "StubKS")

/**
 * Minimal KeyStoreSpi double: implements exactly what [KeystoreSecretCipher]
 * touches (`engineLoad` via `load(null)` / `engineGetKey` via `getKey`) and
 * fails nothing else politely.
 */
private open class StubKeyStoreSpi(
    private val existingKey: SecretKey?,
    private val loadFailure: Exception?,
    private val getKeyFailure: Exception?,
) : KeyStoreSpi() {

    override fun engineLoad(stream: InputStream?, password: CharArray?) {
        loadFailure?.let { throw it }
    }

    override fun engineLoad(param: KeyStore.LoadStoreParameter?) {
        loadFailure?.let { throw it }
    }

    override fun engineGetKey(alias: String?, password: CharArray?): Key? {
        getKeyFailure?.let { throw it }
        return existingKey ?: throw UnrecoverableKeyException("no key under $alias")
    }

    override fun engineSize(): Int = if (existingKey != null) 1 else 0

    override fun engineAliases(): java.util.Enumeration<String> = Collections.emptyEnumeration()

    override fun engineContainsAlias(alias: String?): Boolean = false

    override fun engineIsCertificateEntry(alias: String?): Boolean = false

    override fun engineIsKeyEntry(alias: String?): Boolean = existingKey != null

    override fun engineGetCertificate(alias: String?): java.security.cert.Certificate? = null

    override fun engineGetCertificateChain(alias: String?): Array<java.security.cert.Certificate> = arrayOf()

    override fun engineGetCertificateAlias(cert: java.security.cert.Certificate?): String? = null

    override fun engineGetCreationDate(alias: String?) = null

    override fun engineSetKeyEntry(
        alias: String?,
        key: Key?,
        password: CharArray?,
        chain: Array<java.security.cert.Certificate>?,
    ) = unsupported()

    override fun engineSetKeyEntry(
        alias: String?,
        key: ByteArray?,
        chain: Array<java.security.cert.Certificate>?,
    ) = unsupported()

    override fun engineSetCertificateEntry(alias: String?, cert: java.security.cert.Certificate?) = unsupported()

    override fun engineDeleteEntry(alias: String?) = unsupported()

    override fun engineStore(stream: OutputStream?, password: CharArray?) = unsupported()

    override fun engineStore(param: KeyStore.LoadStoreParameter?) = unsupported()

    private fun unsupported(): Nothing = throw java.security.KeyStoreException("unsupported by stub")
}
