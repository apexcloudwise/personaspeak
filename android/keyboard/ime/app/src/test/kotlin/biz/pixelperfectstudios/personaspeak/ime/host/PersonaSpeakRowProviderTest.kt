package biz.pixelperfectstudios.personaspeak.ime.host

import android.view.ViewGroup
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
class PersonaSpeakRowProviderTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `inflateExtensionRow returns parentless match-width wrap-height ComposeView`() {
        val provider = PersonaSpeakRowProvider(ImeViewTreeOwners())
        val parent = FrameLayout(context)

        val view = provider.inflateExtensionRow(parent)

        assertNull(view.parent)
        assertTrue(view is ComposeView)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.height)
    }

    @Test
    fun `onRemoved clears view and remains idempotent`() {
        val owners = ImeViewTreeOwners()
        val provider = PersonaSpeakRowProvider(owners)
        provider.inflateExtensionRow(FrameLayout(context))
        owners.startInput()

        provider.onRemoved()
        provider.onRemoved()

        assertNull(provider.lastComposeView)
    }

    @Test
    fun `destroy clears view and remains idempotent`() {
        val provider = PersonaSpeakRowProvider(ImeViewTreeOwners())
        provider.inflateExtensionRow(FrameLayout(context))

        provider.destroy()
        provider.destroy()

        assertNull(provider.lastComposeView)
    }
}
