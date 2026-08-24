package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterAuthFailureTest {

    @Test
    fun http401MapsToAuthFailureAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(401, "{}")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = byteArrayOf(1, 2, 3, 4)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("", "", secret)

        assertEquals(AdapterResult.AuthFailure, result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }

    @Test
    fun http403MapsToAuthFailureAndZeroesSecret() = runTest {
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(403, "{}")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secretBytes = byteArrayOf(1, 2, 3, 4)
        val secret = SecretBytes(secretBytes)

        val result = adapter.rewrite("", "", secret)

        assertEquals(AdapterResult.AuthFailure, result)
        assertTrue("Secret bytes must be zeroed in memory", secretBytes.all { it == 0.toByte() })
    }
}
