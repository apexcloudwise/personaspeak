package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigSnapshot
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreFailure
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.charset.StandardCharsets

class SettingsViewModelStoreOutcomeTest {

    private val emptyRepo = object : PersonaRepository {
        override fun list(): Result<List<PersonaSummary>> = Result.success(emptyList())
        override fun loadAll(): Result<List<ValidatedPersona>> = Result.success(emptyList())
        override fun load(id: PersonaId): Result<ValidatedPersona> =
            Result.failure(IllegalArgumentException("none"))
    }

    class FakeStore(
        var outcome: StoreOutcome = StoreOutcome.Unconfigured,
        var secretBytes: ByteArray? = null,
    ) : ProviderConfigStore {
        var clearCalled = false
        var savedConfig: ProviderConfig? = null

        override suspend fun load(): ProviderConfigSnapshot {
            return ProviderConfigSnapshot(outcome, secretBytes?.let { SecretBytes(it) })
        }

        override suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome {
            savedConfig = config
            secretBytes = secret.value
            outcome = StoreOutcome.Configured(config.providerId, config.configuredAtEpochMs, "gen-1")
            return outcome
        }

        override suspend fun clear() {
            clearCalled = true
            outcome = StoreOutcome.Unconfigured
            secretBytes = null
        }
    }

    @Test
    fun loadTransitionsStoreOutcomeToConfigured() = runTest {
        val fakeStore = FakeStore(
            outcome = StoreOutcome.Configured("anthropic", 1000L, "gen-1"),
            secretBytes = "test-key".toByteArray(StandardCharsets.UTF_8),
        )

        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )

        vm.loadProviderConfig()

        assertEquals(StoreOutcome.Configured("anthropic", 1000L, "gen-1"), vm.state.value.providerOutcome)
    }

    @Test
    fun saveProviderKeyTransitionsToConfigured() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )

        val outcome = vm.saveProviderKey(
            providerId = "anthropic",
            keyBytes = "sk-ant-test".toByteArray(StandardCharsets.UTF_8),
            epochMs = 5000L,
        )

        assertEquals(StoreOutcome.Configured("anthropic", 5000L, "gen-1"), outcome)
        assertEquals(StoreOutcome.Configured("anthropic", 5000L, "gen-1"), vm.state.value.providerOutcome)
        assertFalse(vm.state.value.isSavingProvider)
    }

    @Test
    fun clearProviderTransitionsToUnconfigured() = runTest {
        val fakeStore = FakeStore(
            outcome = StoreOutcome.Configured("anthropic", 1000L, "gen-1"),
            secretBytes = "test-key".toByteArray(StandardCharsets.UTF_8),
        )
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )

        vm.clearProvider()

        assertEquals(StoreOutcome.Unconfigured, vm.state.value.providerOutcome)
        assertEquals(true, fakeStore.clearCalled)
    }

    @Test
    fun unavailableStorePreservesStateWithoutMutation() = runTest {
        val fakeStore = FakeStore(
            outcome = StoreOutcome.Unavailable(StoreFailure.KEYSTORE_UNAVAILABLE),
        )
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )

        vm.loadProviderConfig()

        assertEquals(StoreOutcome.Unavailable(StoreFailure.KEYSTORE_UNAVAILABLE), vm.state.value.providerOutcome)
    }
}
