package biz.pixelperfectstudios.personaspeak.ui.editor

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pure-Kotlin contract tests for [EditorPort] driven by a recording fake.
 *
 * The later ASK adapter tests prove that a stale result sends no editor
 * mutation command; these tests only pin the port surface and the snapshot
 * invariants. No Android, InputConnection, EditorInfo, ASK, persistence,
 * logging, or navigation types appear here.
 */
class EditorPortContractTest {

    // -----------------------------------------------------------------
    // CaptureResult refusals
    // -----------------------------------------------------------------

    @Test
    fun `captureSnapshot surfaces an empty-input refusal`() {
        val port = FakeEditorPort(capture = CaptureResult.EmptyInput)

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.EmptyInput, result)
        assertEquals(1, port.captureCalls)
    }

    @Test
    fun `captureSnapshot surfaces a sensitive-editor refusal`() {
        val port = FakeEditorPort(capture = CaptureResult.SensitiveEditor)

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.SensitiveEditor, result)
    }

    @Test
    fun `captureSnapshot surfaces an unsupported-editor refusal`() {
        val port = FakeEditorPort(capture = CaptureResult.UnsupportedEditor)

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.UnsupportedEditor, result)
    }

    @Test
    fun `captureSnapshot surfaces an incomplete-read refusal`() {
        val port = FakeEditorPort(capture = CaptureResult.IncompleteRead)

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.IncompleteRead, result)
    }

    @Test
    fun `captureSnapshot surfaces an oversized-input refusal`() {
        val port = FakeEditorPort(capture = CaptureResult.OversizedInput)

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.OversizedInput, result)
    }

    @Test
    fun `captureSnapshot returns a captured snapshot`() {
        val snapshot = aSnapshot(draft = "hello", selection = Utf16Selection(0, 5))
        val port = FakeEditorPort(capture = CaptureResult.Captured(snapshot))

        val result = runBlocking { port.captureSnapshot() }

        assertEquals(CaptureResult.Captured(snapshot), result)
    }

    // -----------------------------------------------------------------
    // ReplaceResult outcomes
    // -----------------------------------------------------------------

    @Test
    fun `attemptReplace returns AppliedVerified and records the call`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.AppliedVerified)

        val result = runBlocking { port.attemptReplace(snapshot, "world") }

        assertEquals(ReplaceResult.AppliedVerified, result)
        assertEquals(listOf(snapshot to "world"), port.replaceCalls)
    }

    @Test
    fun `attemptReplace returns a stale SessionChanged reason from exactly one replacement`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.Stale(StaleReason.SessionChanged))

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.Stale(StaleReason.SessionChanged), result)
        assertEquals(1, port.replaceCalls.size)
    }

    @Test
    fun `attemptReplace returns a stale GenerationChanged reason from exactly one replacement`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.Stale(StaleReason.GenerationChanged))

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.Stale(StaleReason.GenerationChanged), result)
        assertEquals(1, port.replaceCalls.size)
    }

    @Test
    fun `attemptReplace returns a stale TextChanged reason from exactly one replacement`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.Stale(StaleReason.TextChanged))

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.Stale(StaleReason.TextChanged), result)
        assertEquals(1, port.replaceCalls.size)
    }

    @Test
    fun `attemptReplace returns a stale SelectionChanged reason from exactly one replacement`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.Stale(StaleReason.SelectionChanged))

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.Stale(StaleReason.SelectionChanged), result)
        assertEquals(1, port.replaceCalls.size)
    }

    @Test
    fun `attemptReplace returns WriteRejected`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.WriteRejected)

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.WriteRejected, result)
    }

    @Test
    fun `attemptReplace returns WriteUnconfirmed`() {
        val snapshot = aSnapshot()
        val port = FakeEditorPort(replace = ReplaceResult.WriteUnconfirmed)

        val result = runBlocking { port.attemptReplace(snapshot, "x") }

        assertEquals(ReplaceResult.WriteUnconfirmed, result)
    }

    // -----------------------------------------------------------------
    // EditorSnapshot: 8,000 Unicode code-point boundary
    // -----------------------------------------------------------------

    @Test
    fun `EditorSnapshot accepts a draft of exactly 8000 code points`() {
        val draft = "a".repeat(8_000)

        val snapshot = aSnapshot(draft = draft, selection = Utf16Selection(0, 8_000))

        assertEquals(8_000, draft.codePointCount(0, draft.length))
        assertEquals(draft, snapshot.draft)
    }

    @Test
    fun `EditorSnapshot rejects a draft exceeding 8000 code points`() {
        val draft = "a".repeat(8_001)

        assertFailsWith<IllegalArgumentException> {
            aSnapshot(draft = draft, selection = Utf16Selection(0, 0))
        }
    }

    @Test
    fun `EditorSnapshot counts a supplementary character as one code point`() {
        // U+1F600 GRINNING FACE: two UTF-16 surrogate units, one code point.
        val supplementary = "\uD83D\uDE00"
        assertEquals(2, supplementary.length)
        assertEquals(1, supplementary.codePointCount(0, supplementary.length))

        // 7,999 ASCII + 1 supplementary = 8,000 code points, 8,001 UTF-16 units.
        val draft = "a".repeat(7_999) + supplementary

        val snapshot = aSnapshot(draft = draft, selection = Utf16Selection(0, draft.length))

        assertEquals(8_001, draft.length)
        assertEquals(8_000, draft.codePointCount(0, draft.length))
        assertEquals(draft, snapshot.draft)
    }

    @Test
    fun `EditorSnapshot rejects a draft whose supplementary character tips past 8000 code points`() {
        val supplementary = "\uD83D\uDE00"
        // 8,000 ASCII + 1 supplementary = 8,001 code points.
        val draft = "a".repeat(8_000) + supplementary

        assertFailsWith<IllegalArgumentException> {
            aSnapshot(draft = draft, selection = Utf16Selection(0, 0))
        }
    }

    // -----------------------------------------------------------------
    // Invalid UTF-16 selections
    // -----------------------------------------------------------------

    @Test
    fun `Utf16Selection rejects a negative start`() {
        assertFailsWith<IllegalArgumentException> {
            Utf16Selection(start = -1, end = 0)
        }
    }

    @Test
    fun `Utf16Selection rejects an end before the start`() {
        assertFailsWith<IllegalArgumentException> {
            Utf16Selection(start = 2, end = 1)
        }
    }

    // -----------------------------------------------------------------
    // Selection bounds
    // -----------------------------------------------------------------

    @Test
    fun `EditorSnapshot rejects a selection ending past the draft length`() {
        assertFailsWith<IllegalArgumentException> {
            aSnapshot(draft = "abc", selection = Utf16Selection(0, 4))
        }
    }

    @Test
    fun `EditorSnapshot accepts a selection ending exactly at the draft length`() {
        val snapshot = aSnapshot(draft = "abc", selection = Utf16Selection(1, 3))

        assertEquals(3, snapshot.selection.end)
    }

    @Test
    fun `EditorSnapshot accepts a collapsed caret selection inside the draft`() {
        val snapshot = aSnapshot(draft = "abc", selection = Utf16Selection(2, 2))

        assertEquals(2, snapshot.selection.start)
        assertEquals(2, snapshot.selection.end)
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun aSnapshot(
        draft: String = "draft",
        selection: Utf16Selection = Utf16Selection(0, draft.length),
    ): EditorSnapshot = EditorSnapshot(
        session = EditorSessionToken(1L),
        generation = RequestGeneration(1L),
        draft = draft,
        selection = selection,
    )

    private class FakeEditorPort(
        private val capture: CaptureResult = CaptureResult.EmptyInput,
        private val replace: ReplaceResult = ReplaceResult.AppliedVerified,
    ) : EditorPort {
        var captureCalls: Int = 0
            private set
        val replaceCalls: MutableList<Pair<EditorSnapshot, String>> = mutableListOf()

        override suspend fun captureSnapshot(): CaptureResult {
            captureCalls += 1
            return capture
        }

        override suspend fun attemptReplace(
            snapshot: EditorSnapshot,
            replacement: String,
        ): ReplaceResult {
            replaceCalls += snapshot to replacement
            return replace
        }
    }
}
