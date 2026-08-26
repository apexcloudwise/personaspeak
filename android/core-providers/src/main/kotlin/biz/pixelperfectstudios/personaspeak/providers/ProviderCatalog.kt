package biz.pixelperfectstudios.personaspeak.providers

/** One row of the provider picker: defaults, key link, and wiring hints. */
data class ProviderDef(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val needsBaseUrl: Boolean,
    val keyUrl: String,
)

/** The providers offered at setup time, plus the factory that instantiates them. */
object ProviderCatalog {
    val openrouter = ProviderDef(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "nvidia/nemotron-3-super-120b-a12b:free",
        needsBaseUrl = false,
        keyUrl = "https://openrouter.ai/keys",
    )

    val openaiCompat = ProviderDef(
        id = "openai-compat",
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        needsBaseUrl = true,
        keyUrl = "https://platform.openai.com/api-keys",
    )

    val anthropic = ProviderDef(
        id = "anthropic",
        displayName = "Claude (Anthropic)",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-haiku-4-5",
        needsBaseUrl = false,
        keyUrl = "https://console.anthropic.com/settings/keys",
    )

    val all = listOf(openrouter, openaiCompat, anthropic)

    fun byId(id: String): ProviderDef? = all.firstOrNull { it.id == id }

    fun build(def: ProviderDef, baseUrl: String?, model: String?, apiKey: String): CompletionProvider {
        val resolvedBaseUrl = baseUrl ?: def.defaultBaseUrl
        val resolvedModel = model ?: def.defaultModel
        return if (def.id == anthropic.id) {
            AnthropicProvider(apiKey = apiKey, baseUrl = resolvedBaseUrl, model = resolvedModel)
        } else {
            HttpChatCompletionsProvider(def.id, def.displayName, resolvedBaseUrl, resolvedModel, apiKey)
        }
    }
}
