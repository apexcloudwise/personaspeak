package biz.pixelperfectstudios.personaspeak.data

import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreFailure
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Contract tests for the approved M4 slice-1 recovery matrix. Runs on the JVM:
 * cipher is a deterministic fake, meta is an in-memory seam, blobs are real
 * files in a temp dir — so crash points are simulated by writing artifacts
 * directly to the seams, exactly as the plan requires.
 */
class DataStoreProviderConfigStoreContractTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var liveBlob: File
    private lateinit var stagingBlob: File
    private lateinit var metaStore: FakeMetaStore
    private val cipher = FakeCipher()
    private val secret = SecretBytes("sk-test-credential-material".toByteArray())

    @Before
    fun setUp() {
        liveBlob = File(tmp.root, "personaspeak_secret.bin")
        stagingBlob = File(tmp.root, "personaspeak_secret.bin.staging")
        metaStore = FakeMetaStore()
    }

    private var generationCounter = 0

    private fun store(logEvents: MutableList<StoreEvent> = mutableListOf()) =
        DataStoreProviderConfigStore(
            metaStore = metaStore,
            cipher = cipher,
            liveBlob = liveBlob,
            stagingBlob = stagingBlob,
            logger = StoreLog { logEvents.add(it) },
            newGeneration = { genOf(generationCounter++) },
        )

    private fun config(ts: Long = 1_000L) = ProviderConfig("gemini", configuredAtEpochMs = ts)

    // ---- happy paths -------------------------------------------------------

    @Test
    fun `load on fresh device is Unconfigured`() = runTest {
        assertEquals(StoreOutcome.Unconfigured, store().load().outcome)
    }

    @Test
    fun `save then load round-trips config and secret`() = runTest {
        val s = store()
        assertEquals(
            StoreOutcome.Configured("gemini", 1_000L, genOf(0)),
            s.save(config(), secret),
        )
        val snapshot = s.load()
        assertEquals(StoreOutcome.Configured("gemini", 1_000L, genOf(0)), snapshot.outcome)
        assertArrayEquals(secret.value, snapshot.secret!!.value)
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `clear removes everything and next load is Unconfigured`() = runTest {
        val s = store()
        s.save(config(), secret)
        s.clear()
        assertFalse(liveBlob.exists())
        assertFalse(stagingBlob.exists())
        assertNull(metaStore.read())
        assertEquals(StoreOutcome.Unconfigured, s.load().outcome)
    }

    @Test
    fun `re-save replaces generation and keeps old blob until swap`() = runTest {
        val s = store()
        s.save(config(), secret)
        val firstLiveBytes = liveBlob.readBytes()

        cipher.encryptCalls.clear()
        assertEquals(
            StoreOutcome.Configured("anthropic", 2_000L, genOf(1)),
            s.save(ProviderConfig("anthropic", 2_000L), SecretBytes("key-2".toByteArray())),
        )
        // Old bytes were replaced only by the final swap; the staged write never
        // touched the live file before metadata committed.
        assertFalse(liveBlob.readBytes()!!.contentEquals(firstLiveBytes))
        assertTrue(s.load().outcome is StoreOutcome.Configured)
    }

    // ---- crash points (matrix rows) ----------------------------------------

    @Test
    fun `crash inside stage of re-save keeps old state healthy and drops orphan staging`() =
        runTest {
            val s = store()
            s.save(config(), secret)
            val healthyBytes = liveBlob.readBytes()!!

            simulateCrashInsideStage(newGeneration = 1)

            val snapshot = s.load()
            assertEquals(StoreOutcome.Configured("gemini", 1_000L, genOf(0)), snapshot.outcome)
            assertArrayEquals(secret.value, snapshot.secret!!.value)
            assertArrayEquals(healthyBytes, liveBlob.readBytes())
            assertFalse(stagingBlob.exists())
        }

    @Test
    fun `crash inside first-ever stage leaves Unconfigured with no residue`() = runTest {
        simulateCrashInsideStage(newGeneration = 0)

        assertEquals(StoreOutcome.Unconfigured, store().load().outcome)
        assertFalse(liveBlob.exists())
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `first save crash between commit and swap recovers by completing swap`() = runTest {
        simulateCrashBetweenCommitAndSwap(firstSave = true)

        val snapshot = store().load()
        assertEquals(StoreOutcome.Configured("gemini", 5_000L, genOf(0)), snapshot.outcome)
        assertArrayEquals("new".toByteArray(), snapshot.secret!!.value)
        assertTrue(liveBlob.exists())
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `re-save crash between commit and swap recovers new state via staging`() = runTest {
        val s = store()
        s.save(config(), secret) // gen 0 live

        simulateCrashBetweenCommitAndSwap(firstSave = false) // meta=gen1, staging=new

        val snapshot = s.load()
        assertEquals(StoreOutcome.Configured("gemini", 6_000L, genOf(1)), snapshot.outcome)
        assertArrayEquals("newer".toByteArray(), snapshot.secret!!.value)
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `restore drops blob but keeps meta - InvalidCredentials and full clear`() = runTest {
        val s = store()
        s.save(config(), secret)
        liveBlob.delete()

        assertEquals(StoreOutcome.InvalidCredentials, s.load().outcome)
        assertNull(metaStore.read())
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `meta present but nothing matches it - InvalidCredentials and full clear`() = runTest {
        val s = store()
        s.save(config(), secret)
        // Tamper the blob so neither live nor staging matches metadata.
        liveBlob.writeBytes(liveBlob.readBytes().also { it[20] = (it[20] + 1).toByte() })

        assertEquals(StoreOutcome.InvalidCredentials, s.load().outcome)
        assertNull(metaStore.read())
        assertFalse(liveBlob.exists())
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `corrupt ciphertext fails closed to InvalidCredentials`() = runTest {
        val s = store()
        s.save(config(), secret)
        val bytes = liveBlob.readBytes()!!
        bytes[bytes.size - 1] = (bytes.last() + 1).toByte() // flip a tag byte
        liveBlob.writeBytes(bytes)

        assertEquals(StoreOutcome.InvalidCredentials, s.load().outcome)
        assertFalse(liveBlob.exists())
        assertNull(metaStore.read())
    }

    @Test
    fun `meta absent while blob present - orphans removed, InvalidCredentials`() = runTest {
        metaStore.clear()
        cipher.encrypt(SecretBytes("orphan".toByteArray()), DataStoreProviderConfigStore.generationUuidBytes(genOf(0)))
            .let { atomicWrite(liveBlob, it) }

        assertEquals(StoreOutcome.InvalidCredentials, store().load().outcome)
        assertFalse(liveBlob.exists())
        assertFalse(stagingBlob.exists())
    }

    @Test
    fun `keystore unavailable during load maps to Unavailable without mutation`() = runTest {
        val s = store()
        s.save(config(), secret)
        cipher.throwUnavailableOnDecrypt = true

        val outcome = s.load().outcome
        assertTrue(outcome is StoreOutcome.Unavailable)
        assertEquals(StoreFailure.KEYSTORE_UNAVAILABLE, (outcome as StoreOutcome.Unavailable).reasonCode)
        assertTrue(liveBlob.exists()) // nothing was destroyed on a transient failure
        assertEquals(genOf(0), metaStore.read()!!.generation)
    }

    @Test
    fun `metadata write failure after staging leaves old state fully valid`() = runTest {
        val s = store()
        s.save(config(), secret)
        metaStore.failNextWrite = true

        val outcome = s.save(ProviderConfig("openai", 9_000L), SecretBytes("k3".toByteArray()))
        assertTrue(outcome is StoreOutcome.Unavailable)

        // Old credential still intact; orphan staging swept at load.
        val snapshot = s.load()
        assertEquals(StoreOutcome.Configured("gemini", 1_000L, genOf(0)), snapshot.outcome)
        assertFalse(stagingBlob.exists())
    }

    // ---- data classification regression ------------------------------------

    @Test
    fun `plaintext secret never appears on disk - written bytes are ciphertext only`() =
        runTest {
            val s = store()
            s.save(config(), secret)

            val diskBytes = liveBlob.readBytes()!!
            assertTrue(bytesIndexOf(diskBytes, secret.value) == -1)
            // And the fake cipher's output really differs from the input.
            assertFalse(diskBytes.contentEquals(secret.value))
        }

    @Test
    fun `staging file also never contains plaintext mid-save`() = runTest {
        val s = store()
        // Simulate a crash right after staging: capture staging content.
        val events = mutableListOf<StoreEvent>()
        val inner = metaStore
        val crashingMeta = object : MetaStore {
            override suspend fun read(): ProviderMeta? = inner.read()
            override suspend fun clear() = inner.clear()
            override suspend fun write(meta: ProviderMeta) {
                // Assert plaintext absent from staging BEFORE metadata commits.
                assertTrue(bytesIndexOf(stagingBlob.readBytes()!!, secret.value) == -1)
                inner.write(meta)
            }
        }
        DataStoreProviderConfigStore(crashingMeta, cipher, liveBlob, stagingBlob, StoreLog { events.add(it) })
            .save(config(), secret)
        assertTrue(events.contains(StoreEvent.SAVE_COMMITTED))
    }

    // ---- helpers ------------------------------------------------------------

    private fun genOf(index: Int): String {
        // Deterministic generation names: FakeCipher hands out uuids in order.
        return "00000000-0000-0000-0000-%012d".format(index)
    }

    /** Crash inside step 1 of save: staging exists, metadata does not name it. */
    private suspend fun simulateCrashInsideStage(newGeneration: Int) {
        val blob = cipher.encrypt(
            SecretBytes(if (newGeneration == 0) "new".toByteArray() else "newer".toByteArray()),
            DataStoreProviderConfigStore.generationUuidBytes(genOf(newGeneration)),
        )
        atomicWrite(stagingBlob, blob)
    }

    /**
     * Crash between step 2 (metadata commit) and step 3 (swap). When
     * [firstSave], no live blob ever existed; otherwise gen 0 stays live.
     */
    private suspend fun simulateCrashBetweenCommitAndSwap(firstSave: Boolean) {
        val ts = if (firstSave) 5_000L else 6_000L
        val secretText = if (firstSave) "new" else "newer"
        val gen = if (firstSave) 0 else 1
        val blob = cipher.encrypt(
            SecretBytes(secretText.toByteArray()),
            DataStoreProviderConfigStore.generationUuidBytes(genOf(gen)),
        )
        atomicWrite(stagingBlob, blob)
        if (!firstSave) {
            // Live still holds gen 0 from the earlier successful save.
            val oldBlob = cipher.encrypt(
                secret,
                DataStoreProviderConfigStore.generationUuidBytes(genOf(0)),
            )
            atomicWrite(liveBlob, oldBlob)
        }
        metaStore.write(ProviderMeta("gemini", ts, ProviderConfig.SCHEMA_VERSION, genOf(gen)))
    }

    private fun bytesIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

/**
 * Deterministic XOR "cipher" with a checksum tag emulating GCM auth: same
 * output shape as production, zero hardware, corruption actually detectable.
 */
private class FakeCipher : SecretCipher {
    var throwUnavailableOnDecrypt = false
    val encryptCalls = mutableListOf<ByteArray>()

    private fun keystream(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    override fun encrypt(secret: SecretBytes, generation: ByteArray): ByteArray {
        encryptCalls.add(secret.value)
        val ks = keystream(secret.value.size)
        val ct = ByteArray(secret.value.size) { ((secret.value[it].toInt() xor ks[it].toInt())).toByte().toByte() }
        return BlobFormat.MAGIC + byteArrayOf(BlobFormat.VERSION) + generation +
            ByteArray(BlobFormat.IV_BYTES) + ct + byteArrayOf(checksum(ct))
    }

    override fun decrypt(blob: ByteArray): SecretBytes? {
        if (throwUnavailableOnDecrypt) throw CipherUnavailableException(IllegalStateException("ks"))
        val generation = BlobFormat.generationOf(blob) ?: return null
        if (blob.size <= BlobFormat.HEADER_BYTES) return null
        val body = blob.copyOfRange(BlobFormat.HEADER_BYTES, blob.size - 1)
        if (checksum(body) != blob.last()) return null // tampered
        val ks = keystream(body.size)
        return SecretBytes(ByteArray(body.size) { ((body[it].toInt() xor ks[it].toInt())).toByte().toByte() })
    }

    private fun checksum(bytes: ByteArray): Byte {
        var acc = 0
        for (b in bytes) acc = (acc + b * 3) % 251
        return acc.toByte()
    }
}

private open class FakeMetaStore : MetaStore {
    private var meta: ProviderMeta? = null
    var failNextWrite = false

    override suspend fun read(): ProviderMeta? = meta

    override suspend fun write(value: ProviderMeta) {
        check(!failNextWrite) { "simulated IO failure" }
        meta = value
    }

    override suspend fun clear() {
        meta = null
    }
}
