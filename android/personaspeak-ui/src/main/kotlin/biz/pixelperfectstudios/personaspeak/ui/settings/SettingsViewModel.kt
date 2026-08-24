package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.lifecycle.ViewModel
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel managing PersonaSpeak Settings state and navigation.
 */
class SettingsViewModel(
    private val personasRepo: PersonaRepository,
    initialDestination: SettingsDestination = SettingsDestination.Home,
    initialPersonaId: PersonaId = PersonaId.bundled("jeeves"),
    initialMood: Mood = Mood.DEFAULT,
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
        _state.update { current ->
            current.copy(
                activePersonaId = personaId,
                notice = "Active character updated. Takes effect on next keyboard initialization.",
            )
        }
    }

    fun selectDefaultMood(mood: Mood) {
        _state.update { current ->
            current.copy(
                defaultMood = mood,
                notice = "Default mood updated. Takes effect on next keyboard initialization.",
            )
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }
}
