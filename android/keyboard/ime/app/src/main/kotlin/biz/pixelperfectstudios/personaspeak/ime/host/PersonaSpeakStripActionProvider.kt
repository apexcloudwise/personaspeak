package biz.pixelperfectstudios.personaspeak.ime.host

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.anysoftkeyboard.keyboards.views.KeyboardViewContainerView

/**
 * A [KeyboardViewContainerView.StripActionProvider] that hosts Compose
 * content inside the ASK input-view strip.
 *
 * The returned [ComposeView] is parentless and wrap-content. The caller
 * sets content via [ComposeView.setContent][androidx.compose.ui.platform.ComposeView.setContent]
 * after [inflateActionView] returns.
 *
 * [onRemoved] disposes the composition and tears down the lifecycle.
 */
class PersonaSpeakStripActionProvider(
    private val owners: ImeViewTreeOwners,
) : KeyboardViewContainerView.StripActionProvider {

    private var composeView: ComposeView? = null

    override fun inflateActionView(parent: ViewGroup): View {
        val view = ComposeView(parent.context).apply {
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
