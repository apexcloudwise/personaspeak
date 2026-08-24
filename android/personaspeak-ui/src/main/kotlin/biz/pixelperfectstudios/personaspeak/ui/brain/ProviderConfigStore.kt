package biz.pixelperfectstudios.personaspeak.ui.brain

/**
 * Port for secure, package-scoped persistence of provider configuration and
 * credentials. Implemented by the `personaspeak-data` Android module; consumed
 * (from slice 2 onward) by the settings layer.
 *
 * Contract, from the approved M4 slice-1 plan:
 * - Persists provider ids/timestamps/schema version and Keystore-encrypted
 *   credential ciphertext — never drafts, prompts, candidates, results,
 *   history, or usage counters.
 * - Save is stage/commit/swap: the previous credential stays valid until the
 *   metadata naming the new generation has committed.
 * - Every unrecoverable state resolves to [StoreOutcome.InvalidCredentials]
 *   with artifacts cleared; storage/Keystore failures resolve to
 *   [StoreOutcome.Unavailable] without mutation.
 */
interface ProviderConfigStore {
    suspend fun load(): ProviderConfigSnapshot

    suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome

    suspend fun clear()
}
