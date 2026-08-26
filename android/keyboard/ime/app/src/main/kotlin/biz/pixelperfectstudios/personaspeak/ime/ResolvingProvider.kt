package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.ProviderCatalog
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import kotlin.coroutines.cancellation.CancellationException

/**
 * [CompletionProvider] that resolves the user-configured brain from the
 * provider-config store on first use and delegates to it. Any state without a
 * usable credential — unconfigured, invalid, unavailable, or a broken store —
 * resolves to [fallback]. Key material never leaves this class and is never
 * logged. Delegation errors are not caught here; RewriteCoordinator does the
 * error mapping.
 */
class ResolvingProvider(
    private val store: ProviderConfigStore,
    private val fallback: CompletionProvider = FakeProvider(),
) : CompletionProvider {

    override val id = "resolving"
    override val displayName = "Auto (configured brain)"

    @Volatile
    private var cached: Pair<String, CompletionProvider>? = null

    /** Drops the resolved provider so the next rewrite re-reads the store. */
    fun invalidate() {
        cached = null
    }

    override suspend fun rewrite(system: String, text: String): Result<String> {
        val delegate = resolve()
        android.util.Log.i("PsBrain", "rewrite via ${delegate.id}")
        return delegate.rewrite(system, text).onFailure { e ->
            android.util.Log.w("PsBrain", "rewrite failed via ${delegate.id}: ${e.javaClass.name}: ${e.message}")
        }
    }

    private suspend fun resolve(): CompletionProvider {
        cached?.let { return it.second }
        var resolvedKey = FALLBACK_KEY
        val provider = try {
            val snapshot = store.load()
            val outcome = snapshot.outcome
            val secret = snapshot.secret
            if (outcome is StoreOutcome.Configured && secret != null) {
                val def = ProviderCatalog.byId(outcome.providerId)
                if (def == null) {
                    fallback
                } else {
                    resolvedKey = cacheKey(def.id, outcome.model)
                    ProviderCatalog.build(def, null, outcome.model, String(secret.value))
                }
            } else {
                fallback
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            fallback
        }
        cached = resolvedKey to provider
        return provider
    }

    private companion object {
        const val FALLBACK_KEY = "fallback"

        fun cacheKey(providerId: String, model: String?): String = providerId + "\u0000" + model
    }
}
