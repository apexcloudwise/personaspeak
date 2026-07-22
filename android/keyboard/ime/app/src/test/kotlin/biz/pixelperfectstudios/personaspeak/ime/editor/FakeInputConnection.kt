package biz.pixelperfectstudios.personaspeak.ime.editor

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute

class FakeInputConnection(
    var text: String = "Hello world",
    var selectionStart: Int = 0,
    var selectionEnd: Int = text.length,
    var startOffset: Int = 0,
    var partialStartOffset: Int = -1,
    var partialEndOffset: Int = -1,
    var returnNullExtractedText: Boolean = false,
    var returnNullTextInExtractedText: Boolean = false,
    var replaceTextResult: Boolean = true,
    var commitTextResult: Boolean = true,
    var finishComposingTextResult: Boolean = true,
    var setSelectionResult: Boolean = true,
    var returnNullPostWriteText: Boolean = false,
    var postWriteMismatchedText: String? = null,
) : InputConnectionWrapper(null, true) {

    val calls = mutableListOf<String>()
    var replaceTextCalls = 0
        private set
    var finishComposingTextCalls = 0
        private set
    var setSelectionCalls = 0
        private set
    var commitTextCalls = 0
        private set
    var deleteSurroundingTextCalls = 0
        private set
    var deleteSurroundingTextInCodePointsCalls = 0
        private set

    private var inPostWriteMode = false

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
        calls.add("getExtractedText")
        if (returnNullExtractedText) return null
        if (inPostWriteMode && returnNullPostWriteText) return null

        val et = ExtractedText()
        val currentText = if (inPostWriteMode && postWriteMismatchedText != null) {
            postWriteMismatchedText
        } else if (returnNullTextInExtractedText) {
            null
        } else {
            text
        }

        et.text = currentText
        et.startOffset = startOffset
        et.partialStartOffset = partialStartOffset
        et.partialEndOffset = partialEndOffset
        et.selectionStart = selectionStart
        et.selectionEnd = selectionEnd
        return et
    }

    override fun replaceText(
        start: Int,
        end: Int,
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        calls.add("replaceText($start, $end, $text)")
        replaceTextCalls++
        if (!replaceTextResult) return false
        this.text = text.toString()
        this.selectionStart = this.text.length
        this.selectionEnd = this.text.length
        inPostWriteMode = true
        return true
    }

    override fun finishComposingText(): Boolean {
        calls.add("finishComposingText")
        finishComposingTextCalls++
        return finishComposingTextResult
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        calls.add("setSelection($start, $end)")
        setSelectionCalls++
        if (!setSelectionResult) return false
        this.selectionStart = start
        this.selectionEnd = end
        return true
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        calls.add("commitText($text)")
        commitTextCalls++
        if (!commitTextResult) return false
        val prefix = if (selectionStart in 0..this.text.length) this.text.substring(0, selectionStart) else ""
        val suffix = if (selectionEnd in 0..this.text.length) this.text.substring(selectionEnd) else ""
        this.text = prefix + text.toString() + suffix
        this.selectionStart = (prefix + text.toString()).length
        this.selectionEnd = this.selectionStart
        inPostWriteMode = true
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        calls.add("deleteSurroundingText($beforeLength, $afterLength)")
        deleteSurroundingTextCalls++
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        calls.add("deleteSurroundingTextInCodePoints($beforeLength, $afterLength)")
        deleteSurroundingTextInCodePointsCalls++
        return true
    }
}
