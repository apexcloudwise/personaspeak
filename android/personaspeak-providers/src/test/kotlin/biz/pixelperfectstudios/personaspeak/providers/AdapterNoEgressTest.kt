package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterNoEgressTest {

    @Test
    fun adapterExecutesEntirelyViaMockTransportWithoutRealNetworkEgress() = runTest {
        var networkCallsAttempted = 0
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                networkCallsAttempted++
                return HttpResponse(200, """{"content":[{"type":"text","text":""}]}""")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secret = SecretBytes(byteArrayOf(1))
        val result = adapter.rewrite("", "", secret)

        assertEquals(1, networkCallsAttempted)
        assertTrue(result is AdapterResult.Success)
        assertEquals("", (result as AdapterResult.Success).rewritten)
    }
}
