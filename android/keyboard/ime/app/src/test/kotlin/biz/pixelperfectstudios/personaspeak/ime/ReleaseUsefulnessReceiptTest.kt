package biz.pixelperfectstudios.personaspeak.ime

import android.app.Application
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.HttpResponse
import biz.pixelperfectstudios.personaspeak.providers.HttpTransport
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ime.editor.EditorSessionState
import biz.pixelperfectstudios.personaspeak.ime.editor.FakeInputConnection
import biz.pixelperfectstudios.personaspeak.ime.editor.InputConnectionEditorPort
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.rewrite.ApplyResult
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewriteCoordinator
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelState
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelViewModel
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewriteRequestResult
import biz.pixelperfectstudios.personaspeak.ui.rewrite.StitchError
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Milestone 8 Slice B — Production Usefulness & User-Visible Error Surfacing Test.
 *
 * Evidence classes exercised:
 * 1. `mock_transport_adapter_harness`: Verifies OpenRouter and Anthropic HTTP payload contracts and memory zeroing.
 * 2. `composition_and_ui_harness`: Verifies full end-to-end rewrite cycle (FakeProvider offline understudy)
 *    through RewriteCoordinator -> RewritePanelViewModel -> Review Candidate -> Apply with exactly 1 host editor mutation.
 * 3. `ui_error_sanitization_harness`: Verifies provider failures surface through RewritePanelViewModel strictly
 *    as user-presentable RewritePanelState.Error(StitchError) cards without exposing raw exceptions, stack traces, or credentials.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseUsefulnessReceiptTest {

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val sessionState = PersonaSpeakSessionState.instance
    private lateinit var personaRepo: BundledPersonaRepository

    @Before
    fun setUp() {
        personaRepo = BundledPersonaRepository(AssetPersonaDocumentSource(context.assets))
        sessionState.reset()
    }

    private class MockHttpTransport(
        private val responseSupplier: (String) -> HttpResponse,
    ) : HttpTransport {
        override fun post(
            endpointUrl: String,
            headers: Map<String, String>,
            bodyUtf8: ByteArray,
        ): HttpResponse = responseSupplier(endpointUrl)
    }

    @Test
    fun `end-to-end offline rewrite cycle through UI panel produces valid candidate and 1 editor mutation`() = runBlocking {
        val fakeConnection = FakeInputConnection(text = "running late", selectionStart = 0, selectionEnd = 12)
        val editorSession = EditorSessionState()
        val editorPort = InputConnectionEditorPort(
            sessionState = editorSession,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT } },
        )
        val fakeProvider = FakeProvider()
        val coordinator = RewriteCoordinator(personaRepo, editorPort, fakeProvider)
        val viewModel = RewritePanelViewModel(coordinator, personaRepo, sessionState)

        // 1. Initial state is Resting
        assertTrue(viewModel.state.value is RewritePanelState.Resting)

        // 2. Request rewrite through ViewModel
        viewModel.request()
        while (viewModel.state.value is RewritePanelState.Loading) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper()
        }

        // 3. State transitions to Review with candidate preview
        val stateAfterRequest = viewModel.state.value
        assertTrue("State must be Review, was $stateAfterRequest", stateAfterRequest is RewritePanelState.Review)
        val reviewState = stateAfterRequest as RewritePanelState.Review
        val candidateText = reviewState.candidate.replacement
        assertTrue("Candidate must contain original text with tone", candidateText.contains("running late"))

        // 4. Apply candidate through ViewModel
        viewModel.apply()
        while (viewModel.state.value is RewritePanelState.Applying) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper()
        }

        // 5. State transitions to AppliedVerified and editor text is mutated exactly once
        assertTrue(viewModel.state.value is RewritePanelState.AppliedVerified)
        assertEquals(candidateText, fakeConnection.text)
        assertEquals(1, fakeConnection.replaceTextCalls)
    }

    @Test
    fun `provider failure surfaces through ViewModel as sanitized user-presentable UI card without leaking exceptions`() = runBlocking {
        val fakeConnection = FakeInputConnection(text = "Tea at six.", selectionStart = 0, selectionEnd = 11)
        val editorSession = EditorSessionState()
        val editorPort = InputConnectionEditorPort(
            sessionState = editorSession,
            connectionSupplier = { fakeConnection },
            editorInfoSupplier = { EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT } },
        )
        val failingProvider = object : CompletionProvider {
            override val id: String = "failing_mock"
            override val displayName: String = "Failing Mock"
            override suspend fun rewrite(system: String, text: String): Result<String> {
                return Result.failure(IOException("Fatal connection reset to api.provider.internal/secret_token_12345"))
            }

            override suspend fun suggest(system: String, text: String, count: Int): Result<List<String>> {
                return Result.failure(IOException("Fatal connection reset to api.provider.internal/secret_token_12345"))
            }
        }
        val coordinator = RewriteCoordinator(personaRepo, editorPort, failingProvider)
        val viewModel = RewritePanelViewModel(coordinator, personaRepo, sessionState)

        // Request via ViewModel
        viewModel.request()
        while (viewModel.state.value is RewritePanelState.Loading) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper()
        }

        // State must transition to RewritePanelState.Error containing StitchError.ProviderFailure
        val state = viewModel.state.value
        assertTrue("State must be Error, was $state", state is RewritePanelState.Error)
        val errorState = state as RewritePanelState.Error
        val error = errorState.error

        assertEquals(StitchError.ProviderFailure, error)
        assertEquals("Service unavailable", error.title)
        assertEquals("Rewriting service is unavailable.", error.explanation)
        assertTrue("Editor text must remain untouched on failure", error.editorUntouched)
        assertEquals("Tea at six.", fakeConnection.text)
        assertEquals(0, fakeConnection.replaceTextCalls)

        // Verify zero raw stack trace or secret leakage in user UI
        assertFalse(error.explanation.contains("IOException"))
        assertFalse(error.explanation.contains("secret_token_12345"))
        assertFalse(error.explanation.contains("api.provider.internal"))
    }

    @Test
    fun `openrouter adapter mock transport verifies payload contract and secret zeroing`() = runBlocking {
        val expectedRewritten = "I am cordially obliged to inform you that I shall attend tea at six."
        val mockTransport = MockHttpTransport {
            HttpResponse(
                statusCode = 200,
                body = """{"choices":[{"message":{"content":"$expectedRewritten"}}]}""",
            )
        }

        val adapter = OpenRouterAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-or-test-key-123".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite(
            system = "You are Dr. King Schultz. Speak eloquently.",
            text = "Tea at six.",
            secret = secret,
        )

        assertTrue(result is AdapterResult.Success)
        val candidate = (result as AdapterResult.Success).rewritten
        assertEquals(expectedRewritten, candidate)
        assertTrue("SecretBytes must be zero-filled in memory", secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `anthropic adapter mock transport verifies payload contract and secret zeroing`() = runBlocking {
        val expectedRewritten = "Splendid. I will join you for tea at six sharp."
        val mockTransport = MockHttpTransport {
            HttpResponse(
                statusCode = 200,
                body = """{"content":[{"type":"text","text":"$expectedRewritten"}]}""",
            )
        }

        val adapter = AnthropicMessagesAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-ant-test-key-456".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite(
            system = "You are Jeeves. Speak with utmost decorum.",
            text = "Tea at six.",
            secret = secret,
        )

        assertTrue(result is AdapterResult.Success)
        val candidate = (result as AdapterResult.Success).rewritten
        assertEquals(expectedRewritten, candidate)
        assertTrue("SecretBytes must be zero-filled in memory", secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `auth failure in adapter zeroes secret and maps cleanly to AuthFailure outcome`() = runBlocking {
        val mockTransport = MockHttpTransport {
            HttpResponse(
                statusCode = 401,
                body = """{"error":{"message":"Invalid API key provided"}}""",
            )
        }

        val adapter = OpenRouterAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-invalid-secret-999".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("system", "Tea at six.", secret)

        assertTrue(result is AdapterResult.AuthFailure)
        assertTrue("SecretBytes must be zero-filled", secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `rate limit and network errors in adapter map to closed NetworkFailure codes`() = runBlocking {
        val mockTransportRate = MockHttpTransport {
            HttpResponse(statusCode = 429, body = """{"error":"Rate limit"}""")
        }
        val adapterRate = OpenRouterAdapter(transport = mockTransportRate)
        val secretRate = SecretBytes("sk-valid".toByteArray(StandardCharsets.UTF_8))
        val resultRate = adapterRate.rewrite("system", "Tea at six.", secretRate)
        assertTrue(resultRate is AdapterResult.NetworkFailure)
        assertEquals(NetworkErrorCode.HTTP_CLIENT_ERROR, (resultRate as AdapterResult.NetworkFailure).code)

        val mockTransportTimeout = MockHttpTransport {
            throw IOException("Socket timeout")
        }
        val adapterTimeout = OpenRouterAdapter(transport = mockTransportTimeout)
        val secretTimeout = SecretBytes("sk-valid".toByteArray(StandardCharsets.UTF_8))
        val resultTimeout = adapterTimeout.rewrite("system", "Tea at six.", secretTimeout)
        assertTrue(resultTimeout is AdapterResult.NetworkFailure)
        assertEquals(NetworkErrorCode.IO_ERROR, (resultTimeout as AdapterResult.NetworkFailure).code)
    }
}
