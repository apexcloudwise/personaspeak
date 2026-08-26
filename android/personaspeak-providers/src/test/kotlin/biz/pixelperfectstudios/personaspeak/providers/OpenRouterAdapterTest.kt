package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class OpenRouterAdapterTest {

    @Test
    fun testSuccessfulRewriteExtraction() = runTest {
        val sampleResponse = """
        {
          "id": "gen-12345",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "I have taken the liberty of rephrasing your words, sir."
              },
              "finish_reason": "stop"
            }
          ]
        }
        """.trimIndent()

        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(200, sampleResponse)
            }
        }

        val adapter = OpenRouterAdapter(transport = transport)
        val secretBytes = "test_key".toByteArray(StandardCharsets.UTF_8)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("system prompt", "hello world", secret)

        assertTrue("Expected Success result", result is AdapterResult.Success)
        assertEquals("I have taken the liberty of rephrasing your words, sir.", (result as AdapterResult.Success).rewritten)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun testWhitespaceTrimming() = runTest {
        val response = """{"choices":[{"message":{"content":"   \n  Trimmed text.  \n\t"}}]}"""
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(200, response)
            }
        }

        val adapter = OpenRouterAdapter(transport = transport)
        val secret = SecretBytes(byteArrayOf(1, 2, 3))
        val result = adapter.rewrite("system", "text", secret)

        assertTrue(result is AdapterResult.Success)
        assertEquals("Trimmed text.", (result as AdapterResult.Success).rewritten)
    }

    @Test
    fun testAuthFailure401and403() = runTest {
        for (status in listOf(401, 403)) {
            val transport = object : HttpTransport {
                override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                    return HttpResponse(status, """{"error":{"message":"Invalid API key"}}""")
                }
            }

            val adapter = OpenRouterAdapter(transport = transport)
            val secretBytes = byteArrayOf(9, 9, 9)
            val secret = SecretBytes(secretBytes)

            val result = adapter.rewrite("sys", "txt", secret)
            assertEquals("HTTP $status must map to AuthFailure", AdapterResult.AuthFailure, result)
            assertTrue("Secret must be zeroed after auth failure", secretBytes.all { it == 0.toByte() })
        }
    }

    @Test
    fun testClientErrorMapping() = runTest {
        for (status in listOf(400, 404, 429)) {
            val transport = object : HttpTransport {
                override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                    return HttpResponse(status, "Client Error")
                }
            }

            val adapter = OpenRouterAdapter(transport = transport)
            val secret = SecretBytes(byteArrayOf(1))
            val result = adapter.rewrite("sys", "txt", secret)
            assertEquals(
                "HTTP $status must map to HTTP_CLIENT_ERROR",
                AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_CLIENT_ERROR),
                result
            )
        }
    }

    @Test
    fun testServerErrorMapping() = runTest {
        for (status in listOf(500, 502, 503, 504)) {
            val transport = object : HttpTransport {
                override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                    return HttpResponse(status, "Server Error")
                }
            }

            val adapter = OpenRouterAdapter(transport = transport)
            val secret = SecretBytes(byteArrayOf(1))
            val result = adapter.rewrite("sys", "txt", secret)
            assertEquals(
                "HTTP $status must map to HTTP_SERVER_ERROR",
                AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR),
                result
            )
        }
    }

    @Test
    fun testTimeoutMapping() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                throw SocketTimeoutException("connect timed out")
            }
        }

        val adapter = OpenRouterAdapter(transport = transport)
        val secretBytes = byteArrayOf(4, 5, 6)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("sys", "txt", secret)
        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT), result)
        assertTrue("Secret must be zeroed after timeout", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun testIoErrorMapping() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                throw IOException("connection reset")
            }
        }

        val adapter = OpenRouterAdapter(transport = transport)
        val secretBytes = byteArrayOf(7, 8, 9)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("sys", "txt", secret)
        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.IO_ERROR), result)
        assertTrue("Secret must be zeroed after IOException", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun testMalformedJsonResponse() = runTest {
        val malformedResponses = listOf(
            "not a json",
            "{}",
            """{"choices":[]}""",
            """{"choices":[{"message":{"content":""}}]}""",
            """{"choices":[{"message":{"role":"assistant"}}]}"""
        )

        for (body in malformedResponses) {
            val transport = object : HttpTransport {
                override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                    return HttpResponse(200, body)
                }
            }

            val adapter = OpenRouterAdapter(transport = transport)
            val secret = SecretBytes(byteArrayOf(1))
            val result = adapter.rewrite("sys", "txt", secret)
            assertEquals(
                "Malformed response must map to HTTP_SERVER_ERROR",
                AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR),
                result
            )
        }
    }

    @Test
    fun testRequestHeadersAndBodyFormat() = runTest {
        var recordedEndpoint: String? = null
        var recordedHeaders: Map<String, String>? = null
        var recordedBody: String? = null

        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                recordedEndpoint = endpointUrl
                recordedHeaders = headers
                recordedBody = String(bodyUtf8, StandardCharsets.UTF_8)
                return HttpResponse(200, """{"choices":[{"message":{"content":"ok"}}]}""")
            }
        }

        val adapter = OpenRouterAdapter(
            transport = transport,
            model = "custom/test-model:free",
            temperature = 0.7,
        )
        val secret = SecretBytes("sk-or-v1-secret".toByteArray(StandardCharsets.UTF_8))

        adapter.rewrite("You are Jeeves.", "Help with tea.", secret)

        assertEquals("https://openrouter.ai/api/v1/chat/completions", recordedEndpoint)
        assertEquals("Bearer sk-or-v1-secret", recordedHeaders?.get("Authorization"))
        assertEquals("https://pixelperfectstudios.biz", recordedHeaders?.get("HTTP-Referer"))
        assertEquals("PersonaSpeak", recordedHeaders?.get("X-Title"))
        assertEquals("application/json; charset=utf-8", recordedHeaders?.get("Content-Type"))

        assertTrue("Body must include model", recordedBody?.contains("\"model\":\"custom/test-model:free\"") == true)
        assertTrue("Body must include system role", recordedBody?.contains("\"role\":\"system\"") == true)
        assertTrue("Body must include system prompt", recordedBody?.contains("\"content\":\"You are Jeeves.\"") == true)
        assertTrue("Body must include user role", recordedBody?.contains("\"role\":\"user\"") == true)
        assertTrue("Body must include user prompt", recordedBody?.contains("\"content\":\"Help with tea.\"") == true)
        assertTrue("Body must include temperature", recordedBody?.contains("\"temperature\":0.7") == true)
    }

    @Test
    fun testDefaultTransportEgressValidation() {
        val transport = DefaultOpenRouterHttpTransport()
        try {
            transport.post("https://evil.com/api", emptyMap(), ByteArray(0))
            org.junit.Assert.fail("Expected IllegalArgumentException on unauthorized endpoint")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Egress violation") == true)
        }
    }
}
