package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonaSpeakCompositionTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class RecordingKeyboardViewContainerView(context: Context) :
        KeyboardViewContainerView(context) {
        var extensionAdds = 0
        var extensionRemoves = 0
        var stripAdds = 0

        override fun addExtensionRow(provider: ExtensionRowProvider) {
            extensionAdds++
        }

        override fun removeExtensionRow(provider: ExtensionRowProvider) {
            extensionRemoves++
        }

        override fun addStripAction(provider: StripActionProvider, highPriority: Boolean) {
            stripAdds++
        }
    }

    @Test
    fun `input view uses one extension row and never a strip action`() {
        val container = RecordingKeyboardViewContainerView(context)
        val composition = PersonaSpeakComposition(context, { null }, { EditorInfo() })
        composition.onCreateInputView(container, null)
        composition.onStartInput(EditorInfo(), false)

        composition.onStartInputView()
        composition.onStartInputView()
        composition.onFinishInput()

        assertEquals(1, container.extensionAdds)
        assertEquals(1, container.extensionRemoves)
        assertEquals(0, container.stripAdds)
    }
}
