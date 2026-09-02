package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import biz.pixelperfectstudios.personaspeak.ime.host.PersonaSpeakRowProvider
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanel
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelViewModel
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSettingsActivity

class PersonaSpeakComposition @JvmOverloads constructor(
    private val context: Context,
    inputConnectionSupplier: () -> InputConnection?,
    editorInfoSupplier: () -> EditorInfo?,
    private val contentInstaller: (ComposeView, @Composable () -> Unit) -> Unit = { view, content ->
        view.setContent(content)
    },
) {
    private val graph = PersonaSpeakImeGraph(
        context = context,
        inputConnectionSupplier = inputConnectionSupplier,
        editorInfoSupplier = editorInfoSupplier,
    )
    private val sessionState = graph.sessionState
    private val resolvingProvider = graph.resolvingProvider
    private val personaRepo = graph.personaRepo
    private val coordinator = graph.coordinator

    val owners = graph.owners
    private val rowProvider = PersonaSpeakRowProvider(owners)
    private var container: com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView? = null
    private var isAdded = false

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
        // Delegates to the graph: session starts here (first lifecycle
        // callback in the input sequence) rather than in onStartInputView so
        // the token is valid for any selection callbacks that fire between
        // onStartInput and onStartInputView.
        graph.onStartInput()
    }

    fun onStartInputView() {
        resolvingProvider.invalidate()
        owners.startInput()
        val c = container ?: return
        if (isAdded) return
        c.addExtensionRow(rowProvider)
        isAdded = true
        val composeView = rowProvider.lastComposeView ?: return
        val vm = ViewModelProvider(
            owners.viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    return graph.createRewritePanelViewModel() as T
                }
            },
        )[RewritePanelViewModel::class.java]
        contentInstaller(composeView) {
            val state by vm.state.collectAsState()
            RewritePanel(
                state = state,
                onRewrite = vm::request,
                onApply = vm::apply,
                onDismiss = vm::dismiss,
                onSettings = { launchSettings() },
                // Geometry only. The panel freezes this before Review expands
                // the row, so it must read the container that hosts us, not
                // our own view.
                preExpansionImeHeightPx = {
                    (composeView.parent as? View)?.height ?: 0
                },
                onOpenPersonaPicker = vm::openPersonaPicker,
                onSelectPersona = vm::selectPersona,
                onOpenMoodPicker = vm::openMoodPicker,
                onSelectMood = vm::selectMood,
                onOpenPersonaBrowser = { launchSettings(PersonaSpeakSettingsActivity.DESTINATION_PERSONAS) },
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
        if (!isAdded) return
        c.removeExtensionRow(rowProvider)
        isAdded = false
    }

    fun onDestroy() {
        rowProvider.destroy()
        isAdded = false
        owners.destroy()
        sessionState.finish()
    }

    private fun launchSettings(destination: String = PersonaSpeakSettingsActivity.DESTINATION_HOME) {
        val intent = PersonaSpeakSettingsActivity.createIntent(context, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
