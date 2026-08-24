package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdapterEgressBindingTest {

    @Test
    fun constantEndpointIsStrictlyAnthropicMessagesUrl() {
        assertEquals("https://api.anthropic.com/v1/messages", AnthropicMessagesAdapter.ENDPOINT_URL)
    }

    @Test
    fun defaultTransportRejectsNonAnthropicEndpoints() {
        val transport = DefaultHttpTransport()
        assertThrows(IllegalArgumentException::class.java) {
            transport.post("https://evil.com/leak", emptyMap(), ByteArray(0))
        }
    }
}
