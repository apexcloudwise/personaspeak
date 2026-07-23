package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import biz.pixelperfectstudios.personaspeak.ime.editor.EditorSessionState
import biz.pixelperfectstudios.personaspeak.ime.editor.InputConnectionEditorPort
import biz.pixelperfectstudios.personaspeak.ime.host.ImeViewTreeOwners
import biz.pixelperfectstudios.personaspeak.ime.host.PersonaSpeakStripActionProvider
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewriteCoordinator
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanel
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelViewModel
import com.menny.android.anysoftkeyboard.LauncherSettingsActivity

class PersonaSpeakComposition(
    private val context: Context,
    private val inputConnectionSupplier: () -> InputConnection?,
    private val editorInfoSupplier: () -> EditorInfo?,
) {

    private val personaId = PersonaId.bundled("jeeves")
    private val sessionState = EditorSessionState()
    private val editorPort = InputConnectionEditorPort(
        sessionState = sessionState,
        connectionSupplier = inputConnectionSupplier,
        editorInfoSupplier = editorInfoSupplier,
    )
    private val provider = FakeProvider()
    private val coordinator = RewriteCoordinator(
        personas = BundledPersonaRepository(
            AssetPersonaDocumentSource(context.assets),
        ),
        editor = editorPort,
        provider = provider,
    )

    val owners = ImeViewTreeOwners()
    private val stripProvider = PersonaSpeakStripActionProvider(owners)
    private var container: com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView? = null

    fun onCreateInputView(containerView: View, window: Window?) {
        container = containerView as? com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView
        val decorView = window?.decorView
        if (decorView != null) {
            owners.installOn(decorView, containerView)
        } else {
            owners.installOn(containerView, containerView)
        }
    }

    fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        // Session starts here (first lifecycle callback in the input sequence)
        // rather than in onStartInputView so the token is valid for any
        // selection callbacks that fire between onStartInput and onStartInputView.
        sessionState.start(editorInfoSupplier())
    }

    fun onStartInputView() {
        owners.startInput()
        val c = container ?: return
        c.addStripAction(stripProvider, true)
        val composeView = stripProvider.lastComposeView ?: return
        val vm = ViewModelProvider(
            owners.viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    return RewritePanelViewModel(
                        coordinator = coordinator,
                        personaId = personaId,
                        savedStateHandle = SavedStateHandle(),
                    ) as T
                }
            },
        )[RewritePanelViewModel::class.java]
        composeView.setContent {
            val state by vm.state.collectAsState()
            RewritePanel(
                state = state,
                onRewrite = vm::request,
                onApply = vm::apply,
                onDismiss = vm::dismiss,
                onSettings = { launchSettings() },
            )
        }
    }

    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
    ) {
        sessionState.selectionChanged(newSelStart, newSelEnd)
    }

    fun onFinishInput() {
        owners.finishInput()
        val c = container ?: return
        c.removeStripAction(stripProvider)
    }

    fun onDestroy() {
        stripProvider.destroy()
        owners.destroy()
        sessionState.finish()
    }

    private fun launchSettings() {
        val intent = Intent(context, LauncherSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
