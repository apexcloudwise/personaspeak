package biz.pixelperfectstudios.personaspeak.app.ui.data

/**
 * Hardcoded provider display list for the AI providers settings screen. The
 * real provider registry lives in :core-providers; this is the UI-facing
 * projection with just the display names and statuses the mockups carry.
 */
data class ProviderDisplay(
    val id: String,
    val name: String,
    val shortName: String,
    val status: String,
    val description: String,
    val isActive: Boolean = false,
    val isLocal: Boolean = false,
    val isConfigured: Boolean = false,
    val action: String,
)

object SampleProviders {

    val all: List<ProviderDisplay> = listOf(
        ProviderDisplay(
            id = "gemini",
            name = "Gemini",
            shortName = "G",
            status = "Active",
            description = "Using Google's most capable multimodal models.",
            isActive = true,
            isConfigured = true,
            action = "Active",
        ),
        ProviderDisplay(
            id = "claude",
            name = "Claude",
            shortName = "Anthropic",
            status = "Not configured",
            description = "Anthropic's Claude family, strong for long-form reasoning.",
            action = "Setup",
        ),
        ProviderDisplay(
            id = "openai",
            name = "OpenAI",
            shortName = "GPT-4o",
            status = "Not configured",
            description = "GPT-4o and friends, for general-purpose rewriting.",
            action = "Setup",
        ),
        ProviderDisplay(
            id = "openrouter",
            name = "OpenRouter",
            shortName = "Many models",
            status = "Not configured",
            description = "One key, many providers — route between them.",
            action = "Setup",
        ),
        ProviderDisplay(
            id = "local",
            name = "Local Instance",
            shortName = "Self-host",
            status = "Advanced users",
            description = "Run a model on your own hardware. No cloud, no key.",
            isLocal = true,
            action = "Connect",
        ),
    )

    val active: ProviderDisplay? get() = all.firstOrNull { it.isActive }
}
