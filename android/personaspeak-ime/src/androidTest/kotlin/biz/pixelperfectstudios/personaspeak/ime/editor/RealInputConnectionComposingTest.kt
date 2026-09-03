package biz.pixelperfectstudios.personaspeak.ime.editor

import android.content.Intent
import android.text.InputType
import android.text.Spanned
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.ime.testing.ComposingTestActivity
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * ADR-0003's suite requirement: the capture -> transform -> replace path
 * gets a real-InputConnection test, not only unit tests against fakes.
 * The fork spike found every worker's pure-logic tests green while the
 * on-device runs failed: a draft ending in a live composing span survived
 * the replace because commitText prefers the composing region over the
 * selection. Fakes cannot see that; a real EditText with a real
 * composing span can.
 */
class RealInputConnectionComposingTest {

    private val candidate = "I have taken the liberty, sir, of rephrasing your words."

    private fun composingSpanCount(text: Spanned): Int =
        text.getSpans(0, text.length, Any::class.java).count {
            (text.getSpanFlags(it) and Spanned.SPAN_COMPOSING) != 0
        }

    /** One full replace pass on the main thread over a real editor whose
     *  trailing word sits in a live composing span. Returns the final
     *  buffer text, the composing-span count after the replace, and the
     *  port's verdict. */
    private fun replaceOverLiveComposingSpan(
        sdkIntSupplier: () -> Int,
    ): Triple<String, Int, ReplaceResult> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(), ComposingTestActivity::class.java)
        ActivityScenario.launch<ComposingTestActivity>(intent).use { scenario ->
            lateinit var outcome: Triple<String, Int, ReplaceResult>
            scenario.onActivity { activity ->
                val editor = activity.editor
                editor.inputType = InputType.TYPE_CLASS_TEXT
                val info = EditorInfo()
                val connection = editor.onCreateInputConnection(info)
                checkNotNull(connection) { "editor returned no InputConnection" }

                connection.commitText("Tea at ", 1)
                connection.setComposingText("six", 1)

                // The precondition the unit fakes cannot model: a live
                // composing span inside a real editor's buffer.
                assertEquals("Tea at six", editor.text.toString())
                assertTrue(composingSpanCount(editor.text) > 0, "expected a live composing span")

                val state = EditorSessionState()
                state.start(info)
                val port = InputConnectionEditorPort(
                    state,
                    connectionSupplier = { connection },
                    editorInfoSupplier = { info },
                    sdkIntSupplier = sdkIntSupplier,
                )
                outcome = runBlocking {
                    val snapshot = assertIs<CaptureResult.Captured>(
                        port.captureSnapshot()).snapshot
                    assertEquals("Tea at six", snapshot.draft)
                    val verdict = port.attemptReplace(snapshot, candidate)
                    Triple(
                        editor.text.toString(),
                        composingSpanCount(editor.text),
                        verdict,
                    )
                }
            }
            return outcome
        }
    }

    @Test
    fun replaceOverLiveComposingSpanReplacesWholeDraft_Api34Path() {
        val (finalText, spanCount, verdict) = replaceOverLiveComposingSpan(sdkIntSupplier = { 34 })
        assertIs<ReplaceResult.AppliedVerified>(verdict)
        assertEquals(candidate, finalText)
        assertEquals(0, spanCount, "a composing span survived the replace")
    }

    @Test
    fun replaceOverLiveComposingSpanReplacesWholeDraft_LegacyPath() {
        val (finalText, spanCount, verdict) = replaceOverLiveComposingSpan(sdkIntSupplier = { 33 })
        assertIs<ReplaceResult.AppliedVerified>(verdict)
        assertEquals(candidate, finalText)
        assertEquals(0, spanCount, "a composing span survived the replace")
    }
}
