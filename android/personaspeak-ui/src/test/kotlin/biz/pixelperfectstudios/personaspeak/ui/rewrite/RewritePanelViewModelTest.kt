package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import biz.pixelperfectstudios.personaspeak.personas.Mood
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
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val humphreyId = PersonaId.bundled("sir-humphrey")

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
        mood: Mood = Mood.DEFAULT,
    ): RewritePanelViewModel = RewritePanelViewModel(
        coordinator = coordinator,
        personas = fakeRepo,
        initialPersonaId = personaId,
        initialMood = mood,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `initial state is resting with persona and mood`() = runTest {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue("Expected Resting state, got $state", state is RewritePanelState.Resting)
        val resting = state as RewritePanelState.Resting
        assertEquals(jeevesId, resting.persona.id)
        assertEquals(Mood.DEFAULT, resting.mood)
    }

    @Test
    fun `open persona picker and select persona`() = runTest {
        val vm = createViewModel()
        vm.openPersonaPicker()

        val pickerState = vm.state.value
        assertTrue("Expected PersonaPicker state, got $pickerState", pickerState is RewritePanelState.PersonaPicker)
        val picker = pickerState as RewritePanelState.PersonaPicker
        assertEquals(jeevesId, picker.selectedId)

        vm.selectPersona(humphreyId)
        val restingState = vm.state.value
        assertTrue("Expected Resting state, got $restingState", restingState is RewritePanelState.Resting)
        val resting = restingState as RewritePanelState.Resting
        assertEquals(humphreyId, resting.persona.id)
    }

    @Test
    fun `open mood picker and select mood`() = runTest {
        val vm = createViewModel()
        vm.openMoodPicker()

        val moodState = vm.state.value
        assertTrue("Expected MoodPicker state, got $moodState", moodState is RewritePanelState.MoodPicker)
        val picker = moodState as RewritePanelState.MoodPicker
        assertEquals(Mood.DEFAULT, picker.selectedMood)

        vm.selectMood(Mood.Witty)
        val restingState = vm.state.value
        assertTrue("Expected Resting state, got $restingState", restingState is RewritePanelState.Resting)
        val resting = restingState as RewritePanelState.Resting
        assertEquals(Mood.Witty, resting.mood)
    }

    @Test
    fun `resting to loading to review without mutation`() = runTest {
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
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("Expected Review, got $state", state is RewritePanelState.Review)
        val review = state as RewritePanelState.Review
        assertEquals("Rephrased: Hello world", review.candidate.replacement)
    }

    @Test
    fun `apply maps AppliedVerified to AppliedVerified state`() = runTest {
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
            "Expected AppliedVerified, got $state",
            state is RewritePanelState.AppliedVerified,
        )
        val applied = state as RewritePanelState.AppliedVerified
        assertEquals("replaced", applied.candidate.replacement)
    }

    @Test
    fun `apply maps Stale to Error(StaleEditor) state`() = runTest {
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
            "Expected Error(StaleEditor), got $state",
            state is RewritePanelState.Error && state.error == StitchError.StaleEditor,
        )
        val error = state as RewritePanelState.Error
        assertTrue(error.error.canRetry)
    }

    @Test
    fun `apply maps WriteRejected to Error(WriteRejected) state`() = runTest {
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
            "Expected Error(WriteRejected), got $state",
            state is RewritePanelState.Error && state.error == StitchError.WriteRejected,
        )
        val error = state as RewritePanelState.Error
        assertFalse(error.error.canRetry)
    }

    @Test
    fun `apply maps WriteUnconfirmed to Error(WriteUnconfirmed) state without retry`() = runTest {
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
            "Expected Error(WriteUnconfirmed), got $state",
            state is RewritePanelState.Error && state.error == StitchError.WriteUnconfirmed,
        )
        val error = state as RewritePanelState.Error
        assertFalse("WriteUnconfirmed must not offer retry", error.error.canRetry)
    }

    @Test
    fun `editor finish cancels in-flight provider call and returns to resting`() = runTest {
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
        assertTrue("Expected Resting after finish, got $state", state is RewritePanelState.Resting)
    }

    @Test
    fun `empty input maps to EmptyInput error`() = runTest {
        fakeEditor.captureResult = CaptureResult.EmptyInput
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(EmptyInput), got $state",
            state is RewritePanelState.Error && state.error == StitchError.EmptyInput,
        )
    }

    @Test
    fun `sensitive editor maps to SensitiveEditor error`() = runTest {
        fakeEditor.captureResult = CaptureResult.SensitiveEditor
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(SensitiveEditor), got $state",
            state is RewritePanelState.Error && state.error == StitchError.SensitiveEditor,
        )
    }

    @Test
    fun `unsupported editor maps to UnsupportedEditor error`() = runTest {
        fakeEditor.captureResult = CaptureResult.UnsupportedEditor
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(UnsupportedEditor), got $state",
            state is RewritePanelState.Error && state.error == StitchError.UnsupportedEditor,
        )
    }

    @Test
    fun `incomplete read maps to IncompleteRead error`() = runTest {
        fakeEditor.captureResult = CaptureResult.IncompleteRead
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(IncompleteRead), got $state",
            state is RewritePanelState.Error && state.error == StitchError.IncompleteRead,
        )
    }

    @Test
    fun `oversized input maps to OversizedInput error`() = runTest {
        fakeEditor.captureResult = CaptureResult.OversizedInput
        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(OversizedInput), got $state",
            state is RewritePanelState.Error && state.error == StitchError.OversizedInput,
        )
    }

    @Test
    fun `provider failure maps to ProviderFailure error without leaking raw content`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "secret draft",
                selection = Utf16Selection(0, 12),
            ),
        )
        fakeProvider.failWith = RuntimeException("internal provider exception")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(ProviderFailure), got $state",
            state is RewritePanelState.Error && state.error == StitchError.ProviderFailure,
        )
        val text = (state as RewritePanelState.Error).error.explanation
        assertFalse(text.contains("secret draft"))
        assertFalse(text.contains("internal provider exception"))
    }

    @Test
    fun `malformed response maps to MalformedResponse error`() = runTest {
        fakeEditor.captureResult = CaptureResult.Captured(
            EditorSnapshot(
                session = EditorSessionToken(1L),
                generation = RequestGeneration(1L),
                draft = "valid draft",
                selection = Utf16Selection(0, 11),
            ),
        )
        fakeProvider.result = Result.success("   ")

        val vm = createViewModel()
        vm.request()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(
            "Expected Error(MalformedResponse), got $state",
            state is RewritePanelState.Error && state.error == StitchError.MalformedResponse,
        )
    }

    @Test
    fun `dismiss returns to resting`() = runTest {
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
        assertTrue(vm.state.value is RewritePanelState.Resting)
    }

    @Test
    fun `SavedStateHandle contains no content keys`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val vm = RewritePanelViewModel(
            coordinator = coordinator,
            personas = fakeRepo,
            initialPersonaId = jeevesId,
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

    class FakeRepo : PersonaRepository {
        var failLoad = false

        private val jeeves = ValidatedPersona(
            id = PersonaId.bundled("jeeves"),
            provenance = PersonaProvenance.bundled,
            content = Persona(
                name = "Jeeves",
                context = " (the valet)",
                speechPatterns = listOf("Formal English"),
            ),
        )

        private val humphrey = ValidatedPersona(
            id = PersonaId.bundled("sir-humphrey"),
            provenance = PersonaProvenance.bundled,
            content = Persona(
                name = "Sir Humphrey",
                context = " (civil servant)",
                speechPatterns = listOf("Circumlocution"),
            ),
        )

        override fun list(): Result<List<PersonaSummary>> =
            Result.success(listOf(PersonaSummary(jeeves.id, jeeves.content.name), PersonaSummary(humphrey.id, humphrey.content.name)))

        override fun loadAll(): Result<List<ValidatedPersona>> =
            Result.success(listOf(jeeves, humphrey))

        override fun load(id: PersonaId): Result<ValidatedPersona> {
            if (failLoad) return Result.failure(IllegalArgumentException("not found"))
            return when (id.value) {
                "bundled:sir-humphrey" -> Result.success(humphrey)
                else -> Result.success(jeeves)
            }
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

        override suspend fun rewrite(system: String, text: String): Result<String> {
            if (failWith != null) return Result.failure(failWith!!)
            return result
        }
    }
}
