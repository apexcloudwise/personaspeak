package biz.pixelperfectstudios.personaspeak.ime.editor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputConnectionEditorPortTest {

    private val sessionState = EditorSessionState()

    private fun createEditorInfo(
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
        imeOptions: Int = 0,
    ): EditorInfo = EditorInfo().apply {
        this.inputType = inputType
        this.imeOptions = imeOptions
    }

    private fun assertZeroMutations(connection: FakeInputConnection) {
        assertEquals(0, connection.replaceTextCalls)
        assertEquals(0, connection.commitTextCalls)
        assertEquals(0, connection.finishComposingTextCalls)
        assertEquals(0, connection.setSelectionCalls)
        assertEquals(0, connection.deleteSurroundingTextCalls)
        assertEquals(0, connection.deleteSurroundingTextInCodePointsCalls)
    }

    @Test
    fun `captureSnapshot returns Captured for valid complete input`() {
        val fakeConnection = FakeInputConnection(text = "Hello world", selectionStart = 0, selectionEnd = 11)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertTrue(result is CaptureResult.Captured)
        val snapshot = (result as CaptureResult.Captured).snapshot
        assertEquals("Hello world", snapshot.draft)
        assertEquals(Utf16Selection(0, 11), snapshot.selection)
        assertEquals(EditorSessionToken(2L), snapshot.session)
        assertEquals(RequestGeneration(1L), snapshot.generation)
        assertEquals(Utf16Selection(0, 11), sessionState.currentSelection)
    }

    @Test
    fun `selectionChanged updates currentSelection in EditorSessionState`() {
        val editorInfo = createEditorInfo()
        sessionState.start(editorInfo)
        assertEquals(Utf16Selection(0, 0), sessionState.currentSelection)

        sessionState.selectionChanged(2, 8)
        assertEquals(Utf16Selection(2, 8), sessionState.currentSelection)

        sessionState.selectionChanged(Utf16Selection(5, 10))
        assertEquals(Utf16Selection(5, 10), sessionState.currentSelection)
    }

    @Test
    fun `selection change after capture does not mutate issued snapshot`() {
        val fakeConnection = FakeInputConnection(text = "Hello world", selectionStart = 0, selectionEnd = 11)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }
        assertTrue(result is CaptureResult.Captured)
        val snapshot = (result as CaptureResult.Captured).snapshot

        // Selection change occurs after capture
        sessionState.selectionChanged(2, 5)

        // Session state selection updated, but issued snapshot is immutable
        assertEquals(Utf16Selection(2, 5), sessionState.currentSelection)
        assertEquals(Utf16Selection(0, 11), snapshot.selection)
    }

    @Test
    fun `replacement staleness decision comes from live connection reread not session state`() {
        val fakeConnection = FakeInputConnection(text = "Hello world", selectionStart = 0, selectionEnd = 11)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }
        assertTrue(result is CaptureResult.Captured)
        val snapshot = (result as CaptureResult.Captured).snapshot

        // sessionState selection updated independently
        sessionState.selectionChanged(3, 7)

        // Live connection still has selection 0..11 matching snapshot -> replacement succeeds
        val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replacement") }
        assertEquals(ReplaceResult.AppliedVerified, replaceResult)

        // Now update live connection selection to 3..7 -> replacement becomes stale
        val fakeConnStale = FakeInputConnection(text = "Hello world", selectionStart = 3, selectionEnd = 7)
        val portStale = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnStale },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )
        val staleResult = runBlocking { portStale.attemptReplace(snapshot, "Replacement") }
        assertEquals(ReplaceResult.Stale(StaleReason.SelectionChanged), staleResult)
        assertZeroMutations(fakeConnStale)
    }

    @Test
    fun `captureSnapshot returns EmptyInput for empty text`() {
        val fakeConnection = FakeInputConnection(text = "", selectionStart = 0, selectionEnd = 0)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.EmptyInput, result)
    }

    @Test
    fun `captureSnapshot returns SensitiveEditor for text password variation`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot returns SensitiveEditor for visible password variation`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot returns SensitiveEditor for web password variation`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot returns SensitiveEditor for number password variation`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot returns SensitiveEditor for IME_FLAG_NO_PERSONALIZED_LEARNING`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot returns UnsupportedEditor for non-text class`() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = createEditorInfo(inputType = InputType.TYPE_CLASS_NUMBER)
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.UnsupportedEditor, result)
    }

    @Test
    fun `captureSnapshot returns UnsupportedEditor for null connection or editorInfo`() {
        val portNullConn = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { null },
            editorInfoSupplier = { createEditorInfo() },
        )
        assertEquals(CaptureResult.UnsupportedEditor, runBlocking { portNullConn.captureSnapshot() })

        val portNullInfo = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { FakeInputConnection() },
            editorInfoSupplier = { null },
        )
        assertEquals(CaptureResult.UnsupportedEditor, runBlocking { portNullInfo.captureSnapshot() })
    }

    @Test
    fun `captureSnapshot returns IncompleteRead for non-zero startOffset`() {
        val fakeConnection = FakeInputConnection(startOffset = 5)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.IncompleteRead, result)
    }

    @Test
    fun `captureSnapshot returns IncompleteRead for partial extracted text`() {
        val fakeConnection = FakeInputConnection(partialStartOffset = 0)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.IncompleteRead, result)
    }

    @Test
    fun `captureSnapshot returns IncompleteRead for null ExtractedText or text`() {
        val fakeConnection = FakeInputConnection(returnNullExtractedText = true)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        assertEquals(CaptureResult.IncompleteRead, runBlocking { port.captureSnapshot() })

        val fakeConnectionNullText = FakeInputConnection(returnNullTextInExtractedText = true)
        val portNullText = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnectionNullText },
            editorInfoSupplier = { editorInfo },
        )
        assertEquals(CaptureResult.IncompleteRead, runBlocking { portNullText.captureSnapshot() })
    }

    @Test
    fun `captureSnapshot returns IncompleteRead for invalid selection`() {
        val fakeConnection = FakeInputConnection(text = "abc", selectionStart = -1, selectionEnd = 2)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        assertEquals(CaptureResult.IncompleteRead, runBlocking { port.captureSnapshot() })
    }

    @Test
    fun `captureSnapshot accepts exactly 8000 ASCII code points`() {
        val text = "a".repeat(8_000)
        val fakeConnection = FakeInputConnection(text = text, selectionStart = 0, selectionEnd = text.length)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertTrue(result is CaptureResult.Captured)
        val snapshot = (result as CaptureResult.Captured).snapshot
        assertEquals(8_000, snapshot.draft.codePointCount(0, snapshot.draft.length))
        assertEquals(8_000, snapshot.draft.length)
    }

    @Test
    fun `captureSnapshot accepts exactly 8000 supplementary code points (16000 UTF-16 units)`() {
        val supplementary = "\uD83D\uDE00" // U+1F600 GRINNING FACE (2 UTF-16 units, 1 code point)
        val text = supplementary.repeat(8_000) // 16,000 UTF-16 units, 8,000 code points
        val fakeConnection = FakeInputConnection(text = text, selectionStart = 0, selectionEnd = text.length)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertTrue(result is CaptureResult.Captured)
        val snapshot = (result as CaptureResult.Captured).snapshot
        assertEquals(16_000, snapshot.draft.length)
        assertEquals(8_000, snapshot.draft.codePointCount(0, snapshot.draft.length))
    }

    @Test
    fun `captureSnapshot returns OversizedInput for 8001 ASCII code points`() {
        val text = "a".repeat(8_001)
        val fakeConnection = FakeInputConnection(text = text, selectionStart = 0, selectionEnd = text.length)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.OversizedInput, result)
    }

    @Test
    fun `captureSnapshot returns OversizedInput for 8001 supplementary code points (16002 UTF-16 units)`() {
        val supplementary = "\uD83D\uDE00"
        val text = supplementary.repeat(8_001) // 16,002 UTF-16 units, 8,001 code points
        val fakeConnection = FakeInputConnection(text = text, selectionStart = 0, selectionEnd = text.length)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
        )

        sessionState.start(editorInfo)
        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.OversizedInput, result)
    }

    @Test
    fun `attemptReplace on API 34 uses replaceText and returns AppliedVerified`() {
        val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val captured = runBlocking { port.captureSnapshot() }
        assertTrue(captured is CaptureResult.Captured)
        val snapshot = (captured as CaptureResult.Captured).snapshot

        val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replaced text") }

        assertEquals(ReplaceResult.AppliedVerified, replaceResult)
        assertEquals(1, fakeConnection.replaceTextCalls)
        assertEquals(0, fakeConnection.commitTextCalls)
        assertEquals(0, fakeConnection.deleteSurroundingTextCalls)
        assertEquals(0, fakeConnection.deleteSurroundingTextInCodePointsCalls)
        assertEquals("Replaced text", fakeConnection.text)
    }

    @Test
    fun `attemptReplace on API under 34 uses finishComposingText setSelection commitText`() {
        val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
        val editorInfo = createEditorInfo()
        val port = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 33 },
        )

        sessionState.start(editorInfo)
        val captured = runBlocking { port.captureSnapshot() }
        assertTrue(captured is CaptureResult.Captured)
        val snapshot = (captured as CaptureResult.Captured).snapshot

        val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replaced text") }

        assertEquals(ReplaceResult.AppliedVerified, replaceResult)
        assertEquals(0, fakeConnection.replaceTextCalls)
        assertEquals(1, fakeConnection.finishComposingTextCalls)
        assertEquals(1, fakeConnection.setSelectionCalls)
        assertEquals(1, fakeConnection.commitTextCalls)
        assertEquals(0, fakeConnection.deleteSurroundingTextCalls)
        assertEquals(0, fakeConnection.deleteSurroundingTextInCodePointsCalls)
        assertEquals("Replaced text", fakeConnection.text)
    }

    @Test
    fun `attemptReplace returns Stale SessionChanged and emits zero mutations on both API 34 and API 33`() {
        for (sdk in listOf(34, 33)) {
            val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
            val editorInfo = createEditorInfo()
            val port = InputConnectionEditorPort(
                sessionState = sessionState,
                connectionSupplier = { fakeConnection },
                editorInfoSupplier = { editorInfo },
                sdkIntSupplier = { sdk },
            )

            sessionState.start(editorInfo)
            val captured = runBlocking { port.captureSnapshot() }
            assertTrue(captured is CaptureResult.Captured)
            val snapshot = (captured as CaptureResult.Captured).snapshot

            sessionState.finish()

            val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replaced text") }

            assertEquals(ReplaceResult.Stale(StaleReason.SessionChanged), replaceResult)
            assertZeroMutations(fakeConnection)
        }
    }

    @Test
    fun `attemptReplace returns Stale GenerationChanged and emits zero mutations on both API 34 and API 33`() {
        for (sdk in listOf(34, 33)) {
            val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
            val editorInfo = createEditorInfo()
            val port = InputConnectionEditorPort(
                sessionState = sessionState,
                connectionSupplier = { fakeConnection },
                editorInfoSupplier = { editorInfo },
                sdkIntSupplier = { sdk },
            )

            sessionState.start(editorInfo)
            val firstCaptured = runBlocking { port.captureSnapshot() }
            assertTrue(firstCaptured is CaptureResult.Captured)
            val firstSnapshot = (firstCaptured as CaptureResult.Captured).snapshot

            // Second capture bumps generation
            val secondCaptured = runBlocking { port.captureSnapshot() }
            assertTrue(secondCaptured is CaptureResult.Captured)

            val replaceResult = runBlocking { port.attemptReplace(firstSnapshot, "Replaced text") }

            assertEquals(ReplaceResult.Stale(StaleReason.GenerationChanged), replaceResult)
            assertZeroMutations(fakeConnection)
        }
    }

    @Test
    fun `attemptReplace returns Stale TextChanged and emits zero mutations on both API 34 and API 33`() {
        for (sdk in listOf(34, 33)) {
            val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
            val editorInfo = createEditorInfo()
            val port = InputConnectionEditorPort(
                sessionState = sessionState,
                connectionSupplier = { fakeConnection },
                editorInfoSupplier = { editorInfo },
                sdkIntSupplier = { sdk },
            )

            sessionState.start(editorInfo)
            val captured = runBlocking { port.captureSnapshot() }
            assertTrue(captured is CaptureResult.Captured)
            val snapshot = (captured as CaptureResult.Captured).snapshot

            // Text changed externally
            fakeConnection.text = "Modified text externally"

            val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replaced text") }

            assertEquals(ReplaceResult.Stale(StaleReason.TextChanged), replaceResult)
            assertZeroMutations(fakeConnection)
        }
    }

    @Test
    fun `attemptReplace returns Stale SelectionChanged and emits zero mutations on both API 34 and API 33`() {
        for (sdk in listOf(34, 33)) {
            val fakeConnection = FakeInputConnection(text = "Original text", selectionStart = 0, selectionEnd = 13)
            val editorInfo = createEditorInfo()
            val port = InputConnectionEditorPort(
                sessionState = sessionState,
                connectionSupplier = { fakeConnection },
                editorInfoSupplier = { editorInfo },
                sdkIntSupplier = { sdk },
            )

            sessionState.start(editorInfo)
            val captured = runBlocking { port.captureSnapshot() }
            assertTrue(captured is CaptureResult.Captured)
            val snapshot = (captured as CaptureResult.Captured).snapshot

            // Selection changed externally
            fakeConnection.selectionStart = 2
            fakeConnection.selectionEnd = 5

            val replaceResult = runBlocking { port.attemptReplace(snapshot, "Replaced text") }

            assertEquals(ReplaceResult.Stale(StaleReason.SelectionChanged), replaceResult)
            assertZeroMutations(fakeConnection)
        }
    }

    @Test
    fun `attemptReplace returns WriteRejected when mutation fails`() {
        val fakeConnectionApi34 = FakeInputConnection(replaceTextResult = false)
        val editorInfo = createEditorInfo()
        val port34 = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnectionApi34 },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val captured34 = runBlocking { port34.captureSnapshot() }
        assertTrue(captured34 is CaptureResult.Captured)
        val snapshot34 = (captured34 as CaptureResult.Captured).snapshot
        assertEquals(ReplaceResult.WriteRejected, runBlocking { port34.attemptReplace(snapshot34, "x") })

        val fakeConnectionApi33 = FakeInputConnection(commitTextResult = false)
        val port33 = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnectionApi33 },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 33 },
        )

        sessionState.start(editorInfo)
        val captured33 = runBlocking { port33.captureSnapshot() }
        assertTrue(captured33 is CaptureResult.Captured)
        val snapshot33 = (captured33 as CaptureResult.Captured).snapshot
        assertEquals(ReplaceResult.WriteRejected, runBlocking { port33.attemptReplace(snapshot33, "x") })
    }

    @Test
    fun `attemptReplace returns WriteUnconfirmed when post-write reread is null or mismatching`() {
        val fakeConnectionNullPost = FakeInputConnection(returnNullPostWriteText = true)
        val editorInfo = createEditorInfo()
        val portNullPost = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnectionNullPost },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val capturedNullPost = runBlocking { portNullPost.captureSnapshot() }
        assertTrue(capturedNullPost is CaptureResult.Captured)
        val snapshotNullPost = (capturedNullPost as CaptureResult.Captured).snapshot
        assertEquals(ReplaceResult.WriteUnconfirmed, runBlocking { portNullPost.attemptReplace(snapshotNullPost, "x") })

        val fakeConnectionMismatch = FakeInputConnection(postWriteMismatchedText = "Unexpected text")
        val portMismatch = InputConnectionEditorPort(
            sessionState = sessionState,
            connectionSupplier = { fakeConnectionMismatch },
            editorInfoSupplier = { editorInfo },
            sdkIntSupplier = { 34 },
        )

        sessionState.start(editorInfo)
        val capturedMismatch = runBlocking { portMismatch.captureSnapshot() }
        assertTrue(capturedMismatch is CaptureResult.Captured)
        val snapshotMismatch = (capturedMismatch as CaptureResult.Captured).snapshot
        assertEquals(ReplaceResult.WriteUnconfirmed, runBlocking { portMismatch.attemptReplace(snapshotMismatch, "x") })
    }

    @Test
    fun `connection supplier called fresh on each operation for both API 34 and API 33`() {
        for (sdk in listOf(34, 33)) {
            val connA = FakeInputConnection(text = "Hello", selectionStart = 0, selectionEnd = 5)
            val connB = FakeInputConnection(text = "Hello", selectionStart = 0, selectionEnd = 5)
            var supplierCallCount = 0
            val editorInfo = createEditorInfo()

            val port = InputConnectionEditorPort(
                sessionState = sessionState,
                connectionSupplier = {
                    supplierCallCount++
                    if (supplierCallCount == 1) connA else connB
                },
                editorInfoSupplier = { editorInfo },
                sdkIntSupplier = { sdk },
            )

            sessionState.start(editorInfo)
            val captured = runBlocking { port.captureSnapshot() }
            assertTrue(captured is CaptureResult.Captured)
            val snapshot = (captured as CaptureResult.Captured).snapshot

            val result = runBlocking { port.attemptReplace(snapshot, "World") }

            assertEquals(ReplaceResult.AppliedVerified, result)
            assertZeroMutations(connA)
            if (sdk >= 34) {
                assertEquals(1, connB.replaceTextCalls)
            } else {
                assertEquals(1, connB.commitTextCalls)
            }
        }
    }
}
