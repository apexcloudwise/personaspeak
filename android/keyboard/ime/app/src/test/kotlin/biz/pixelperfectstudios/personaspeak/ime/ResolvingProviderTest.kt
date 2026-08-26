package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigSnapshot
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreFailure
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvingProviderTest {

    private class TestStore(
        var snapshot: ProviderConfigSnapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured),
    ) : ProviderConfigStore {
        var loadCount = 0

        override suspend fun load(): ProviderConfigSnapshot {
            loadCount++
            return snapshot
        }

        override suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome {
            snapshot = ProviderConfigSnapshot(
                outcome = StoreOutcome.Configured(
                    providerId = config.providerId,
                    configuredAtEpochMs = config.configuredAtEpochMs,
                    generation = "gen-1",
                    model = config.model,
                ),
                secret = secret,
            )
            return snapshot.outcome
        }

        override suspend fun clear() {
            snapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured)
        }
    }

    private class StubFallback(
        override val id: String = "stub-fallback",
        override val displayName: String = "Stub Fallback",
    ) : CompletionProvider {
        var calledWithText: String? = null
        override suspend fun rewrite(system: String, text: String): Result<String> {
            calledWithText = text
            return Result.success("fallback: $text")
        }
    }

    @Test
    fun resolvesToFallbackWhenUnconfigured() = runBlocking {
        val store = TestStore(ProviderConfigSnapshot(StoreOutcome.Unconfigured))
        val fallback = StubFallback()
        val resolving = ResolvingProvider(store = store, fallback = fallback)

        val result = resolving.rewrite("sys", "Hello world")
        assertTrue(result.isSuccess)
        assertEquals("fallback: Hello world", result.getOrNull())
        assertEquals("Hello world", fallback.calledWithText)
    }

    @Test
    fun resolvesToFallbackWhenUnavailable() = runBlocking {
        val store = TestStore(
            ProviderConfigSnapshot(StoreOutcome.Unavailable(StoreFailure.KEYSTORE_UNAVAILABLE)),
        )
        val fallback = StubFallback()
        val resolving = ResolvingProvider(store = store, fallback = fallback)

        val result = resolving.rewrite("sys", "Hello world")
        assertTrue(result.isSuccess)
        assertEquals("fallback: Hello world", result.getOrNull())
    }

    @Test
    fun cachesResolvedProviderUntilInvalidated() = runBlocking {
        val store = TestStore(ProviderConfigSnapshot(StoreOutcome.Unconfigured))
        val fallback = StubFallback()
        val resolving = ResolvingProvider(store = store, fallback = fallback)

        resolving.rewrite("sys", "msg 1")
        resolving.rewrite("sys", "msg 2")
        assertEquals(1, store.loadCount)

        resolving.invalidate()
        resolving.rewrite("sys", "msg 3")
        assertEquals(2, store.loadCount)
    }
}
