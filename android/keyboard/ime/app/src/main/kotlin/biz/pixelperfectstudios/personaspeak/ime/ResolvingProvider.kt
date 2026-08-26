package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.providers.ProviderAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.ui.settings.ProviderCatalog
import biz.pixelperfectstudios.personaspeak.ui.settings.ProviderDef
import kotlin.coroutines.cancellation.CancellationException

/**
 * [CompletionProvider] that resolves the user-configured brain from the
 * provider-config store per-use and delegates to it. Any state without a
 * usable credential — unconfigured, invalid, unavailable, or a broken store —
 * resolves to [fallback].
 *
 * Credentials are decrypted on-demand per rewrite and immediately zeroed in memory
 * by the downstream adapter. No plaintext secret material is retained across calls.
 */
class ResolvingProvider(
    private val store: ProviderConfigStore,
    private val fallback: CompletionProvider = FakeProvider(),
    private val adapterFactory: (def: ProviderDef, model: String?, customBaseUrl: String?) -> ProviderAdapter = { def, model, customBaseUrl ->
        val resolvedModel = model?.trim()?.takeIf { it.isNotEmpty() } ?: def.defaultModel
        when (def.id) {
            "anthropic" -> AnthropicMessagesAdapter(model = resolvedModel)
            "openrouter" -> OpenRouterAdapter(model = resolvedModel)
            else -> {
                val baseUrl = customBaseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: def.defaultBaseUrl
                OpenRouterAdapter(
                    model = resolvedModel,
                    endpointUrl = baseUrl.trimEnd('/') + "/chat/completions",
                )
            }
        }
    },
) : CompletionProvider {

    override val id = "resolving"
    override val displayName = "Auto (configured brain)"

    /**
     * Invalidate hook invoked on input start to signal session boundaries.
     */
    fun invalidate() {
        // Per-use resolution requires no cache clearing, but hook is preserved for IME composition lifecycle.
    }

    override suspend fun rewrite(system: String, text: String): Result<String> {
        val snapshot = try {
            store.load()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }

        val outcome = snapshot?.outcome
        val secret = snapshot?.secret

        if (outcome is StoreOutcome.Configured && secret != null) {
            val def = ProviderCatalog.byId(outcome.providerId)
            if (def != null) {
                val adapter = adapterFactory(def, outcome.model, outcome.customBaseUrl)
                return when (val res = adapter.rewrite(system, text, secret)) {
                    is AdapterResult.Success -> Result.success(res.rewritten)
                    is AdapterResult.AuthFailure -> Result.failure(IllegalStateException("Authentication failure"))
                    is AdapterResult.NetworkFailure -> Result.failure(IllegalStateException("Network failure: ${res.code}"))
                }
            }
        }

        return fallback.rewrite(system, text)
    }
}
