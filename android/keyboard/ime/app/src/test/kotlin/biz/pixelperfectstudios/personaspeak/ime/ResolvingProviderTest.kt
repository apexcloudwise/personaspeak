package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.ProviderAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
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
import java.nio.charset.StandardCharsets

class ResolvingProviderTest {

    private class TestStore(
        var snapshot: ProviderConfigSnapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured),
    ) : ProviderConfigStore {
        var loadCount = 0
        var storedSecretString: String? = null

        override suspend fun load(): ProviderConfigSnapshot {
            loadCount++
            val secret = storedSecretString?.let { SecretBytes(it.toByteArray(StandardCharsets.UTF_8)) }
            return snapshot.copy(secret = secret)
        }

        override suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome {
            storedSecretString = String(secret.value, StandardCharsets.UTF_8)
            snapshot = ProviderConfigSnapshot(
                outcome = StoreOutcome.Configured(
                    providerId = config.providerId,
                    configuredAtEpochMs = config.configuredAtEpochMs,
                    generation = "gen-1",
                    model = config.model,
                    customBaseUrl = config.customBaseUrl,
                ),
                secret = secret,
            )
            return snapshot.outcome
        }

        override suspend fun clear() {
            storedSecretString = null
            snapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured)
        }
    }

    private class StubFallback(
        override val id: String = "stub-fallback",
        override val displayName: String = "Stub Fallback",
    ) : CompletionProvider {
        var calledWithText: String? = null
        var suggestCalls: Int = 0
        override suspend fun rewrite(system: String, text: String): Result<String> {
            calledWithText = text
            return Result.success("fallback: $text")
        }

        override suspend fun suggest(system: String, text: String, count: Int): Result<List<String>> {
            suggestCalls += 1
            calledWithText = text
            return Result.success(List(minOf(count, 3)) { "fallback suggestion $it" })
        }
    }

    private class RecordingZeroingAdapter(
        override val providerId: String = "openrouter",
        override val displayName: String = "Recording Adapter",
    ) : ProviderAdapter {
        val capturedKeys = mutableListOf<String>()

        override suspend fun rewrite(
            system: String,
            text: String,
            secret: SecretBytes,
        ): AdapterResult {
            val keyCopy = String(secret.value, StandardCharsets.UTF_8)
            capturedKeys.add(keyCopy)
            // Zero secret immediately per slice-2 adapter contract
            secret.value.fill(0)
            return AdapterResult.Success("rewritten: $text")
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
    fun configuredProviderRewritesTwiceWithIntactSecret() = runBlocking {
        val store = TestStore()
        store.save(
            ProviderConfig(
                providerId = "openrouter",
                configuredAtEpochMs = 12345L,
                model = "nvidia/nemotron-3-super-120b-a12b:free",
            ),
            SecretBytes("sk-or-real-api-key".toByteArray(StandardCharsets.UTF_8)),
        )

        val recordingAdapter = RecordingZeroingAdapter()
        val resolving = ResolvingProvider(
            store = store,
            adapterFactory = { _, _, _ -> recordingAdapter },
        )

        // First rewrite call
        val result1 = resolving.rewrite("sys", "Hello first")
        assertTrue(result1.isSuccess)
        assertEquals("rewritten: Hello first", result1.getOrNull())

        // Second rewrite call (verifying fix 1: fresh secret loaded per call, not zeroes)
        val result2 = resolving.rewrite("sys", "Hello second")
        assertTrue(result2.isSuccess)
        assertEquals("rewritten: Hello second", result2.getOrNull())

        assertEquals(2, recordingAdapter.capturedKeys.size)
        assertEquals("sk-or-real-api-key", recordingAdapter.capturedKeys[0])
        assertEquals("sk-or-real-api-key", recordingAdapter.capturedKeys[1])
    }

    @Test
    fun mapsAdapterFailuresCorrectly() = runBlocking {
        val store = TestStore()
        store.save(
            ProviderConfig(providerId = "openrouter", configuredAtEpochMs = 12345L),
            SecretBytes("sk-key".toByteArray(StandardCharsets.UTF_8)),
        )

        var returnFailure: AdapterResult = AdapterResult.AuthFailure
        val resolving = ResolvingProvider(
            store = store,
            adapterFactory = { _, _, _ ->
                object : ProviderAdapter {
                    override val providerId = "openrouter"
                    override val displayName = "OpenRouter"
                    override suspend fun rewrite(system: String, text: String, secret: SecretBytes): AdapterResult {
                        secret.value.fill(0)
                        return returnFailure
                    }
                }
            },
        )

        val authResult = resolving.rewrite("sys", "test")
        assertTrue(authResult.isFailure)
        assertEquals("Authentication failure", authResult.exceptionOrNull()?.message)

        returnFailure = AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT)
        val timeoutResult = resolving.rewrite("sys", "test")
        assertTrue(timeoutResult.isFailure)
        assertEquals("Network failure: TIMEOUT", timeoutResult.exceptionOrNull()?.message)
    }

    @Test
    fun suggestsViaFallbackWhenUnconfigured() = runBlocking {
        val store = TestStore(ProviderConfigSnapshot(StoreOutcome.Unconfigured))
        val fallback = StubFallback()
        val resolving = ResolvingProvider(store = store, fallback = fallback)

        val result = resolving.suggest("sys", "Running late", count = 3)
        assertTrue(result.isSuccess)
        assertEquals(listOf("fallback suggestion 0", "fallback suggestion 1", "fallback suggestion 2"), result.getOrNull())
        assertEquals("Running late", fallback.calledWithText)
        assertEquals(1, fallback.suggestCalls)
    }

    @Test
    fun configuredProviderSuggestsParsedNumberedLines() = runBlocking {
        val store = TestStore()
        store.save(
            ProviderConfig(
                providerId = "openrouter",
                configuredAtEpochMs = 12345L,
                model = "nvidia/nemotron-3-super-120b-a12b:free",
            ),
            SecretBytes("sk-or-real-api-key".toByteArray(StandardCharsets.UTF_8)),
        )

        val resolving = ResolvingProvider(
            store = store,
            adapterFactory = { _, _, _ ->
                object : ProviderAdapter {
                    override val providerId = "openrouter"
                    override val displayName = "OpenRouter"
                    override suspend fun rewrite(system: String, text: String, secret: SecretBytes): AdapterResult {
                        secret.value.fill(0)
                        // One completion carrying the N-replies contract inside
                        // the numbered lines (ADR-0011 §6) — with decoration a
                        // real model might add.
                        return AdapterResult.Success(
                            "1. On my way!\n\n2) Shall I bring dessert?\n* See you at six."
                        )
                    }
                }
            },
        )

        val result = resolving.suggest("sys", "Running late, start the tea without me", count = 3)
        assertTrue(result.isSuccess)
        assertEquals(
            listOf("On my way!", "Shall I bring dessert?", "See you at six."),
            result.getOrNull(),
        )
    }

    @Test
    fun suggestMapsAdapterFailuresCorrectly() = runBlocking {
        val store = TestStore()
        store.save(
            ProviderConfig(providerId = "openrouter", configuredAtEpochMs = 12345L),
            SecretBytes("sk-key".toByteArray(StandardCharsets.UTF_8)),
        )

        var returnFailure: AdapterResult = AdapterResult.AuthFailure
        val resolving = ResolvingProvider(
            store = store,
            adapterFactory = { _, _, _ ->
                object : ProviderAdapter {
                    override val providerId = "openrouter"
                    override val displayName = "OpenRouter"
                    override suspend fun rewrite(system: String, text: String, secret: SecretBytes): AdapterResult {
                        secret.value.fill(0)
                        return returnFailure
                    }
                }
            },
        )

        val authResult = resolving.suggest("sys", "test", count = 3)
        assertTrue(authResult.isFailure)
        assertEquals("Authentication failure", authResult.exceptionOrNull()?.message)

        returnFailure = AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT)
        val timeoutResult = resolving.suggest("sys", "test", count = 3)
        assertTrue(timeoutResult.isFailure)
        assertEquals("Network failure: TIMEOUT", timeoutResult.exceptionOrNull()?.message)
    }
}
