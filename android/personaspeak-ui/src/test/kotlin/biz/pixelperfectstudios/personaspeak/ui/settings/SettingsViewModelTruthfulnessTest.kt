package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigSnapshot
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

class SettingsViewModelTruthfulnessTest {

    private val emptyRepo = object : PersonaRepository {
        override fun list(): Result<List<PersonaSummary>> = Result.success(emptyList())
        override fun loadAll(): Result<List<ValidatedPersona>> = Result.success(emptyList())
        override fun load(id: PersonaId): Result<ValidatedPersona> =
            Result.failure(IllegalArgumentException("none"))
    }

    class FakeStore(
        var outcome: StoreOutcome = StoreOutcome.Configured("anthropic", 12345L, "gen-1"),
        var secretBytes: ByteArray? = "my-secret-key".toByteArray(StandardCharsets.UTF_8),
    ) : ProviderConfigStore {
        var clearCount = 0

        override suspend fun load(): ProviderConfigSnapshot =
            ProviderConfigSnapshot(outcome, secretBytes?.let { SecretBytes(it) })

        override suspend fun save(config: ProviderConfig, secret: SecretBytes): StoreOutcome {
            outcome = StoreOutcome.Configured(config.providerId, config.configuredAtEpochMs, "gen-1")
            return outcome
        }

        override suspend fun clear() {
            clearCount++
            outcome = StoreOutcome.Unconfigured
            secretBytes = null
        }
    }

    @Test
    fun authFailureSetsLastRewriteResultWithoutWipingOrChangingStoreOutcome() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )
        vm.loadProviderConfig()

        assertEquals(StoreOutcome.Configured("anthropic", 12345L, "gen-1"), vm.state.value.providerOutcome)
        assertNull(vm.state.value.lastRewriteResult)

        // Simulate adapter returning AuthFailure on rewrite
        vm.onRewriteCompleted(AdapterResult.AuthFailure)

        // A4 truthfulness assertion: store is NOT wiped, providerOutcome remains Configured
        assertEquals(StoreOutcome.Configured("anthropic", 12345L, "gen-1"), vm.state.value.providerOutcome)
        assertEquals(AdapterResult.AuthFailure, vm.state.value.lastRewriteResult)
        assertEquals(0, fakeStore.clearCount)
    }

    @Test
    fun networkFailureSetsLastRewriteResultWithoutWipingOrChangingStoreOutcome() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )
        vm.loadProviderConfig()

        // Simulate adapter returning NetworkFailure(TIMEOUT) on rewrite
        vm.onRewriteCompleted(AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT))

        // Store is NOT mutated
        assertEquals(StoreOutcome.Configured("anthropic", 12345L, "gen-1"), vm.state.value.providerOutcome)
        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT), vm.state.value.lastRewriteResult)
        assertEquals(0, fakeStore.clearCount)
    }

    @Test
    fun successRewriteClearsLastRewriteResult() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )
        vm.loadProviderConfig()

        vm.onRewriteCompleted(AdapterResult.AuthFailure)
        assertEquals(AdapterResult.AuthFailure, vm.state.value.lastRewriteResult)

        vm.onRewriteCompleted(AdapterResult.Success("Rewritten draft text"))
        assertNull(vm.state.value.lastRewriteResult)
        assertEquals(StoreOutcome.Configured("anthropic", 12345L, "gen-1"), vm.state.value.providerOutcome)
    }

    @Test
    fun saveProviderKeyClearsLastRewriteResult() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )
        vm.onRewriteCompleted(AdapterResult.AuthFailure)
        assertEquals(AdapterResult.AuthFailure, vm.state.value.lastRewriteResult)

        vm.saveProviderKey("anthropic", "new-key".toByteArray(StandardCharsets.UTF_8))
        assertNull(vm.state.value.lastRewriteResult)
    }

    @Test
    fun clearProviderClearsLastRewriteResult() = runTest {
        val fakeStore = FakeStore()
        val vm = SettingsViewModel(
            personasRepo = emptyRepo,
            providerConfigStore = fakeStore,
        )
        vm.onRewriteCompleted(AdapterResult.AuthFailure)
        assertEquals(AdapterResult.AuthFailure, vm.state.value.lastRewriteResult)

        vm.clearProvider()
        assertNull(vm.state.value.lastRewriteResult)
        assertEquals(StoreOutcome.Unconfigured, vm.state.value.providerOutcome)
    }
}
