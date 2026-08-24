package biz.pixelperfectstudios.personaspeak.ui.brain

/**
 * Provider-configuration data carried across the [ProviderConfigStore] port.
 *
 * Pure data only: the storage implementation lives behind the port
 * (`personaspeak-data`), so `personaspeak-ui` never touches Android storage.
 * Nothing in this file may hold user-typed text — provider ids, timestamps,
 * and opaque credential bytes are the entire universe of what persists
 * (M4 slice-1 plan, data classification).
 */

/** Opaque, already-decrypted credential material. Never logged, never persisted in plaintext. */
@JvmInline
value class SecretBytes(val value: ByteArray)

/** Non-secret configuration metadata plus the secret it was saved with. */
data class ProviderConfig(
    val providerId: String,
    val configuredAtEpochMs: Long,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        /** Bumped on any breaking change to the persisted config shape. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/**
 * Result of any store operation. Fixed set — the adapter logs these codes and
 * nothing else, which is what keeps secrets out of logcat by construction.
 */
sealed interface StoreOutcome {
    /** No stored configuration exists. */
    data object Unconfigured : StoreOutcome

    /** Configuration present, credential readable. */
    data class Configured(
        val providerId: String,
        val configuredAtEpochMs: Long,
        val generation: String,
    ) : StoreOutcome

    /** Storage or Keystore is broken; nothing was mutated by the failed read. */
    data class Unavailable(val reasonCode: StoreFailure) : StoreOutcome

    /**
     * Stored state exists but cannot be resolved to a valid credential
     * (corrupt blob, unrecoverable generation mismatch). Artifacts were
     * cleared; the caller must reconfigure from scratch.
     */
    data object InvalidCredentials : StoreOutcome
}

enum class StoreFailure {
    KEYSTORE_UNAVAILABLE,
    IO_ERROR,
}

/** Snapshot returned by [ProviderConfigStore.load]; carries the decrypted secret when configured. */
data class ProviderConfigSnapshot(
    val outcome: StoreOutcome,
    val secret: SecretBytes? = null,
)
