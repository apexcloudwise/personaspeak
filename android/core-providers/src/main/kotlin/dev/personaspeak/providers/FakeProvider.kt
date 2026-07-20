package dev.personaspeak.providers

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
}
