package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RewritePanelViewModel(
    private val coordinator: RewriteCoordinator,
    private val personaId: PersonaId,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<RewritePanelState>(RewritePanelState.Idle)
    val state: StateFlow<RewritePanelState> = _state.asStateFlow()

    private var currentCandidate: RewriteCandidate? = null
    private var activeRequest: Job? = null
    private var editorFinished = false

    fun request() {
        if (editorFinished) return
        activeRequest?.cancel()
        _state.value = RewritePanelState.Loading
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.request(personaId)) {
                is RewriteRequestResult.Ready -> {
                    currentCandidate = result.candidate
                    _state.value = RewritePanelState.Review(candidate = result.candidate)
                }
                is RewriteRequestResult.NoPersona ->
                    _state.value = RewritePanelState.Message(RewriteMessage.NoPersona)
                is RewriteRequestResult.EmptyInput ->
                    _state.value = RewritePanelState.Message(RewriteMessage.EmptyInput)
                is RewriteRequestResult.SensitiveEditor ->
                    _state.value = RewritePanelState.Message(RewriteMessage.SensitiveEditor)
                is RewriteRequestResult.UnsupportedEditor ->
                    _state.value = RewritePanelState.Message(RewriteMessage.UnsupportedEditor)
                is RewriteRequestResult.IncompleteRead ->
                    _state.value = RewritePanelState.Message(RewriteMessage.IncompleteRead)
                is RewriteRequestResult.OversizedInput ->
                    _state.value = RewritePanelState.Message(RewriteMessage.OversizedInput)
                is RewriteRequestResult.ProviderFailure ->
                    _state.value = RewritePanelState.Message(RewriteMessage.ProviderFailure)
                is RewriteRequestResult.MalformedResponse ->
                    _state.value = RewritePanelState.Message(RewriteMessage.MalformedResponse)
            }
        }
    }

    fun apply() {
        val candidate = currentCandidate ?: return
        currentCandidate = null
        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            when (val result = coordinator.apply(candidate)) {
                is ApplyResult.AppliedVerified ->
                    _state.value = RewritePanelState.Review(candidate, RewriteOutcome.Applied)
                is ApplyResult.Stale ->
                    _state.value = RewritePanelState.Review(candidate, RewriteOutcome.Stale(result.reason))
                is ApplyResult.WriteRejected ->
                    _state.value = RewritePanelState.Review(candidate, RewriteOutcome.Rejected)
                is ApplyResult.WriteUnconfirmed ->
                    _state.value = RewritePanelState.Review(candidate, RewriteOutcome.Unconfirmed)
            }
        }
    }

    fun dismiss() {
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.Idle
    }

    fun finish() {
        editorFinished = true
        activeRequest?.cancel()
        currentCandidate = null
        _state.value = RewritePanelState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        activeRequest?.cancel()
        currentCandidate = null
    }
}
