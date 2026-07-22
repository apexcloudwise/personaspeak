package biz.pixelperfectstudios.personaspeak.ui.editor

/**
 * Opaque handle for a single editor session.
 *
 * The value is opaque to the port's callers; the ASK adapter assigns and
 * compares it to detect that the user has navigated away from the editor a
 * snapshot was taken from. It carries no meaning and makes no atomicity
 * claim across processes.
 */
@JvmInline
value class EditorSessionToken(val value: Long)

/**
 * Opaque generation counter for a single capture/replace cycle against one
 * editor session. The adapter bumps it to invalidate earlier snapshots whose
 * replacement arrived too late.
 */
@JvmInline
value class RequestGeneration(val value: Long)

/**
 * UTF-16 code-unit selection range, half-open in the same units the platform
 * editor uses (`InputConnection.getSelectionStart` / `getSelectionEnd`).
 * UTF-16 — not code points — so a supplementary character occupies two units.
 */
data class Utf16Selection(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start)
    }
}

/**
 * A complete, bounded draft captured from the editor at one instant.
 *
 * - [draft] is capped at 8,000 Unicode code points (counted by code point,
 *   not UTF-16 unit, so supplementary characters count as one).
 * - [selection] is in UTF-16 units and must fall within [draft].
 *
 * The snapshot is a value: it carries no reference to the live editor and
 * cannot mutate it. Whether the adapter's read was atomic is the adapter's
 * concern, not the port's — this type makes no cross-process atomicity claim.
 */
data class EditorSnapshot(
    val session: EditorSessionToken,
    val generation: RequestGeneration,
    val draft: String,
    val selection: Utf16Selection,
) {
    init {
        require(draft.codePointCount(0, draft.length) <= 8_000)
        require(selection.end <= draft.length)
    }
}

/**
 * Outcome of capturing the editor. Exactly one [Captured] means a snapshot is
 * available; every other variant is a typed refusal explaining why no
 * snapshot could be taken.
 */
sealed interface CaptureResult {
    data class Captured(val snapshot: EditorSnapshot) : CaptureResult
    data object EmptyInput : CaptureResult
    data object SensitiveEditor : CaptureResult
    data object UnsupportedEditor : CaptureResult
    data object IncompleteRead : CaptureResult
    data object OversizedInput : CaptureResult
}

/**
 * Why an attempted replacement was rejected as stale. Returned by the adapter
 * when the editor moved on between capture and replace.
 */
sealed interface StaleReason {
    data object SessionChanged : StaleReason
    data object GenerationChanged : StaleReason
    data object TextChanged : StaleReason
    data object SelectionChanged : StaleReason
}

/**
 * Outcome of attempting to replace the snapshot's selection with [replacement].
 *
 * - [AppliedVerified]: the adapter confirms the write landed at the captured
 *   coordinates.
 * - [Stale]: the snapshot no longer describes the live editor.
 * - [WriteRejected]: the editor refused the write outright.
 * - [WriteUnconfirmed]: the write was issued but the adapter could not verify
 *   it landed. Honest about uncertainty; no silent success.
 *
 * A [Stale] or [WriteUnconfirmed] result is a port-level observation only.
 * Whether the adapter actually emitted an editor mutation command is proven
 * by the adapter's own tests, not by this contract.
 */
sealed interface ReplaceResult {
    data object AppliedVerified : ReplaceResult
    data class Stale(val reason: StaleReason) : ReplaceResult
    data object WriteRejected : ReplaceResult
    data object WriteUnconfirmed : ReplaceResult
}

/**
 * The accepted editor boundary: a pure Kotlin contract for reading a bounded
 * draft out of the editor and attempting a replacement.
 *
 * No `InputConnection`, `EditorInfo`, ASK, persistence, logging, or
 * navigation types cross this boundary. The later ASK adapter implements it
 * against the platform editor; everything upstream (prompt building,
 * persona selection) depends only on this interface.
 */
interface EditorPort {
    suspend fun captureSnapshot(): CaptureResult

    suspend fun attemptReplace(
        snapshot: EditorSnapshot,
        replacement: String,
    ): ReplaceResult
}
