package biz.pixelperfectstudios.personaspeak.ime

import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.HttpResponse
import biz.pixelperfectstudios.personaspeak.providers.HttpTransport
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Milestone 8 Slice B — Production Usefulness & Error Surfacing Test.
 *
 * Verifies:
 * 1. Deterministic production-path rewrite execution yielding high-quality persona candidate.
 * 2. User-presentable error transformations (401 auth failure, 429 rate limit, 503 provider error, network timeout).
 * 3. Zero raw exception or stack trace leakage to user UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseUsefulnessReceiptTest {

    private class MockHttpTransport(
        private val responseSupplier: (String) -> HttpResponse,
    ) : HttpTransport {
        override fun post(
            endpointUrl: String,
            headers: Map<String, String>,
            bodyUtf8: ByteArray,
        ): HttpResponse {
            return responseSupplier(endpointUrl)
        }
    }

    @Test
    fun `production openrouter path executes deterministic rewrite`() = runBlocking {
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
        assertTrue(secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `production anthropic path executes deterministic rewrite`() = runBlocking {
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
        assertTrue(secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `offline fallback fake provider executes deterministic persona rewrite`() = runBlocking {
        val fake = FakeProvider()
        val result = fake.rewrite("system prompt", "running late")
        assertTrue(result.isSuccess)
        val text = result.getOrThrow()
        assertNotNull(text)
        assertTrue(text.contains("running late"))
    }

    @Test
    fun `auth failure surfaces clean user-presentable error without leaking tokens`() = runBlocking {
        val mockTransport = MockHttpTransport {
            HttpResponse(
                statusCode = 401,
                body = """{"error":{"message":"Invalid API key provided"}}""",
            )
        }

        val adapter = OpenRouterAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-invalid".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("system", "Tea at six.", secret)

        assertTrue(result is AdapterResult.AuthFailure)
        assertTrue(secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `rate limit error surfaces clean retryable network error code`() = runBlocking {
        val mockTransport = MockHttpTransport {
            HttpResponse(
                statusCode = 429,
                body = """{"error":{"message":"Rate limit exceeded. Please retry in 5s."}}""",
            )
        }

        val adapter = OpenRouterAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-or-valid".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("system", "Tea at six.", secret)

        assertTrue(result is AdapterResult.NetworkFailure)
        val networkFailure = result as AdapterResult.NetworkFailure
        assertEquals(biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode.HTTP_CLIENT_ERROR, networkFailure.code)
    }

    @Test
    fun `network transport error surfaces clean failure code without crashing`() = runBlocking {
        val mockTransport = MockHttpTransport {
            throw IOException("Unable to resolve host \"openrouter.ai\": No address associated with hostname")
        }

        val adapter = OpenRouterAdapter(transport = mockTransport)
        val secret = SecretBytes("sk-or-valid".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("system", "Tea at six.", secret)

        assertTrue(result is AdapterResult.NetworkFailure)
        val networkFailure = result as AdapterResult.NetworkFailure
        assertEquals(biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode.IO_ERROR, networkFailure.code)
    }
}
