package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.delay

/**
 * Walking-skeleton provider: proves the panel → provider → commit wiring
 * without a network or a key. Replaced by real providers in GTM Days 4-6;
 * kept forever for tests and demos.
 */
class FakeProvider(private val latencyMs: Long = 400) : CompletionProvider {
    override val id = "fake"
    override val displayName = "The Understudy (offline fake)"

    override suspend fun rewrite(system: String, text: String): Result<String> {
        delay(latencyMs) // pretend to think
        return Result.success(
            "I have taken the liberty, sir, of rephrasing your words: “$text” — " +
                "though I must confess the genuine article is still en route."
        )
    }

    override suspend fun suggest(system: String, text: String, count: Int): Result<List<String>> {
        require(count >= 1) { "suggestion count must be at least 1" }
        delay(latencyMs) // pretend to think, same pacing as rewrite

        val topic = text.trim()
            .trimEnd('.', '!', '?', ',', ';', ':', '“', '”', '"')
            .take(120)
            .takeIf { it.isNotEmpty() }
            ?: "that"

        // Deterministic, three distinct registers (assured / curious / warm).
        // A stable hash of the topic picks the line per register, so the same
        // message always drafts the same suggestions and different messages
        // vary without any randomness or network. Honest limitation: the fake
        // cannot truly flavor by persona — that is what configured providers
        // are for (ADR-0011 §4).
        return Result.success(
            SUGGESTION_REGISTERS.map { pool -> pool[(topic.hashCode() and 0x7fffffff) % pool.size].format(topic) }
                .take(count)
        )
    }

    private companion object {
        val SUGGESTION_REGISTERS: List<List<String>> = listOf(
            listOf(
                "Right away — consider “%s” handled.",
                "On it. “%s” will be sorted before you know it.",
                "Very good — “%s” is already in hand.",
            ),
            listOf(
                "Shall I go ahead with “%s”, or would you change something first?",
                "Before I act on “%s” — any adjustments you'd like?",
                "One thing about “%s”: proceed as-is, or refine it?",
            ),
            listOf(
                "Rest assured, “%s” will be seen to personally.",
                "You have my word — “%s” gets my full attention.",
                "Consider it promised: “%s” is in safe hands.",
            ),
        )
    }
}
