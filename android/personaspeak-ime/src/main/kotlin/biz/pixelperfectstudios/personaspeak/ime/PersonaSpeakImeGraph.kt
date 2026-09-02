package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.SavedStateHandle
import biz.pixelperfectstudios.personaspeak.ime.editor.EditorSessionState
import biz.pixelperfectstudios.personaspeak.ime.editor.InputConnectionEditorPort
import biz.pixelperfectstudios.personaspeak.ime.host.ImeViewTreeOwners
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewriteCoordinator
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelViewModel
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState

/**
 * Host-neutral object graph for the PersonaSpeak IME layer: the race-guarded
 * editor port, the resolving provider (configured brain or FakeProvider
 * fallback), the bundled persona repository, and the rewrite coordinator.
 *
 * An IME host (keyboard fork) supplies the InputConnection/EditorInfo
 * suppliers and a surface to render the rewrite panel in. The host owns
 * forwarding framework lifecycle callbacks
 * (onStartInput/onUpdateSelection/onFinishInput) to [sessionState] and
 * [resolvingProvider] so session and generation tokens stay valid.
 */
class PersonaSpeakImeGraph(
    context: Context,
    private val inputConnectionSupplier: () -> InputConnection?,
    private val editorInfoSupplier: () -> EditorInfo?,
) {
    val sessionState = EditorSessionState()
    val editorPort: InputConnectionEditorPort = InputConnectionEditorPort(
        sessionState = sessionState,
        connectionSupplier = inputConnectionSupplier,
        editorInfoSupplier = editorInfoSupplier,
    )
    val resolvingProvider = ResolvingProvider(
        store = PersonaSpeakBrain.createStore(context),
        fallback = FakeProvider(),
    )
    val personaRepo = BundledPersonaRepository(
        AssetPersonaDocumentSource(context.assets),
    )
    val coordinator = RewriteCoordinator(
        personas = personaRepo,
        editor = editorPort,
        provider = resolvingProvider,
    )
    val owners = ImeViewTreeOwners()

    /**
     * Session starts at the host's onStartInput — the first callback in the
     * input sequence — rather than onStartInputView, so the session token is
     * valid for any selection callbacks that fire between the two.
     */
    fun onStartInput() {
        sessionState.start(editorInfoSupplier())
    }

    fun createRewritePanelViewModel(): RewritePanelViewModel {
        val session = PersonaSpeakSessionState.instance
        return RewritePanelViewModel(
            coordinator = coordinator,
            personas = personaRepo,
            sessionState = session,
            initialPersonaId = session.activePersonaId,
            initialMood = session.defaultMood,
            savedStateHandle = SavedStateHandle(),
        )
    }
}
