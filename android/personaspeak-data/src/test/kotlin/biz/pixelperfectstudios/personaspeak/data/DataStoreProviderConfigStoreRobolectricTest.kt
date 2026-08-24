package biz.pixelperfectstudios.personaspeak.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric pass: real DataStore + real file system (fake cipher, since
 * Robolectric has no AndroidKeyStore). Verifies the metadata half round-trips,
 * the staging/live files behave, and the store survives "process recreation"
 * by constructing a fresh instance over the same backing storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreProviderConfigStoreRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newStore(): DataStoreProviderConfigStore {
        val filesDir: File = context.filesDir
        return DataStoreProviderConfigStore(
            metaStore = DataStoreMetaStore(context),
            cipher = RobolectricCipher(),
            liveBlob = File(filesDir, DataStoreProviderConfigStore.LIVE_BLOB_NAME),
            stagingBlob = File(filesDir, DataStoreProviderConfigStore.STAGING_BLOB_NAME),
            logger = StoreLog { },
        )
    }

    @Test
    fun `save load across a fresh store instance - persistence survives process recreation`() =
        runTest {
            val secret = SecretBytes("robolectric-secret".toByteArray())
            val first = newStore()
            first.save(ProviderConfig("gemini", 42L), secret)

            // New instance over the same storage = process death + relaunch.
            val second = newStore()
            val snapshot = second.load()
            val configured = snapshot.outcome as StoreOutcome.Configured
            assertEquals("gemini", configured.providerId)
            assertEquals(42L, configured.configuredAtEpochMs)
            assertArrayEquals(secret.value, snapshot.secret!!.value)
        }

    @Test
    fun `artifact file names match the backup-exclusion rules`() = runTest {
        assertEquals("personaspeak_secret.bin", DataStoreProviderConfigStore.LIVE_BLOB_NAME)
        assertEquals(
            "personaspeak_secret.bin.staging",
            DataStoreProviderConfigStore.STAGING_BLOB_NAME,
        )
    }

    @Test
    fun `clear wipes both files and metadata`() = runTest {
        val s = newStore()
        s.save(ProviderConfig("fake", 1L), SecretBytes(byteArrayOf(1, 2, 3)))
        assertTrue(File(context.filesDir, DataStoreProviderConfigStore.LIVE_BLOB_NAME).exists())
        s.clear()
        assertFalse(File(context.filesDir, DataStoreProviderConfigStore.LIVE_BLOB_NAME).exists())
        assertFalse(File(context.filesDir, DataStoreProviderConfigStore.STAGING_BLOB_NAME).exists())
        assertEquals(StoreOutcome.Unconfigured, newStore().load().outcome)
    }
}

/** XOR fake with checksum tag, identical shape to the JVM contract-test fake. */
private class RobolectricCipher : SecretCipher {
    override fun encrypt(secret: SecretBytes, generation: ByteArray): ByteArray {
        val ks = ByteArray(secret.value.size) { (it * 31 + 7).toByte() }
        val ct = ByteArray(secret.value.size) { ((secret.value[it].toInt() xor ks[it].toInt())).toByte().toByte() }
        return BlobFormat.MAGIC + byteArrayOf(BlobFormat.VERSION) + generation +
            ByteArray(BlobFormat.IV_BYTES) + ct + byteArrayOf(checksum(ct))
    }

    override fun decrypt(blob: ByteArray): SecretBytes? {
        val generation = BlobFormat.generationOf(blob) ?: return null
        if (blob.size <= BlobFormat.HEADER_BYTES) return null
        require(generation.size == BlobFormat.GENERATION_BYTES)
        val body = blob.copyOfRange(BlobFormat.HEADER_BYTES, blob.size - 1)
        if (checksum(body) != blob.last()) return null
        val ks = ByteArray(body.size) { (it * 31 + 7).toByte() }
        return SecretBytes(ByteArray(body.size) { ((body[it].toInt() xor ks[it].toInt())).toByte().toByte() })
    }

    private fun checksum(bytes: ByteArray): Byte {
        var acc = 0
        for (b in bytes) acc = (acc + b * 3) % 251
        return acc.toByte()
    }
}
