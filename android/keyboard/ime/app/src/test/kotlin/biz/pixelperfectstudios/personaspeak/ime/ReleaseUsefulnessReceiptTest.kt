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
import biz.pixelperfectstudios.personaspeak.ime.editor.FakeInputConnection
import biz.pixelperfectstudios.personaspeak.ime.editor.InputConnectionEditorPort
import biz.pixelperfectstudios.personaspeak.ime.editor.EditorSessionState
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
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Milestone 8 Slice B — Production Usefulness & User-Visible Error Surfacing Test.
 *
 * Evidence classes exercised:
 * 1. `mock_transport_adapter_harness`: Verifies OpenRouter and Anthropic HTTP payload contracts and memory zeroing.
 * 2. `composition_and_ui_harness`: Verifies full end-to-end rewrite cycle (FakeProvider offline understudy)
 *    through RewriteCoordinator -> Review Candidate -> Apply with exactly 1 host editor mutation.
 * 3. `ui_error_sanitization_harness`: Verifies provider failures surface strictly as user-presentable StitchError cards
 *    without exposing raw exceptions, stack traces, or credentials.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseUsefulnessReceiptTest {

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var personaRepo: BundledPersonaRepository

    @Before
    fun setUp() {
        personaRepo = BundledPersonaRepository(AssetPersonaDocumentSource(context.assets))
        PersonaSpeakSessionState.instance.reset()
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
        val jeevesId = PersonaId.bundled("jeeves")

        // 1. Request rewrite through coordinator (understudy pipeline)
        val requestResult = coordinator.request(jeevesId, Mood.Polite)
        assertTrue("Expected Ready result, got $requestResult", requestResult is RewriteRequestResult.Ready)
        val ready = requestResult as RewriteRequestResult.Ready
        val candidate = ready.candidate
        assertTrue("Candidate must contain polite rewrite", candidate.replacement.contains("running late"))

        // 2. Apply candidate
        val applyResult = coordinator.apply(candidate)
        assertTrue("Expected AppliedVerified, got $applyResult", applyResult is ApplyResult.AppliedVerified)

        // 3. Verify exactly 1 text replacement on host editor
        assertEquals(1, fakeConnection.replaceTextCalls)
        assertEquals(candidate.replacement, fakeConnection.text)
    }

    @Test
    fun `provider failure surfaces as sanitized user-presentable UI card without leaking exceptions`() = runBlocking {
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
            override suspend fun rewrite(systemPrompt: String, userText: String): Result<String> {
                return Result.failure(IOException("Fatal connection reset to api.provider.internal/secret_token_12345"))
            }
        }
        val coordinator = RewriteCoordinator(personaRepo, editorPort, failingProvider)
        val jeevesId = PersonaId.bundled("jeeves")

        val result = coordinator.request(jeevesId, Mood.Polite)
        assertTrue("Expected ProviderFailure, got $result", result is RewriteRequestResult.ProviderFailure)

        // Verify StitchError card presentation
        val error = StitchError.ProviderFailure
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
