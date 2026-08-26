package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.ui.settings.ProviderCatalog
import kotlin.coroutines.cancellation.CancellationException

/**
 * [CompletionProvider] that resolves the user-configured brain from the
 * provider-config store on first use and delegates to it. Any state without a
 * usable credential — unconfigured, invalid, unavailable, or a broken store —
 * resolves to [fallback]. Key material never leaves this class and is never
 * logged.
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
        return delegate.rewrite(system, text)
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
                    resolvedKey = cacheKey(def.id, outcome.model, outcome.customBaseUrl)
                    val resolvedModel = outcome.model?.trim()?.takeIf { it.isNotEmpty() } ?: def.defaultModel
                    val adapter = when (def.id) {
                        "anthropic" -> AnthropicMessagesAdapter(model = resolvedModel)
                        "openrouter" -> OpenRouterAdapter(model = resolvedModel)
                        else -> {
                            val baseUrl = outcome.customBaseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: def.defaultBaseUrl
                            OpenRouterAdapter(
                                model = resolvedModel,
                                endpointUrl = baseUrl.trimEnd('/') + "/chat/completions",
                            )
                        }
                    }
                    object : CompletionProvider {
                        override val id: String = def.id
                        override val displayName: String = def.displayName
                        override suspend fun rewrite(system: String, text: String): Result<String> {
                            return when (val res = adapter.rewrite(system, text, secret)) {
                                is AdapterResult.Success -> Result.success(res.rewritten)
                                is AdapterResult.AuthFailure -> Result.failure(IllegalStateException("Authentication failure"))
                                is AdapterResult.NetworkFailure -> Result.failure(IllegalStateException("Network failure: ${res.code}"))
                            }
                        }
                    }
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

        fun cacheKey(providerId: String, model: String?, baseUrl: String?): String =
            "$providerId\u0000$model\u0000$baseUrl"
    }
}
