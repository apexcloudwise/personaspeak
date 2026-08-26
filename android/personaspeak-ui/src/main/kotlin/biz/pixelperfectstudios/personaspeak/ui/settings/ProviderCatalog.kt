package biz.pixelperfectstudios.personaspeak.ui.settings

/**
 * Model metadata entry for UI catalog display.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val isFree: Boolean,
)

/**
 * One row of the provider picker: defaults, key link, and wiring hints.
 */
data class ProviderDef(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val needsBaseUrl: Boolean,
    val keyUrl: String,
)

/**
 * The providers offered at setup time.
 */
object ProviderCatalog {
    val openrouter = ProviderDef(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "nvidia/nemotron-3-super-120b-a12b:free",
        needsBaseUrl = false,
        keyUrl = "https://openrouter.ai/keys",
    )

    val anthropic = ProviderDef(
        id = "anthropic",
        displayName = "Claude (Anthropic)",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-3-5-haiku-20241022",
        needsBaseUrl = false,
        keyUrl = "https://console.anthropic.com/settings/keys",
    )

    val openaiCompat = ProviderDef(
        id = "openai-compat",
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        needsBaseUrl = true,
        keyUrl = "https://platform.openai.com/api-keys",
    )

    val all = listOf(openrouter, anthropic, openaiCompat)

    fun byId(id: String): ProviderDef? = all.firstOrNull { it.id == id }
}
