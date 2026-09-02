package biz.pixelperfectstudios.personaspeak.ime.editor

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.InsertResult
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection

class InputConnectionEditorPort(
    private val sessionState: EditorSessionState,
    private val connectionSupplier: () -> InputConnection?,
    private val editorInfoSupplier: () -> EditorInfo?,
    private val sdkIntSupplier: () -> Int = { Build.VERSION.SDK_INT },
) : EditorPort {

    override suspend fun captureSnapshot(): CaptureResult {
        val connection = connectionSupplier() ?: return CaptureResult.UnsupportedEditor
        val editorInfo = editorInfoSupplier() ?: return CaptureResult.UnsupportedEditor

        val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION

        val isTextPassword = inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumberPassword = inputClass == InputType.TYPE_CLASS_NUMBER && (
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        val noPersonalizedLearning = (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        if (isTextPassword || isNumberPassword || noPersonalizedLearning) {
            return CaptureResult.SensitiveEditor
        }

        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            return CaptureResult.UnsupportedEditor
        }

        val extractedText = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return CaptureResult.IncompleteRead

        val textSequence = extractedText.text ?: return CaptureResult.IncompleteRead

        if (extractedText.startOffset != 0 || extractedText.partialStartOffset >= 0 || extractedText.partialEndOffset >= 0) {
            return CaptureResult.IncompleteRead
        }

        val draft = textSequence.toString()
        if (draft.isEmpty()) {
            return CaptureResult.EmptyInput
        }

        val codePoints = draft.codePointCount(0, draft.length)
        if (codePoints > 8_000) {
            return CaptureResult.OversizedInput
        }

        val selStart = extractedText.selectionStart
        val selEnd = extractedText.selectionEnd
        if (selStart < 0 || selEnd < selStart || selEnd > draft.length) {
            return CaptureResult.IncompleteRead
        }

        val selection = Utf16Selection(selStart, selEnd)
        sessionState.selectionChanged(selection)
        val generation = sessionState.nextGeneration()
        val session = sessionState.currentSessionToken

        val snapshot = EditorSnapshot(
            session = session,
            generation = generation,
            draft = draft,
            selection = selection,
        )
        return CaptureResult.Captured(snapshot)
    }

    override suspend fun attemptReplace(
        snapshot: EditorSnapshot,
        replacement: String,
    ): ReplaceResult {
        if (snapshot.session != sessionState.currentSessionToken) {
            return ReplaceResult.Stale(StaleReason.SessionChanged)
        }
        if (snapshot.generation != sessionState.currentGeneration) {
            return ReplaceResult.Stale(StaleReason.GenerationChanged)
        }

        val connection = connectionSupplier() ?: return ReplaceResult.Stale(StaleReason.TextChanged)
        val editorInfo = editorInfoSupplier() ?: return ReplaceResult.Stale(StaleReason.TextChanged)

        val extractedText = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return ReplaceResult.Stale(StaleReason.TextChanged)

        val textSequence = extractedText.text ?: return ReplaceResult.Stale(StaleReason.TextChanged)

        if (extractedText.startOffset != 0 || extractedText.partialStartOffset >= 0 || extractedText.partialEndOffset >= 0) {
            return ReplaceResult.Stale(StaleReason.TextChanged)
        }

        val currentText = textSequence.toString()
        if (currentText != snapshot.draft) {
            return ReplaceResult.Stale(StaleReason.TextChanged)
        }

        val currentSelStart = extractedText.selectionStart
        val currentSelEnd = extractedText.selectionEnd
        if (currentSelStart < 0 || currentSelEnd < currentSelStart || currentSelEnd > currentText.length) {
            return ReplaceResult.Stale(StaleReason.SelectionChanged)
        }

        val currentSelection = Utf16Selection(currentSelStart, currentSelEnd)
        if (currentSelection != snapshot.selection) {
            return ReplaceResult.Stale(StaleReason.SelectionChanged)
        }

        val oldLength = snapshot.draft.length
        val sdkInt = sdkIntSupplier()
        val success: Boolean

        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            success = connection.replaceText(0, oldLength, replacement, 1, null)
        } else {
            connection.finishComposingText()
            connection.setSelection(0, oldLength)
            success = connection.commitText(replacement, 1)
        }

        if (!success) {
            return ReplaceResult.WriteRejected
        }

        val postExtracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return ReplaceResult.WriteUnconfirmed
        val postText = postExtracted.text?.toString() ?: return ReplaceResult.WriteUnconfirmed

        return if (postText == replacement) {
            ReplaceResult.AppliedVerified
        } else {
            ReplaceResult.WriteUnconfirmed
        }
    }

    override suspend fun insertDraft(text: String): InsertResult {
        // Contract (ADR-0011 §5): the caller just observed EmptyInput. The
        // read here re-verifies that immediately before the one mutation.
        val connection = connectionSupplier() ?: return InsertResult.WriteRejected
        val editorInfo = editorInfoSupplier() ?: return InsertResult.WriteRejected

        val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        val isSensitive = (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) ||
            (inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (isSensitive) {
            // A persona draft never lands in a credential field.
            return InsertResult.WriteRejected
        }

        val extractedText = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return InsertResult.WriteRejected
        val currentText = extractedText.text?.toString() ?: return InsertResult.WriteRejected
        if (currentText.isNotEmpty()) {
            // The editor filled up between capture and insert — no blind
            // mutation; the caller re-routes through the guarded replace path.
            return InsertResult.WriteRejected
        }

        val success: Boolean
        if (sdkIntSupplier() >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            success = connection.replaceText(0, 0, text, 1, null)
        } else {
            connection.finishComposingText()
            success = connection.commitText(text, 1)
        }
        if (!success) {
            return InsertResult.WriteRejected
        }

        val postExtracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: return InsertResult.WriteUnconfirmed
        val postText = postExtracted.text?.toString() ?: return InsertResult.WriteUnconfirmed

        return if (postText == text) {
            InsertResult.AppliedVerified
        } else {
            InsertResult.WriteUnconfirmed
        }
    }
}
