package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.lifecycle.SavedStateHandle
import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.InsertResult
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import biz.pixelperfectstudios.personaspeak.ui.reply.IncomingMessageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * State-machine tests for the suggested-replies flow (plan §4.3): chip →
 * suggest → apply / dismiss / regenerate / cancel / ReplyContextGone, with a
 * fake store, fake provider, and fake EditorPort — including the empty-editor
 * insert and the non-empty rejection, and the forget-on-apply contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplySuggestionsViewModelTest {

    private val jeevesId = PersonaId.bundled("jeeves")
    private val incoming = IncomingMessageContext(
        sender = "Sam",
        appLabel = "Messages",
        text = "Running late, start the tea without me",
    )

    private lateinit var fakeRepo: FakeRepo
    private lateinit var fakeEditor: FakeEditorPort
    private lateinit var fakeProvider: FakeProvider
    private lateinit var coordinator: RewriteCoordinator
    private lateinit var store: IncomingMessageStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepo = FakeRepo()
        fakeEditor = FakeEditorPort()
        fakeProvider = FakeProvider()
        coordinator = RewriteCoordinator(fakeRepo, fakeEditor, fakeProvider)
        store = IncomingMessageStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RewritePanelViewModel = RewritePanelViewModel(
        coordinator = coordinator,
        personas = fakeRepo,
        initialPersonaId = jeevesId,
        initialMood = Mood.DEFAULT,
        replyStore = store,
        savedStateHandle = SavedStateHandle(),
    )

    private fun putIncoming() {
        store.put("u0|com.example.messages|0|42", incoming)
    }

    @Test
    fun `chip context mirrors the latest incoming message and clears with the store`() = runTest {
        val vm = createViewModel()

        assertNull(vm.replyContext.value, "nothing is shown before a message arrives")
        putIncoming()
        assertEquals(incoming, vm.replyContext.value)
        store.forget("u0|com.example.messages|0|42")
        assertNull(vm.replyContext.value, "forget-on-apply hides the chip reactively")
    }

    @Test
    fun `requestSuggestions walks chip to Suggesting to Suggestions`() = runTest {
        val vm = createViewModel()
        putIncoming()

        vm.requestSuggestions()

        assertTrue(vm.state.value is RewritePanelState.Suggestions, "Unconfined main runs the launch eagerly")
        val suggestions = vm.state.value as RewritePanelState.Suggestions
        assertEquals(3, suggestions.replies.size)
        assertEquals(incoming, suggestions.context)
        assertEquals("u0|com.example.messages|0|42", suggestions.conversationKey)
    }

    @Test
    fun `requestSuggestions without a message is a no-op`() = runTest {
        val vm = createViewModel()

        vm.requestSuggestions()

        assertTrue(vm.state.value is RewritePanelState.Resting)
    }

    @Test
    fun `applySuggestion inserts into an empty editor and forgets the conversation`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        fakeEditor.captureResult = CaptureResult.EmptyInput
        fakeEditor.insertResult = InsertResult.AppliedVerified

        vm.applySuggestion(0)

        assertTrue(vm.state.value is RewritePanelState.Resting)
        assertEquals(1, fakeEditor.insertCalls, "exactly one editor mutation")
        assertNull(store.peekLatest(), "forgotten on reply")
    }

    @Test
    fun `applySuggestion on a non-empty editor replaces the draft instead of inserting`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        fakeEditor.captureResult = CaptureResult.Captured(aSnapshot())

        vm.applySuggestion(1)

        assertTrue(vm.state.value is RewritePanelState.Resting)
        assertEquals(0, fakeEditor.insertCalls)
        assertEquals(1, fakeEditor.replaceCalls)
        assertNull(store.peekLatest(), "forget-on-apply holds on the replace path too")
    }

    @Test
    fun `applySuggestion failure keeps the message and surfaces the typed error`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        fakeEditor.captureResult = CaptureResult.EmptyInput
        fakeEditor.insertResult = InsertResult.WriteRejected

        vm.applySuggestion(2)

        val state = vm.state.value
        assertTrue(state is RewritePanelState.Error, "got $state")
        assertEquals(StitchError.WriteRejected, (state as RewritePanelState.Error).error)
        assertTrue(store.peekLatest() != null, "a failed apply must not forget the message")
    }

    @Test
    fun `dismissSuggestions returns to Resting and keeps the cached message`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()

        vm.dismissSuggestions()

        assertTrue(vm.state.value is RewritePanelState.Resting)
        assertEquals(incoming, store.peekLatest(), "dismiss keeps the context for a retry")
        assertEquals(incoming, vm.replyContext.value)
    }

    @Test
    fun `regenerate issues a fresh suggest call`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        val first = (vm.state.value as RewritePanelState.Suggestions).replies
        fakeProvider.suggestResult = listOf("one", "two", "three")

        vm.regenerateSuggestions()

        val regenerated = vm.state.value as RewritePanelState.Suggestions
        assertEquals(listOf("one", "two", "three"), regenerated.replies)
        assertTrue(first != regenerated.replies)
        assertEquals(2, fakeProvider.suggestCalls)
        assertTrue(store.peekLatest() != null)
    }

    @Test
    fun `regenerate after the store is wiped surfaces ReplyContextGone`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        store.clearAll()

        vm.regenerateSuggestions()

        val state = vm.state.value
        assertTrue(state is RewritePanelState.Error, "got $state")
        assertEquals(StitchError.ReplyContextGone, (state as RewritePanelState.Error).error)
    }

    @Test
    fun `cancel from Suggesting returns to Resting and keeps the message`() = runTest {
        val vm = createViewModel()
        putIncoming()
        vm.requestSuggestions()
        assertTrue(
            vm.state.value is RewritePanelState.Suggesting || vm.state.value is RewritePanelState.Suggestions,
        )

        vm.dismiss()

        assertTrue(vm.state.value is RewritePanelState.Resting)
        assertEquals(incoming, store.peekLatest())
    }

    // -----------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------

    private fun aSnapshot(draft: String = "existing draft"): EditorSnapshot = EditorSnapshot(
        session = EditorSessionToken(1L),
        generation = RequestGeneration(1L),
        draft = draft,
        selection = Utf16Selection(0, draft.length),
    )

    private class FakeRepo : PersonaRepository {
        private val jeeves = ValidatedPersona(
            id = PersonaId.bundled("jeeves"),
            provenance = PersonaProvenance.bundled,
            content = Persona(name = "Jeeves", speechPatterns = listOf("Impeccably formal")),
        )

        override fun list(): Result<List<PersonaSummary>> =
            Result.success(listOf(PersonaSummary(jeeves.id, jeeves.content.name)))

        override fun loadAll(): Result<List<ValidatedPersona>> = Result.success(listOf(jeeves))

        override fun load(id: PersonaId): Result<ValidatedPersona> = Result.success(jeeves)
    }

    private class FakeEditorPort : EditorPort {
        var captureResult: CaptureResult = CaptureResult.EmptyInput
        var applyResult: ReplaceResult = ReplaceResult.AppliedVerified
        var insertResult: InsertResult = InsertResult.AppliedVerified
        var insertCalls: Int = 0
            private set
        var replaceCalls: Int = 0
            private set

        override suspend fun captureSnapshot(): CaptureResult = captureResult

        override suspend fun attemptReplace(snapshot: EditorSnapshot, replacement: String): ReplaceResult {
            replaceCalls += 1
            return applyResult
        }

        override suspend fun insertDraft(text: String): InsertResult {
            insertCalls += 1
            return insertResult
        }
    }

    private class FakeProvider : CompletionProvider {
        override val id = "fake"
        override val displayName = "Fake"

        var suggestResult: List<String>? = null
        var suggestCalls: Int = 0
            private set

        override suspend fun rewrite(system: String, text: String): Result<String> =
            Result.success("rewritten")

        override suspend fun suggest(system: String, text: String, count: Int): Result<List<String>> {
            suggestCalls += 1
            val replies = suggestResult ?: List(minOf(count, 3)) { "suggestion $it" }
            return Result.success(replies)
        }
    }
}
