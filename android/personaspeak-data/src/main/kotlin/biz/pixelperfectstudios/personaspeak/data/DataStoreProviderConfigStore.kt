package biz.pixelperfectstudios.personaspeak.data

import android.content.Context
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigSnapshot
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreFailure
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * The two-artifact provider-configuration store: DataStore metadata +
 * Keystore-encrypted ciphertext file, bound by a shared generation UUID.
 *
 * Save is stage/commit/swap so a mid-save crash always resolves to the old or
 * the new state, never to nothing. Load implements the approved recovery
 * matrix; every unrecoverable combination fails closed to InvalidCredentials
 * with artifacts cleared.
 */
class DataStoreProviderConfigStore(
    private val metaStore: MetaStore,
    private val cipher: SecretCipher,
    private val liveBlob: File,
    private val stagingBlob: File,
    private val logger: StoreLog,
    private val newGeneration: () -> String = { UUID.randomUUID().toString() },
) : ProviderConfigStore {

    override suspend fun load(): ProviderConfigSnapshot {
        val meta = try {
            metaStore.read()
        } catch (e: Exception) {
            return unavailable(e)
        }

        try {
            if (meta == null) {
                // A leftover live blob without metadata is untrusted
                // (restore/partial clear): fail closed and sweep. A leftover
                // staging file alone, though, is the residue of a crashed
                // FIRST save — nothing was ever committed — so sweep it and
                // report the truth: Unconfigured.
                if (liveBlob.exists()) {
                    deleteArtifacts()
                    logger.event(StoreEvent.LOAD_INVALID_CLEARED)
                    return snapshot(StoreOutcome.InvalidCredentials)
                }
                if (stagingBlob.exists()) {
                    stagingBlob.delete()
                }
                logger.event(StoreEvent.LOAD_UNCONFIGURED)
                return snapshot(StoreOutcome.Unconfigured)
            }

            val liveResult = decryptFile(liveBlob)

            when {
                liveResult is Decrypted && liveResult.generation == meta.generation ->
                    return configured(meta, liveResult.secret, cleanupStaging = true)

                stagingBlob.exists() -> {
                    val stagedResult = decryptFile(stagingBlob)
                    if (stagedResult is Decrypted && stagedResult.generation == meta.generation) {
                        // Crash between metadata commit and swap — first save or
                        // re-save. The committed metadata names the staged blob:
                        // complete the swap and serve the new state.
                        if (!stagingBlob.renameTo(liveBlob)) {
                            return unavailable(IOExceptionLike("rename failed"))
                        }
                        logger.event(StoreEvent.LOAD_RECOVERED_FROM_STAGING)
                        return configured(meta, stagedResult.secret, cleanupStaging = false)
                    }
                }
            }

            // Nothing matches the metadata: corrupt blob, tampered bytes, or a
            // restore that dropped half the state. Fail closed, clear everything.
            deleteArtifacts()
            try {
                metaStore.clear()
            } catch (e: Exception) {
                return unavailable(e)
            }
            logger.event(StoreEvent.LOAD_INVALID_CLEARED)
            return snapshot(StoreOutcome.InvalidCredentials)
        } catch (e: CipherUnavailableException) {
            // Keystore itself is broken: report Unavailable, mutate nothing.
            return unavailable(e)
        }
    }

    override suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome {
        val generation = newGeneration()
        val generationBytes = generationUuidBytes(generation)
        val blob = try {
            cipher.encrypt(secret, generationBytes)
        } catch (e: CipherUnavailableException) {
            logger.event(StoreEvent.SAVE_FAILED)
            return StoreOutcome.Unavailable(StoreFailure.KEYSTORE_UNAVAILABLE)
        }

        // Stage: write the new blob without touching the live credential.
        try {
            atomicWrite(stagingBlob, blob)
        } catch (e: Exception) {
            stagingBlob.delete()
            logger.event(StoreEvent.SAVE_FAILED)
            return StoreOutcome.Unavailable(StoreFailure.IO_ERROR)
        }
        logger.event(StoreEvent.SAVE_STAGED)

        // Commit: only now does the metadata name the new generation. From
        // this instant on, a crash is recoverable via the staging swap in load().
        val meta = ProviderMeta(
            providerId = config.providerId,
            configuredAtEpochMs = config.configuredAtEpochMs,
            schemaVersion = config.schemaVersion,
            generation = generation,
        )
        try {
            metaStore.write(meta)
        } catch (e: Exception) {
            // Metadata did not commit: old state remains fully valid; drop the orphan stage.
            stagingBlob.delete()
            logger.event(StoreEvent.SAVE_FAILED)
            return StoreOutcome.Unavailable(StoreFailure.IO_ERROR)
        }
        logger.event(StoreEvent.SAVE_COMMITTED)

        // Swap: atomic on the same filesystem volume.
        if (!stagingBlob.renameTo(liveBlob)) {
            // Recoverable at load time (staging still matches metadata).
            logger.event(StoreEvent.SAVE_FAILED)
            return StoreOutcome.Configured(
                providerId = meta.providerId,
                configuredAtEpochMs = meta.configuredAtEpochMs,
                generation = meta.generation,
            )
        }
        logger.event(StoreEvent.SAVE_SWAPPED)
        return StoreOutcome.Configured(
            providerId = meta.providerId,
            configuredAtEpochMs = meta.configuredAtEpochMs,
            generation = meta.generation,
        )
    }

    override suspend fun clear() {
        try {
            metaStore.clear()
        } catch (_: Exception) {
            // Bytes must go regardless of metadata fate.
        }
        deleteArtifacts()
        logger.event(StoreEvent.CLEAR_DONE)
    }

    private sealed interface BlobRead

    private data class Decrypted(val generation: String, val secret: SecretBytes) : BlobRead

    private fun decryptFile(file: File): BlobRead? {
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            null
        } ?: return null
        val headerGeneration = BlobFormat.generationOf(bytes)?.let { uuidFromBytes(it) }
        val secret = try {
            cipher.decrypt(bytes)
        } catch (e: CipherUnavailableException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return if (secret != null && headerGeneration != null) {
            Decrypted(headerGeneration, secret)
        } else {
            null
        }
    }

    private fun configured(
        meta: ProviderMeta,
        secret: SecretBytes,
        cleanupStaging: Boolean,
    ): ProviderConfigSnapshot {
        if (cleanupStaging) stagingBlob.delete()
        logger.event(StoreEvent.LOAD_CONFIGURED)
        return ProviderConfigSnapshot(
            outcome = StoreOutcome.Configured(
                providerId = meta.providerId,
                configuredAtEpochMs = meta.configuredAtEpochMs,
                generation = meta.generation,
            ),
            secret = secret,
        )
    }

    private fun unavailable(e: Exception): ProviderConfigSnapshot {
        logger.event(StoreEvent.LOAD_UNAVAILABLE)
        return ProviderConfigSnapshot(outcome = StoreOutcome.Unavailable(mapFailure(e)))
    }

    private fun deleteArtifacts() {
        liveBlob.delete()
        stagingBlob.delete()
    }

    companion object {
        /** Wires the production stack: real KeyStore cipher + real DataStore + app files dir. */
        fun create(context: Context, sdkInt: Int): DataStoreProviderConfigStore =
            DataStoreProviderConfigStore(
                metaStore = DataStoreMetaStore(context),
                cipher = KeystoreSecretCipher(sdkInt = sdkInt),
                liveBlob = File(context.filesDir, LIVE_BLOB_NAME),
                stagingBlob = File(context.filesDir, STAGING_BLOB_NAME),
                logger = LogcatStoreLog(),
            )

        const val LIVE_BLOB_NAME: String = "personaspeak_secret.bin"
        const val STAGING_BLOB_NAME: String = "personaspeak_secret.bin.staging"

        internal fun generationUuidBytes(generation: String): ByteArray {
            val uuid = UUID.fromString(generation)
            val buffer = java.nio.ByteBuffer.allocate(16)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            return buffer.array()
        }

        internal fun uuidFromBytes(bytes: ByteArray): String {
            require(bytes.size == 16)
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val uuid = UUID(buffer.long, buffer.long)
            return uuid.toString()
        }
    }
}

private fun snapshot(outcome: StoreOutcome) = ProviderConfigSnapshot(outcome = outcome)

private fun mapFailure(e: Exception): StoreFailure =
    if (e is CipherUnavailableException) StoreFailure.KEYSTORE_UNAVAILABLE else StoreFailure.IO_ERROR

private class IOExceptionLike(message: String) : Exception(message)

internal fun atomicWrite(target: File, bytes: ByteArray) {
    FileOutputStream(target).use { out ->
        out.write(bytes)
        out.flush()
        out.fd.sync()
    }
}
