package biz.pixelperfectstudios.personaspeak.ui.rewrite

import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.PromptBuilder
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-Kotlin contract tests for [RewriteCoordinator] driven by recording fakes.
 *
 * Pins the two-stage request/apply split: a provider result appears before any
 * editor mutation is attempted, every typed capture refusal short-circuits
 * before the provider is invoked, provider failure never leaks the raw body or
 * exception, blank responses collapse to MalformedResponse, cancellation
 * propagates as control flow rather than a ProviderFailure, and apply never
 * retries. No Android, ASK, persistence, logging, analytics, or navigation
 * types appear here.
 */
class RewriteCoordinatorTest {

    // -----------------------------------------------------------------
    // Capture refusals: provider never invoked
    // -----------------------------------------------------------------

    @Test
    fun `request surfaces an empty-input refusal and never calls the provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.EmptyInput)
        val provider = FakeCompletionProvider()

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.EmptyInput, result)
        assertEquals(1, editor.captureCalls)
        assertTrue(provider.calls.isEmpty())
        assertTrue(editor.replaceCalls.isEmpty())
    }

    @Test
    fun `request surfaces a sensitive-editor refusal and never calls the provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.SensitiveEditor)
        val provider = FakeCompletionProvider()

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.SensitiveEditor, result)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `request surfaces an unsupported-editor refusal and never calls the provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.UnsupportedEditor)
        val provider = FakeCompletionProvider()

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.UnsupportedEditor, result)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `request surfaces an incomplete-read refusal and never calls the provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.IncompleteRead)
        val provider = FakeCompletionProvider()

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.IncompleteRead, result)
        assertTrue(provider.calls.isEmpty())
    }

    @Test
    fun `request surfaces an oversized-input refusal and never calls the provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.OversizedInput)
        val provider = FakeCompletionProvider()

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.OversizedInput, result)
        assertTrue(provider.calls.isEmpty())
    }

    // -----------------------------------------------------------------
    // Persona resolution
    // -----------------------------------------------------------------

    @Test
    fun `request returns NoPersona when the persona is unknown and never touches the editor or provider`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider()
        val personas = FakePersonaRepository(persona = null)

        val result = runBlocking { coordinator(personas, editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.NoPersona, result)
        assertEquals(1, personas.loadCalls)
        assertEquals(0, editor.captureCalls)
        assertTrue(provider.calls.isEmpty())
    }

    // -----------------------------------------------------------------
    // Successful request
    // -----------------------------------------------------------------

    @Test
    fun `request builds the prompt, calls the provider once, and returns a candidate with the snapshot and replacement`() {
        val snapshot = aSnapshot(draft = "the original draft")
        val editor = FakeEditorPort(capture = CaptureResult.Captured(snapshot))
        val provider = FakeCompletionProvider(result = Result.success("the polished rewrite"))

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        val candidate = (result as RewriteRequestResult.Ready).candidate
        assertEquals(snapshot, candidate.snapshot)
        assertEquals("the polished rewrite", candidate.replacement)
        assertEquals(1, provider.calls.size)
        assertEquals(PromptBuilder.build(TEST_PERSONA.content), provider.calls.single().first)
        assertEquals("the original draft", provider.calls.single().second)
    }

    // -----------------------------------------------------------------
    // Malformed responses
    // -----------------------------------------------------------------

    @Test
    fun `request returns MalformedResponse for a blank provider response and never produces a candidate`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(result = Result.success(""))

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.MalformedResponse, result)
        assertEquals(1, provider.calls.size)
        assertFalse(result is RewriteRequestResult.Ready)
    }

    @Test
    fun `request returns MalformedResponse for a whitespace-only provider response and never produces a candidate`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(result = Result.success("   \n\t "))

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.MalformedResponse, result)
        assertFalse(result is RewriteRequestResult.Ready)
    }

    // -----------------------------------------------------------------
    // Provider failure: no raw leakage
    // -----------------------------------------------------------------

    @Test
    fun `request returns ProviderFailure when the provider returns a failure result without leaking the raw message`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(
            result = Result.failure(RuntimeException("a secret api key that must not leak")),
        )

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.ProviderFailure, result)
        assertEquals(1, provider.calls.size)
        assertFalse(result.toString().contains("secret"), "raw exception message must not leak into the result")
    }

    @Test
    fun `request returns ProviderFailure when the provider throws without leaking the raw message`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(
            throwOnCall = RuntimeException("a secret stack trace that must not leak"),
        )

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertEquals(RewriteRequestResult.ProviderFailure, result)
        assertEquals(1, provider.calls.size)
        assertFalse(result.toString().contains("secret"), "raw exception message must not leak into the result")
    }

    // -----------------------------------------------------------------
    // Request never applies
    // -----------------------------------------------------------------

    @Test
    fun `request never calls attemptReplace even on a successful candidate`() {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(result = Result.success("rewritten"))

        val result = runBlocking { coordinator(editor, provider).request(PERSONA_ID) }

        assertTrue(result is RewriteRequestResult.Ready)
        assertTrue(editor.replaceCalls.isEmpty(), "request must not touch attemptReplace")
    }

    // -----------------------------------------------------------------
    // Cancellation propagation
    // -----------------------------------------------------------------

    @Test
    fun `request rethrows a CancellationException delivered via Result failure rather than collapsing it to ProviderFailure`() = runBlocking {
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = FakeCompletionProvider(
            result = Result.failure(CancellationException("delivered as a failure result")),
        )
        val coordinator = coordinator(editor, provider)

        val job = launch { coordinator.request(PERSONA_ID) }
        job.join()

        assertTrue(job.isCancelled, "CancellationException in Result.failure must propagate, not become ProviderFailure")
        assertEquals(1, provider.calls.size)
        assertTrue(editor.replaceCalls.isEmpty(), "cancellation-as-failure must not reach attemptReplace")
        Unit
    }

    @Test
    fun `cancellation during the provider rewrite propagates without ProviderFailure or attemptReplace`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val editor = FakeEditorPort(capture = CaptureResult.Captured(aSnapshot()))
        val provider = CancellingCompletionProvider(entered, exited)
        val coordinator = coordinator(editor, provider)

        val job = launch { coordinator.request(PERSONA_ID) }

        entered.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled, "cancellation must propagate as CancellationException, not ProviderFailure")
        assertTrue(exited.isCompleted, "provider finally must run on cancellation")
        assertEquals(1, provider.calls.size)
        assertTrue(editor.replaceCalls.isEmpty(), "cancellation must not reach attemptReplace")
        Unit
    }

    // -----------------------------------------------------------------
    // Apply: all four ReplaceResult variants, exactly one attempt each
    // -----------------------------------------------------------------

    @Test
    fun `apply maps AppliedVerified from exactly one replacement attempt`() {
        val editor = FakeEditorPort(replace = ReplaceResult.AppliedVerified)
        val candidate = RewriteCandidate(aSnapshot(), "rewritten")

        val result = runBlocking { coordinator(editor = editor).apply(candidate) }

        assertEquals(ApplyResult.AppliedVerified, result)
        assertEquals(1, editor.replaceCalls.size)
        assertEquals(candidate.snapshot to candidate.replacement, editor.replaceCalls.single())
    }

    @Test
    fun `apply maps a stale SessionChanged reason from exactly one replacement attempt`() =
        assertStaleMapping(StaleReason.SessionChanged)

    @Test
    fun `apply maps a stale GenerationChanged reason from exactly one replacement attempt`() =
        assertStaleMapping(StaleReason.GenerationChanged)

    @Test
    fun `apply maps a stale TextChanged reason from exactly one replacement attempt`() =
        assertStaleMapping(StaleReason.TextChanged)

    @Test
    fun `apply maps a stale SelectionChanged reason from exactly one replacement attempt`() =
        assertStaleMapping(StaleReason.SelectionChanged)

    @Test
    fun `apply maps WriteRejected from exactly one replacement attempt`() {
        val editor = FakeEditorPort(replace = ReplaceResult.WriteRejected)
        val candidate = RewriteCandidate(aSnapshot(), "rewritten")

        val result = runBlocking { coordinator(editor = editor).apply(candidate) }

        assertEquals(ApplyResult.WriteRejected, result)
        assertEquals(1, editor.replaceCalls.size)
    }

    @Test
    fun `apply maps WriteUnconfirmed from exactly one replacement attempt`() {
        val editor = FakeEditorPort(replace = ReplaceResult.WriteUnconfirmed)
        val candidate = RewriteCandidate(aSnapshot(), "rewritten")

        val result = runBlocking { coordinator(editor = editor).apply(candidate) }

        assertEquals(ApplyResult.WriteUnconfirmed, result)
        assertEquals(1, editor.replaceCalls.size)
    }

    @Test
    fun `apply does not retain candidate state between calls`() {
        val editor = FakeEditorPort(replace = ReplaceResult.AppliedVerified)
        val coordinator = coordinator(editor = editor)
        val first = RewriteCandidate(aSnapshot(draft = "first"), "first-out")
        val second = RewriteCandidate(aSnapshot(draft = "second"), "second-out")

        runBlocking { coordinator.apply(first) }
        runBlocking { coordinator.apply(second) }

        assertEquals(2, editor.replaceCalls.size)
        assertEquals(first.snapshot to first.replacement, editor.replaceCalls[0])
        assertEquals(second.snapshot to second.replacement, editor.replaceCalls[1])
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun assertStaleMapping(reason: StaleReason) {
        val editor = FakeEditorPort(replace = ReplaceResult.Stale(reason))
        val candidate = RewriteCandidate(aSnapshot(), "rewritten")

        val result = runBlocking { coordinator(editor = editor).apply(candidate) }

        assertEquals(ApplyResult.Stale(reason), result)
        assertEquals(1, editor.replaceCalls.size, "apply must attempt replacement exactly once — no retry")
    }

    private fun coordinator(
        editor: FakeEditorPort = FakeEditorPort(),
        provider: FakeCompletionProvider = FakeCompletionProvider(),
    ): RewriteCoordinator = RewriteCoordinator(FakePersonaRepository(), editor, provider)

    private fun coordinator(
        personas: PersonaRepository,
        editor: FakeEditorPort,
        provider: FakeCompletionProvider,
    ): RewriteCoordinator = RewriteCoordinator(personas, editor, provider)
}

// -----------------------------------------------------------------
// File-private fixtures
// -----------------------------------------------------------------

private val PERSONA_ID: PersonaId = PersonaId.bundled("jeeves")

private val TEST_PERSONA: ValidatedPersona = ValidatedPersona(
    id = PERSONA_ID,
    provenance = PersonaProvenance.bundled,
    content = Persona(
        name = "Jeeves",
        speechPatterns = listOf("speaks with precise, deferential economy"),
    ),
)

private fun aSnapshot(
    draft: String = "draft text",
    selection: Utf16Selection = Utf16Selection(0, draft.length),
): EditorSnapshot = EditorSnapshot(
    session = EditorSessionToken(7L),
    generation = RequestGeneration(1L),
    draft = draft,
    selection = selection,
)

private class FakePersonaRepository(
    private val persona: ValidatedPersona? = TEST_PERSONA,
) : PersonaRepository {
    var loadCalls: Int = 0
        private set

    override fun list(): Result<List<PersonaSummary>> =
        Result.success(listOfNotNull(persona?.let { PersonaSummary(it.id, it.content.name) }))

    override fun load(id: PersonaId): Result<ValidatedPersona> {
        loadCalls += 1
        return if (persona != null && id == persona.id) Result.success(persona)
        else Result.failure(NoSuchElementException("unknown persona ${id.value}"))
    }
}

private class FakeEditorPort(
    private val capture: CaptureResult = CaptureResult.Captured(aSnapshot()),
    private val replace: ReplaceResult = ReplaceResult.AppliedVerified,
) : EditorPort {
    var captureCalls: Int = 0
        private set
    val replaceCalls: MutableList<Pair<EditorSnapshot, String>> = mutableListOf()

    override suspend fun captureSnapshot(): CaptureResult {
        captureCalls += 1
        return capture
    }

    override suspend fun attemptReplace(snapshot: EditorSnapshot, replacement: String): ReplaceResult {
        replaceCalls += snapshot to replacement
        return replace
    }
}

private open class FakeCompletionProvider(
    private val result: Result<String> = Result.success("rewritten"),
    private val throwOnCall: Throwable? = null,
) : CompletionProvider {
    val calls: MutableList<Pair<String, String>> = mutableListOf()

    override val id: String = "fake"
    override val displayName: String = "Fake"

    override suspend fun rewrite(system: String, text: String): Result<String> {
        recordCall(system, text)
        throwOnCall?.let { throw it }
        return result
    }

    protected fun recordCall(system: String, text: String) {
        calls += system to text
    }
}

private class CancellingCompletionProvider(
    val entered: CompletableDeferred<Unit>,
    val exited: CompletableDeferred<Unit>,
) : FakeCompletionProvider() {
    override val id: String = "cancelling"
    override val displayName: String = "Cancelling"

    override suspend fun rewrite(system: String, text: String): Result<String> {
        recordCall(system, text)
        entered.complete(Unit)
        try {
            suspendCancellableCoroutine<Unit> { /* suspends until cancelled */ }
        } finally {
            exited.complete(Unit)
        }
        @Suppress("UNREACHABLE_CODE")
        error("suspendCancellableCoroutine never returns normally")
    }
}
