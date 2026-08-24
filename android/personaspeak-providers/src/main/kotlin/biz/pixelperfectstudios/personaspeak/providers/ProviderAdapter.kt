package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes

/**
 * Interface representing a remote model provider adapter.
 * Implementations execute rewrites using credentials supplied from secure storage.
 */
interface ProviderAdapter {
    val providerId: String
    val displayName: String

    /**
     * Rewrite [text] according to [system] prompt using credential [secret].
     * Never logs [secret], [text], or raw stack traces.
     */
    suspend fun rewrite(
        system: String,
        text: String,
        secret: SecretBytes,
    ): AdapterResult
}
