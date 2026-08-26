package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.ProviderCatalog
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing PersonaSpeak Settings state and navigation.
 */
class SettingsViewModel(
    private val personasRepo: PersonaRepository,
    initialDestination: SettingsDestination = SettingsDestination.Home,
    private val sessionState: PersonaSpeakSessionState = PersonaSpeakSessionState.instance,
    initialPersonaId: PersonaId = sessionState.activePersonaId,
    initialMood: Mood = sessionState.defaultMood,
    private val providerStore: ProviderConfigStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            destination = initialDestination,
            activePersonaId = initialPersonaId,
            defaultMood = initialMood,
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadPersonas()
    }

    fun loadPersonas() {
        val loaded = personasRepo.loadAll().getOrDefault(emptyList())
        _state.update { current ->
            val detailPersona = if (current.destination is SettingsDestination.PersonaDetail) {
                loaded.find { it.id == current.destination.personaId }
            } else current.selectedDetailPersona
            current.copy(
                personas = loaded,
                selectedDetailPersona = detailPersona,
            )
        }
    }

    fun navigateTo(destination: SettingsDestination) {
        _state.update { current ->
            val detailPersona = if (destination is SettingsDestination.PersonaDetail) {
                current.personas.find { it.id == destination.personaId }
            } else null
            current.copy(
                destination = destination,
                selectedDetailPersona = detailPersona,
                notice = null,
            )
        }
    }

    fun selectPersona(personaId: PersonaId) {
        sessionState.activePersonaId = personaId
        _state.update { current ->
            current.copy(
                activePersonaId = personaId,
                notice = "Active character updated. Takes effect on next keyboard initialization in this session (not saved to disk).",
            )
        }
    }

    fun selectDefaultMood(mood: Mood) {
        sessionState.defaultMood = mood
        _state.update { current ->
            current.copy(
                defaultMood = mood,
                notice = "Default mood updated. Takes effect on next keyboard initialization in this session (not saved to disk).",
            )
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * Re-reads the provider-config store outcome into [ProviderStatusSummary].
     * Never touches or retains the secret.
     */
    suspend fun refreshProviderStatus() {
        val store = providerStore ?: return
        val summary = try {
            when (val outcome = store.load().outcome) {
                is StoreOutcome.Configured ->
                    ProviderStatusSummary.Configured(outcome.providerId, outcome.configuredAtEpochMs)
                StoreOutcome.Unconfigured -> ProviderStatusSummary.Unconfigured
                is StoreOutcome.Unavailable -> ProviderStatusSummary.Unavailable
                StoreOutcome.InvalidCredentials -> ProviderStatusSummary.InvalidCredentials
            }
        } catch (_: Throwable) {
            ProviderStatusSummary.Unavailable
        }
        _state.update { it.copy(providerStatus = summary) }
    }

    /**
     * Persists [apiKey] and optional [model] override for [providerId],
     * refreshes status, and posts a notice. Key material is passed straight
     * into the store and never surfaced again.
     */
    fun saveProvider(providerId: String, apiKey: String, model: String?, onDone: () -> Unit) {
        val store = providerStore
        if (store == null) {
            onDone()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSavingProvider = true) }
            val outcome = try {
                store.save(
                    ProviderConfig(
                        providerId,
                        System.currentTimeMillis(),
                        model = model?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                    SecretBytes(apiKey.toByteArray()),
                )
            } catch (_: Throwable) {
                null
            }
            refreshProviderStatus()
            _state.update { it.copy(isSavingProvider = false) }
            val displayName = ProviderCatalog.byId(providerId)?.displayName
            _state.update {
                it.copy(
                    notice = if (outcome is StoreOutcome.Configured && displayName != null) {
                        "Brain connected: $displayName."
                    } else {
                        "Could not save the key. Check storage and try again."
                    }
                )
            }
            onDone()
        }
    }

    /** Clears the stored configuration and refreshes status. */
    fun clearProvider(onDone: () -> Unit) {
        val store = providerStore
        if (store == null) {
            onDone()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSavingProvider = true) }
            try {
                store.clear()
            } catch (_: Throwable) {
            }
            refreshProviderStatus()
            _state.update {
                it.copy(
                    isSavingProvider = false,
                    notice = "Brain key removed. The offline understudy takes over.",
                )
            }
            onDone()
        }
    }
}
