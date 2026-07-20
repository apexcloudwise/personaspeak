package biz.pixelperfectstudios.personaspeak.providers

/**
 * One brain in the provider registry (ADR-0002). Implementations at launch:
 * Gemini free tier (default), Anthropic/OpenAI/OpenRouter via BYOK, and a
 * fake for tests and walking skeletons. On-device joins in Phase 3.
 */
interface CompletionProvider {
    /** Stable identifier used in settings and routing config. */
    val id: String

    /** Human-readable name for the provider picker. */
    val displayName: String

    /**
     * Rewrite [text] according to the persona/tone [system] prompt.
     * Failures come back as [Result.failure] with a user-presentable message
     * (in-voice, per AGENTS.md) — never a raw stack trace.
     */
    suspend fun rewrite(system: String, text: String): Result<String>
}
