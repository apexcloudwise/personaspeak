package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RewritePanelViewModel(
    private val coordinator: RewriteCoordinator,
    private val personas: PersonaRepository,
    private val sessionState: PersonaSpeakSessionState = PersonaSpeakSessionState.instance,
    initialPersonaId: PersonaId = sessionState.activePersonaId,
    initialMood: Mood = sessionState.defaultMood,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private var activePersona: ValidatedPersona = resolvePersona(initialPersonaId)
    private var activeMood: Mood = initialMood

    private val _state = MutableStateFlow<RewritePanelState>(
        RewritePanelState.Resting(activePersona, activeMood),
    )
    val state: StateFlow<RewritePanelState> = _state.asStateFlow()

    private var currentCandidate: RewriteCandidate? = null
    private var activeRequest: Job? = null
    private var editorFinished = false

    private fun resolvePersona(id: PersonaId): ValidatedPersona {
        return personas.load(id).getOrElse {
            personas.load(PersonaId.bundled("jeeves")).getOrElse {
                DEFAULT_FALLBACK_PERSONA
            }
        }
    }

    fun openPersonaPicker() {
        if (editorFinished) return
        activeRequest?.cancel()
        currentCandidate = null
        val allPersonas = personas.loadAll().getOrElse {
            listOf(activePersona)
        }.ifEmpty {
            listOf(activePersona)
        }
        _state.value = RewritePanelState.PersonaPicker(
            personas = allPersonas,
            selectedId = activePersona.id,
            currentMood = activeMood,
        )
    }

    fun selectPersona(personaId: PersonaId) {
        if (editorFinished) return
        sessionState.activePersonaId = personaId
        activePersona = resolvePersona(personaId)
        _state.value = RewritePanelState.Resting(activePersona, activeMood)
    }

    fun openMoodPicker() {
        if (editorFinished) return
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.MoodPicker(
            moods = Mood.ALL,
            selectedMood = activeMood,
            currentPersona = activePersona,
        )
    }

    fun selectMood(mood: Mood) {
        if (editorFinished) return
        sessionState.defaultMood = mood
        activeMood = mood
        _state.value = RewritePanelState.Resting(activePersona, activeMood)
    }

    fun dismissPicker() {
        _state.value = RewritePanelState.Resting(activePersona, activeMood)
    }

    fun request() {
        if (editorFinished) return
        activeRequest?.cancel()
        _state.value = RewritePanelState.Loading(activePersona, activeMood)
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.request(activePersona.id, activeMood)) {
                is RewriteRequestResult.Ready -> {
                    currentCandidate = result.candidate
                    _state.value = RewritePanelState.Review(
                        persona = activePersona,
                        mood = activeMood,
                        candidate = result.candidate,
                    )
                }
                is RewriteRequestResult.NoPersona -> {
                    activePersona = resolvePersona(PersonaId.bundled("jeeves"))
                    _state.value = RewritePanelState.Error(
                        error = StitchError.NoProvider,
                        persona = activePersona,
                        mood = activeMood,
                    )
                }
                is RewriteRequestResult.EmptyInput ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.EmptyInput,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.SensitiveEditor ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.SensitiveEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.UnsupportedEditor ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.UnsupportedEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.IncompleteRead ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.IncompleteRead,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.OversizedInput ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.OversizedInput,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.ProviderFailure ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.ProviderFailure,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is RewriteRequestResult.MalformedResponse ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.MalformedResponse,
                        persona = activePersona,
                        mood = activeMood,
                    )
            }
        }
    }

    fun apply() {
        val candidate = currentCandidate ?: return
        currentCandidate = null
        activeRequest?.cancel()
        _state.value = RewritePanelState.Applying(activePersona, activeMood, candidate)
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.apply(candidate)) {
                is ApplyResult.AppliedVerified ->
                    _state.value = RewritePanelState.AppliedVerified(
                        persona = activePersona,
                        mood = activeMood,
                        candidate = candidate,
                    )
                is ApplyResult.Stale ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.StaleEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplyResult.WriteRejected ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.WriteRejected,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplyResult.WriteUnconfirmed ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.WriteUnconfirmed,
                        persona = activePersona,
                        mood = activeMood,
                    )
            }
        }
    }

    fun dismiss() {
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.Resting(activePersona, activeMood)
    }

    fun finish() {
        editorFinished = true
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.Resting(activePersona, activeMood)
    }

    override fun onCleared() {
        super.onCleared()
        activeRequest?.cancel()
        currentCandidate = null
    }

    companion object {
        val DEFAULT_FALLBACK_PERSONA = ValidatedPersona(
            id = PersonaId.bundled("jeeves"),
            provenance = PersonaProvenance.bundled,
            content = Persona(
                name = "Jeeves",
                context = " (the impeccably composed valet from P.G. Wodehouse's Jeeves and Wooster)",
                speechPatterns = listOf("Impeccably formal, correct English"),
            ),
        )
    }
}
