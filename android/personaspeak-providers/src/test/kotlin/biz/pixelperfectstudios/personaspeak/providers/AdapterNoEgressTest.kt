package biz.pixelperfectstudios.personaspeak.providers

import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class AdapterNoEgressTest {

    @Test
    fun adapterExecutesEntirelyViaMockTransportWithoutRealNetworkEgress() = runTest {
        var networkCallsAttempted = 0
        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                networkCallsAttempted++
                return HttpResponse(200, """{"content":[{"type":"text","text":"offline mock result"}]}""")
            }
        }

        val adapter = AnthropicMessagesAdapter(transport = transport)
        val secret = SecretBytes("key".toByteArray(StandardCharsets.UTF_8))
        val result = adapter.rewrite("s", "t", secret)

        assertEquals(1, networkCallsAttempted)
        assertTrue(result is AdapterResult.Success)
        assertEquals("offline mock result", (result as AdapterResult.Success).rewritten)
    }
}
