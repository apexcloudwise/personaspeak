package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets

class AdapterNetworkErrorCodeTest {

    @Test
    fun testStatusCodeTaxonomyMapping() = runTest {
        val testCases = mapOf(
            400 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_CLIENT_ERROR),
            404 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_CLIENT_ERROR),
            429 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_CLIENT_ERROR),
            500 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR),
            502 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR),
            503 to AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR),
        )

        for ((status, expected) in testCases) {
            val transport = object : HttpTransport {
                override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                    return HttpResponse(status, "{}")
                }
            }
            val adapter = AnthropicMessagesAdapter(transport = transport)
            val secret = SecretBytes("key".toByteArray(StandardCharsets.UTF_8))
            val result = adapter.rewrite("s", "t", secret)
            assertEquals("Failed for HTTP status $status", expected, result)
        }
    }

    @Test
    fun ioExceptionMapsToIoErrorCode() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                throw IOException("Connection reset by peer")
            }
        }
        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secret = SecretBytes("key".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("s", "t", secret)
        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.IO_ERROR), result)
    }
}
