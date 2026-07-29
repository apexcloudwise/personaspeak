package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView
import androidx.compose.ui.platform.ComposeView
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
            super.addExtensionRow(provider)
            extensionAdds++
        }

        override fun removeExtensionRow(provider: ExtensionRowProvider) {
            super.removeExtensionRow(provider)
            extensionRemoves++
        }

        override fun addStripAction(provider: StripActionProvider, highPriority: Boolean) {
            super.addStripAction(provider, highPriority)
            stripAdds++
        }
    }

    @Test
    fun `input view uses one extension row and never a strip action`() {
        val container = RecordingKeyboardViewContainerView(context)
        var contentInstallations = 0
        val contentInstaller: (ComposeView, @androidx.compose.runtime.Composable () -> Unit) -> Unit = { _, _ ->
            contentInstallations++
        }

        val composition = PersonaSpeakComposition(
            context = context,
            inputConnectionSupplier = { null },
            editorInfoSupplier = { EditorInfo() },
            contentInstaller = contentInstaller
        )
        composition.onCreateInputView(container, null)
        composition.onStartInput(EditorInfo(), false)

        composition.onStartInputView()
        composition.onStartInputView()
        composition.onFinishInput()

        assertEquals(1, container.extensionAdds)
        assertEquals(1, container.extensionRemoves)
        assertEquals(0, container.stripAdds)
        assertEquals(1, contentInstallations)
    }
}
