package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class AdapterAuthFailureTest {

    @Test
    fun http401MapsToAuthFailureAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(401, "{\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = "sk-ant-invalid-key".toByteArray(StandardCharsets.UTF_8)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("system prompt", "draft text", secret)

        assertEquals(AdapterResult.AuthFailure, result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun http403MapsToAuthFailureAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(403, "{\"error\":{\"type\":\"permission_error\",\"message\":\"forbidden\"}}")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = "sk-ant-forbidden-key".toByteArray(StandardCharsets.UTF_8)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("system prompt", "draft text", secret)

        assertEquals(AdapterResult.AuthFailure, result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }
}
