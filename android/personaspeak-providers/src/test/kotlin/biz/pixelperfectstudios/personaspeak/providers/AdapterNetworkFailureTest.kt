package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.NetworkErrorCode
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class AdapterNetworkFailureTest {

    @Test
    fun timeoutExceptionMapsToTimeoutCodeAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                throw SocketTimeoutException()
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = byteArrayOf(1, 2, 3, 4)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("", "", secret)

        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.TIMEOUT), result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun serverError500MapsToHttpServerErrorAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(500, "{}")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = byteArrayOf(1, 2, 3, 4)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("", "", secret)

        assertEquals(AdapterResult.NetworkFailure(NetworkErrorCode.HTTP_SERVER_ERROR), result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }
}
