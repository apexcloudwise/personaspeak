package biz.pixelperfectstudios.personaspeak.ime.host

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Provides [LifecycleOwner], [SavedStateRegistryOwner], and
 * [ViewModelStoreOwner] for Compose views hosted inside an
 * [android.inputmethodservice.InputMethodService] input view.
 *
 * The ASK IME window does not supply these owners by default.
 * Install them on both the IME decor view and the ComposeView
 * so Compose's recomposer can find them via ViewTree lookups.
 */
class ImeViewTreeOwners : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private var lifecycleRegistry = LifecycleRegistry(this)
    private var savedStateController = SavedStateRegistryController.create(this)
    private var _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    /**
     * Installs this owner on both the IME decor view and the [composeView].
     * Must be called before the first setContent.
     */
    fun installOn(decorView: View, composeView: View) {
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
    }

    /**
     * Drives the lifecycle from INITIALIZED through CREATED, STARTED, to RESUMED.
     * Restores saved state before any consumer can access it.
     * If the lifecycle was previously DESTROYED, creates fresh registries.
     *
     * Note: savedstate 1.3.0's [SavedStateRegistryController.performRestore] internally
     * calls [SavedStateRegistryController.performAttach] if not already attached, but the
     * plan mandates the explicit ordering — a future library version could change that
     * internal behavior. The consumer-readiness test serves as a regression guard.
     */
    fun startInput() {
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) return
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            lifecycleRegistry = LifecycleRegistry(this)
            savedStateController = SavedStateRegistryController.create(this)
            _viewModelStore = ViewModelStore()
        }
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /**
     * Drives the lifecycle from RESUMED back through STARTED, CREATED, to DESTROYED.
     * Clears the ViewModelStore.
     */
    fun finishInput() {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }

    /**
     * Idempotent destroy — delegates to [finishInput].
     * Present so callers have an explicit teardown entry point.
     */
    fun destroy() {
        finishInput()
    }
}
