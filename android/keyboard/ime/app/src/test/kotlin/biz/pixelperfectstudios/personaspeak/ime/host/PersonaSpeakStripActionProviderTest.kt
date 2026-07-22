package biz.pixelperfectstudios.personaspeak.ime.host

import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonaSpeakStripActionProviderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `inflateActionView returns a ComposeView with no parent`() {
        val owners = ImeViewTreeOwners()
        val provider = PersonaSpeakStripActionProvider(owners)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())

        val view = provider.inflateActionView(parent)

        assertNull(view.parent)
        assertTrue(view is ComposeView)
    }

    @Test
    fun `onRemoved disposes composition and is idempotent`() {
        val owners = ImeViewTreeOwners()
        val provider = PersonaSpeakStripActionProvider(owners)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())

        provider.inflateActionView(parent)
        owners.startInput()

        provider.onRemoved()
        provider.onRemoved() // should not throw
    }

    @Test
    fun `destroy is idempotent`() {
        val owners = ImeViewTreeOwners()
        val provider = PersonaSpeakStripActionProvider(owners)
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())

        provider.inflateActionView(parent)
        provider.destroy()
        provider.destroy() // should not throw
    }
}
