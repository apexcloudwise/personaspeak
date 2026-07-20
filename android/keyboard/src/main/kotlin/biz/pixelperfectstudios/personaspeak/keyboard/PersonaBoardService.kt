package biz.pixelperfectstudios.personaspeak.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider

/**
 * The thin persona keyboard (ADR-0001). Not a typing keyboard: a reply
 * panel you flip to, use, and flip back from. Compose needs view-tree
 * lifecycle owners that a plain InputMethodService doesn't provide, hence
 * the ceremony below.
 */
class PersonaBoardService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val provider = FakeProvider()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View =
        ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PersonaBoardService)
            setViewTreeSavedStateRegistryOwner(this@PersonaBoardService)
            setContent {
                PersonaPanel(
                    provider = provider,
                    onCommit = { reply ->
                        currentInputConnection?.commitText(reply, 1)
                    },
                    onSwitchBack = { switchBackToPreviousKeyboard() },
                )
            }
        }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun switchBackToPreviousKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToPreviousInputMethod()) requestHideSelf(0)
        } else {
            requestHideSelf(0)
        }
    }
}
