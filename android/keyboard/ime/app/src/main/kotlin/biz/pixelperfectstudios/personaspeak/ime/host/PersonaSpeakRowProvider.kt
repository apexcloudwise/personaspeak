package biz.pixelperfectstudios.personaspeak.ime.host

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView

/**
 * A [KeyboardViewContainerView.ExtensionRowProvider] that hosts Compose
 * content inside a dedicated keyboard extension row.
 */
class PersonaSpeakRowProvider(
    private val owners: ImeViewTreeOwners,
) : KeyboardViewContainerView.ExtensionRowProvider {

    private var composeView: ComposeView? = null

    val lastComposeView: ComposeView? get() = composeView

    override fun inflateExtensionRow(parent: ViewGroup): View {
        val view = ComposeView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
        }
        composeView = view
        return view
    }

    override fun onRemoved() {
        composeView?.disposeComposition()
        composeView = null
        owners.finishInput()
    }

    /**
     * Idempotent teardown.
     */
    fun destroy() {
        composeView?.disposeComposition()
        composeView = null
    }
}
