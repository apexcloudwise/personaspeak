package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class AdapterSecretFlowTest {

    @Test
    fun headersContainApiKeyAndAnthropicVersionAndSecretZeroedOnSuccess() = runTest {
        var recordedHeaders: Map<String, String>? = null
        var recordedBody: String? = null

        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                recordedHeaders = headers
                recordedBody = String(bodyUtf8, StandardCharsets.UTF_8)
                return HttpResponse(
                    200,
                    """{"content":[{"type":"text","text":"Rewritten text successfully."}]}"""
                )
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = "sk-ant-valid-secret-key".toByteArray(StandardCharsets.UTF_8)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("system persona prompt", "Hello world draft", secret)

        assertTrue(result is AdapterResult.Success)
        assertEquals("Rewritten text successfully.", (result as AdapterResult.Success).rewritten)

        assertEquals("sk-ant-valid-secret-key", recordedHeaders?.get("x-api-key"))
        assertEquals("2023-06-01", recordedHeaders?.get("anthropic-version"))
        assertEquals("application/json; charset=utf-8", recordedHeaders?.get("content-type"))

        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }
}
