package biz.pixelperfectstudios.personaspeak.ime.host

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeViewTreeOwnersTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `installOn sets view tree owners on both decor and ComposeView`() {
        val owners = ImeViewTreeOwners()
        val decor = FrameLayout(context)
        val composeView = ComposeView(context)

        owners.installOn(decor, composeView)

        assertSame(owners, decor.findViewTreeLifecycleOwner())
        assertSame(owners, decor.findViewTreeSavedStateRegistryOwner())
        assertSame(owners, decor.findViewTreeViewModelStoreOwner())
        assertSame(owners, composeView.findViewTreeLifecycleOwner())
        assertSame(owners, composeView.findViewTreeSavedStateRegistryOwner())
        assertSame(owners, composeView.findViewTreeViewModelStoreOwner())
    }

    @Test
    fun `startInput drives lifecycle to RESUMED`() {
        val owners = ImeViewTreeOwners()

        owners.startInput()

        assertEquals(Lifecycle.State.RESUMED, owners.lifecycle.currentState)
    }

    @Test
    fun `finishInput drives lifecycle to DESTROYED`() {
        val owners = ImeViewTreeOwners()
        owners.startInput()

        owners.finishInput()

        assertEquals(Lifecycle.State.DESTROYED, owners.lifecycle.currentState)
    }

    @Test
    fun `owners survive finish start cycle`() {
        val owners = ImeViewTreeOwners()
        val decor = FrameLayout(context)
        val composeView = ComposeView(context)

        owners.installOn(decor, composeView)
        owners.startInput()
        owners.finishInput()
        owners.startInput()

        assertSame(owners, decor.findViewTreeLifecycleOwner())
        assertSame(owners, composeView.findViewTreeLifecycleOwner())
        assertEquals(Lifecycle.State.RESUMED, owners.lifecycle.currentState)
    }

    @Test
    fun `finishInput clears ViewModelStore`() {
        val owners = ImeViewTreeOwners()
        owners.startInput()
        val store = owners.viewModelStore

        owners.finishInput()

        owners.startInput()
        assertNotSame(store, owners.viewModelStore)
    }

    @Test
    fun `savedStateRegistry is ready for non-content consumers and content keys are absent`() {
        val owners = ImeViewTreeOwners()

        owners.startInput()

        // Registering a non-content provider proves performAttach + performRestore were called
        // (registerSavedStateProvider throws if the registry is not attached and restored)
        val provider = androidx.savedstate.SavedStateRegistry.SavedStateProvider {
            Bundle().apply { putString("payload", "value") }
        }
        owners.savedStateRegistry.registerSavedStateProvider("allowed_non_content_key", provider)

        // No content keys are stored
        assertNull(owners.savedStateRegistry.consumeRestoredStateForKey("draft"))
        assertNull(owners.savedStateRegistry.consumeRestoredStateForKey("result"))
        assertNull(owners.savedStateRegistry.consumeRestoredStateForKey("snapshot"))
        assertNull(owners.savedStateRegistry.consumeRestoredStateForKey("candidate"))

        owners.savedStateRegistry.unregisterSavedStateProvider("allowed_non_content_key")
        owners.finishInput()
    }
}
