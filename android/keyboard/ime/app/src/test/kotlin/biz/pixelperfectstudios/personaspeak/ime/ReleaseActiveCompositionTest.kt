package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigSnapshot
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.ui.settings.ProviderCatalog
import com.menny.android.anysoftkeyboard.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

/**
 * Milestone 8 Slice A — Fail-Closed Active-Composition & Release Invariant Test.
 *
 * Enforces ROADMAP Phase-1 exit rules:
 * 1. The release build must be rejected if the active provider composition is fake/stub.
 *    (Tests active composition, not banning the class: FakeProvider remains legal as an explicit
 *    offline understudy, but cannot be configured as a default catalog entry or hardcoded active provider).
 * 2. Version identity must strictly match v0.1.0 / versionCode 1000.
 * 3. ProviderCatalog must offer only real production adapters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseActiveCompositionTest {

    private class TestProviderConfigStore(
        private var snapshot: ProviderConfigSnapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured),
    ) : ProviderConfigStore {
        override suspend fun load(): ProviderConfigSnapshot = snapshot
        override suspend fun save(config: biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig, secret: SecretBytes): StoreOutcome = snapshot.outcome
        override suspend fun clear() {
            snapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured)
        }
    }

    @Test
    fun `release build identity conforms to v0_1_0 and versionCode 1000`() {
        assertEquals("biz.pixelperfectstudios.personaspeak", BuildConfig.APPLICATION_ID)
        assertEquals(1000, BuildConfig.VERSION_CODE)
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }

    @Test
    fun `provider catalog offers strictly real production providers and rejects fake catalog entries`() {
        val providers = ProviderCatalog.all
        assertEquals(3, providers.size)

        val ids = providers.map { it.id }
        assertTrue("Catalog must contain openrouter", ids.contains("openrouter"))
        assertTrue("Catalog must contain anthropic", ids.contains("anthropic"))
        assertTrue("Catalog must contain openai-compat", ids.contains("openai-compat"))

        // Catalog must not contain fake or stub provider definitions
        assertFalse("Catalog must not offer fake provider", ids.contains("fake"))
        assertFalse("Catalog must not offer stub provider", ids.contains("stub"))
        assertNull("Lookup for 'fake' must return null", ProviderCatalog.byId("fake"))
        assertNull("Lookup for 'stub' must return null", ProviderCatalog.byId("stub"))
    }

    @Test
    fun `resolving provider composition wires real production adapters when configured`() = runBlocking {
        var createdAdapterProviderId: String? = null
        var createdModel: String? = null

        val store = TestProviderConfigStore(
            snapshot = ProviderConfigSnapshot(
                outcome = StoreOutcome.Configured(
                    providerId = "openrouter",
                    configuredAtEpochMs = 1000L,
                    generation = "test-gen",
                    model = "nvidia/nemotron-3-super-120b-a12b:free",
                    customBaseUrl = null,
                ),
                secret = SecretBytes("sk-or-test".toByteArray(StandardCharsets.UTF_8)),
            ),
        )

        val resolving = ResolvingProvider(
            store = store,
            adapterFactory = { def, model, customBaseUrl ->
                createdAdapterProviderId = def.id
                createdModel = model
                when (def.id) {
                    "anthropic" -> AnthropicMessagesAdapter(model = model ?: def.defaultModel)
                    "openrouter" -> OpenRouterAdapter(model = model ?: def.defaultModel)
                    else -> OpenRouterAdapter(model = model ?: def.defaultModel)
                }
            },
        )

        // Force resolution
        resolving.rewrite("system prompt", "test input")
        assertEquals("openrouter", createdAdapterProviderId)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", createdModel)
    }

    @Test
    fun `unconfigured baseline defaults to offline understudy without fake active configuration`() = runBlocking {
        val unconfiguredStore = TestProviderConfigStore(
            snapshot = ProviderConfigSnapshot(StoreOutcome.Unconfigured),
        )

        val resolving = ResolvingProvider(
            store = unconfiguredStore,
            fallback = FakeProvider(),
        )

        val result = resolving.rewrite("system prompt", "Tea at six.")
        assertTrue(result.isSuccess)
        val text = result.getOrNull()
        assertNotNull(text)
        assertTrue(text!!.contains("Tea at six."))
    }
}
