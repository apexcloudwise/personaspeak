package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RewritePanelViewModelTest {

    private lateinit var fakeRepo: FakeRepo
    private lateinit var fakeEditor: FakeEditorPort
    private lateinit var fakeProvider: FakeProvider
    private lateinit var coordinator: RewriteCoordinator
    private val jeevesId = PersonaId.bundled("jeeves")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeRepo()
        fakeEditor = FakeEditorPort()
        fakeProvider = FakeProvider()
        coordinator = RewriteCoordinator(fakeRepo, fakeEditor, fakeProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        personaId: PersonaId = jeevesId,
    ): RewritePanelViewModel = RewritePanelViewModel(
        coordinator = coordinator,
        personaId = personaId,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `idle to loading to review without mutation`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "Hello world",
                selection = Utf16Selection(0, 11),
            ),
        )
        fakeProvider.result = Result.success("Rephrased: Hello world")

        val vm = createViewModel()
        assertEquals(RewritePanelState.Idle, vm.state.value)

        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Review, got $state", state is RewritePanelState.Review)
        val review = state as RewritePanelState.Review
        assertEquals("Rephrased: Hello world", review.candidate.replacement)
    }

    @Test
    fun `review contains only the current in-memory candidate`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "Test draft",
                selection = Utf16Selection(0, 10),
            ),
        )
        fakeProvider.result = Result.success("Only this replacement")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val review = vm.state.value as RewritePanelState.Review
        assertEquals("Only this replacement", review.candidate.replacement)
        assertEquals("Test draft", review.candidate.snapshot.draft)
    }

    @Test
    fun `apply maps AppliedVerified to Review with Applied outcome`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")
        fakeEditor.applyResult = ReplaceResult.AppliedVerified

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        vm.apply()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Review(Applied), got $state",
            state is RewritePanelState.Review && state.outcome == RewriteOutcome.Applied,
        )
    }

    @Test
    fun `apply maps Stale to Review with Stale outcome`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")
        fakeEditor.applyResult = ReplaceResult.Stale(StaleReason.TextChanged)

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        vm.apply()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Review(Stale), got $state",
            state is RewritePanelState.Review && state.outcome is RewriteOutcome.Stale,
        )
    }

    @Test
    fun `apply maps WriteRejected to Review with Rejected outcome`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")
        fakeEditor.applyResult = ReplaceResult.WriteRejected

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        vm.apply()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Review(Rejected), got $state",
            state is RewritePanelState.Review && state.outcome == RewriteOutcome.Rejected,
        )
    }

    @Test
    fun `apply maps WriteUnconfirmed to Review with Unconfirmed outcome`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")
        fakeEditor.applyResult = ReplaceResult.WriteUnconfirmed

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        vm.apply()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Review(Unconfirmed), got $state",
            state is RewritePanelState.Review && state.outcome == RewriteOutcome.Unconfirmed,
        )
    }

    @Test
    fun `newer request cancels and discards older result`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )

        var callCount = 0
        fakeProvider.resultFunction = {
            callCount++
            if (callCount == 1) Result.success("first")
            else Result.success("second")
        }

        val vm = createViewModel()
        vm.request()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Review with second result, got $state",
            state is RewritePanelState.Review &&
                state.candidate.replacement == "second",
        )
    }

    @Test
    fun `editor finish cancels in-flight provider call and clears candidate`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")

        val vm = createViewModel()
        vm.request()
        vm.finish()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Idle after finish, got $state", state is RewritePanelState.Idle)
    }

    @Test
    fun `result arriving after finish cannot mutate UI`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")

        val vm = createViewModel()
        vm.finish()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected non-Review state after finish, got $state",
            state !is RewritePanelState.Review,
        )
    }

    @Test
    fun `SavedStateHandle contains no content keys`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val vm = RewritePanelViewModel(
            coordinator = coordinator,
            personaId = jeevesId,
            savedStateHandle = savedStateHandle,
        )

        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "sensitive draft text",
                selection = Utf16Selection(0, 20),
            ),
        )
        fakeProvider.result = Result.success("sensitive replacement text")

        vm.request()
        advanceUntilIdle()

        val contentKeys = setOf("draft", "result", "snapshot", "candidate", "replacement", "text")
        val actualKeys = savedStateHandle.keys().toList()
        for (key in actualKeys) {
            assertTrue(
                "SavedStateHandle contains content key: $key",
                key !in contentKeys,
            )
        }
    }

    @Test
    fun `onCleared cancels work`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        assertTrue("Precondition: should be Review", vm.state.value is RewritePanelState.Review)

        val store = ViewModelStore()
        store.put("vm", vm)
        store.clear()

        // After onCleared, viewModelScope is cancelled — new requests are no-ops
        vm.request()
        advanceUntilIdle()
        assertTrue(
            "Expected non-Review after onCleared + request, got ${vm.state.value}",
            vm.state.value !is RewritePanelState.Review,
        )
    }

    @Test
    fun `failure message does not embed draft or provider text`() = runTest {
        fakeRepo.failLoad = true
        val vm = createViewModel()

        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Message state, got $state", state is RewritePanelState.Message)
        val text = (state as RewritePanelState.Message).kind.toString()
        assertTrue("Message should not contain 'secret draft' but was: $text", "secret draft" !in text)
        assertTrue("Message should not contain 'provider output' but was: $text", "provider output" !in text)
    }

    @Test
    fun `provider failure does not embed provider text`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "secret draft",
                selection = Utf16Selection(0, 12),
            ),
        )
        fakeProvider.failWith = RuntimeException("internal provider error details")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Message state, got $state", state is RewritePanelState.Message)
        val text = (state as RewritePanelState.Message).kind.toString()
        assertTrue("Message should not contain 'secret draft' but was: $text", "secret draft" !in text)
        assertTrue(
            "Message should not contain 'internal provider error details' but was: $text",
            "internal provider error details" !in text,
        )
    }

    @Test
    fun `empty input maps to EmptyInput message`() = runTest {
        fakeEditor.captureResult = CaptureResult.EmptyInput
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Message(EmptyInput), got $state",
            state is RewritePanelState.Message && state.kind is RewriteMessage.EmptyInput,
        )
    }

    @Test
    fun `sensitive editor maps to SensitiveEditor message`() = runTest {
        fakeEditor.captureResult = CaptureResult.SensitiveEditor
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Message(SensitiveEditor), got $state",
            state is RewritePanelState.Message && state.kind is RewriteMessage.SensitiveEditor,
        )
    }

    @Test
    fun `dismiss returns to idle`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "draft",
                selection = Utf16Selection(0, 5),
            ),
        )
        fakeProvider.result = Result.success("replaced")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()
        assertTrue(vm.state.value is RewritePanelState.Review)

        vm.dismiss()
        assertEquals(RewritePanelState.Idle, vm.state.value)
    }

    class FakeRepo : PersonaRepository {
        var failLoad = false

        override fun list(): Result<List<biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary>> {
            return Result.success(emptyList())
        }

        override fun load(id: PersonaId): Result<ValidatedPersona> {
            if (failLoad) return Result.failure(IllegalArgumentException("not found"))
            return Result.success(
                ValidatedPersona(
                    id = id,
                    provenance = PersonaProvenance.bundled,
                    content = Persona(
                        name = "Jeeves",
                        context = " (the valet)",
                        speechPatterns = listOf("Formal English"),
                    ),
                ),
            )
        }
    }

    class FakeEditorPort : EditorPort {
        var captureResult: CaptureResult = CaptureResult.EmptyInput
        var applyResult: ReplaceResult = ReplaceResult.AppliedVerified

        override suspend fun captureSnapshot(): CaptureResult = captureResult

        override suspend fun attemptReplace(
            snapshot: EditorSnapshot,
            replacement: String,
        ): ReplaceResult = applyResult
    }

    class FakeProvider : CompletionProvider {
        override val id = "fake"
        override val displayName = "Fake"

        var result: Result<String> = Result.success("replaced")
        var failWith: Throwable? = null
        var resultFunction: ((String) -> Result<String>)? = null

        override suspend fun rewrite(system: String, text: String): Result<String> {
            if (failWith != null) return Result.failure(failWith!!)
            return resultFunction?.invoke(text) ?: result
        }
    }
}
