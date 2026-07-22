package biz.pixelperfectstudios.personaspeak.ime.editor

import android.view.inputmethod.EditorInfo
import androidx.annotation.MainThread
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection

@MainThread
class EditorSessionState {
    var currentSessionToken: EditorSessionToken = EditorSessionToken(1L)
        private set

    var currentGeneration: RequestGeneration = RequestGeneration(0L)
        private set

    var currentSelection: Utf16Selection = Utf16Selection(0, 0)
        private set

    fun start(editorInfo: EditorInfo?) {
        currentSessionToken = EditorSessionToken(currentSessionToken.value + 1L)
        currentGeneration = RequestGeneration(0L)
        currentSelection = Utf16Selection(0, 0)
    }

    fun nextGeneration(): RequestGeneration {
        val next = RequestGeneration(currentGeneration.value + 1L)
        currentGeneration = next
        return next
    }

    fun selectionChanged(selStart: Int, selEnd: Int) {
        if (selStart >= 0 && selEnd >= selStart) {
            currentSelection = Utf16Selection(selStart, selEnd)
        }
    }

    fun selectionChanged(selection: Utf16Selection) {
        currentSelection = selection
    }

    fun finish() {
        currentSessionToken = EditorSessionToken(currentSessionToken.value + 1L)
        currentGeneration = RequestGeneration(0L)
        currentSelection = Utf16Selection(0, 0)
    }
}
