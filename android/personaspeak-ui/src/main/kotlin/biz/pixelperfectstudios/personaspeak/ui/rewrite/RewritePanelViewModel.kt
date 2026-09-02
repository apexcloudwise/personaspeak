package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.reply.IncomingMessageStore
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RewritePanelViewModel(
    private val coordinator: RewriteCoordinator,
    private val personas: PersonaRepository,
    private val sessionState: PersonaSpeakSessionState = PersonaSpeakSessionState.instance,
    initialPersonaId: PersonaId = sessionState.activePersonaId,
    initialMood: Mood = sessionState.defaultMood,
    private val replyStore: IncomingMessageStore = IncomingMessageStore.instance,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {

    private var activePersona: ValidatedPersona = resolvePersona(initialPersonaId)
    private var activeMood: Mood = initialMood

    private val _state = MutableStateFlow<RewritePanelState>(
        RewritePanelState.Resting(activePersona, activeMood),
    )
    val state: StateFlow<RewritePanelState> = _state.asStateFlow()

    /** The latest incoming message, for the "Replying to: …" chip while Resting. */
    val replyContext: StateFlow<IncomingMessageContext?> = replyStore.state
        .map { entries -> entries.values.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    /**
     * Drafts suggestions for the latest incoming message (ADR-0011). Valid
     * while Resting; the message stays cached on cancel or error.
     */
    fun requestSuggestions() {
        if (editorFinished) return
        val key = replyStore.peekLatestKey() ?: return
        val context = replyStore.peek(key) ?: return
        launchSuggestions(key, context)
    }

    /** Re-rolls suggestions for the conversation currently on the strip. */
    fun regenerateSuggestions() {
        if (editorFinished) return
        val current = _state.value
        val key = when (current) {
            is RewritePanelState.Suggesting -> current.conversationKey
            is RewritePanelState.Suggestions -> current.conversationKey
            else -> return
        }
        if (replyStore.peek(key) == null) {
            // The context was forgotten mid-flow (access revoked, store wiped):
            // typed return to Resting via dismiss.
            activeRequest?.cancel()
            _state.value = RewritePanelState.Error(
                error = StitchError.ReplyContextGone,
                persona = activePersona,
                mood = activeMood,
            )
            return
        }
        val context = when (current) {
            is RewritePanelState.Suggesting -> current.context
            is RewritePanelState.Suggestions -> current.context
            else -> return
        }
        launchSuggestions(key, context)
    }

    private fun launchSuggestions(key: String, context: IncomingMessageContext) {
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.Suggesting(activePersona, activeMood, context, key)
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.requestSuggestions(activePersona.id, activeMood, context)) {
                is SuggestResult.Ready ->
                    _state.value = RewritePanelState.Suggestions(
                        persona = activePersona,
                        mood = activeMood,
                        context = result.suggestions.context,
                        conversationKey = key,
                        replies = result.suggestions.replies,
                    )
                is SuggestResult.NoPersona ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.NoProvider,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is SuggestResult.ProviderFailure ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.ProviderFailure,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is SuggestResult.MalformedResponse ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.MalformedResponse,
                        persona = activePersona,
                        mood = activeMood,
                    )
            }
        }
    }

    /**
     * Applies the suggestion at [index] as exactly one editor mutation, then
     * forgets the conversation ("forgotten on reply", ADR-0011 §2). Dismiss
     * keeps the message for a retry instead.
     */
    fun applySuggestion(index: Int) {
        if (editorFinished) return
        val current = _state.value as? RewritePanelState.Suggestions ?: return
        val text = current.replies.getOrNull(index) ?: return
        val key = current.conversationKey

        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.applySuggestion(text)) {
                is ApplySuggestionResult.AppliedVerified -> {
                    replyStore.forget(key)
                    _state.value = RewritePanelState.Resting(activePersona, activeMood)
                }
                is ApplySuggestionResult.Stale ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.StaleEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.WriteRejected ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.WriteRejected,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.WriteUnconfirmed ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.WriteUnconfirmed,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.SensitiveEditor ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.SensitiveEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.UnsupportedEditor ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.UnsupportedEditor,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.IncompleteRead ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.IncompleteRead,
                        persona = activePersona,
                        mood = activeMood,
                    )
                is ApplySuggestionResult.OversizedInput ->
                    _state.value = RewritePanelState.Error(
                        error = StitchError.OversizedInput,
                        persona = activePersona,
                        mood = activeMood,
                    )
            }
        }
    }

    /** Leaves the suggestions view; the cached message is kept for a retry. */
    fun dismissSuggestions() {
        if (_state.value is RewritePanelState.Suggesting || _state.value is RewritePanelState.Suggestions) {
            dismiss()
        }
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
