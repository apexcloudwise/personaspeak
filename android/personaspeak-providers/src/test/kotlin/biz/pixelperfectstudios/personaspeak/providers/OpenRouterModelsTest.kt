package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelsTest {

    @Test
    fun testParseModelsFreeFirstSorting() {
        val samplePayload = """
        {
          "data": [
            {
              "id": "openai/gpt-4o",
              "name": "GPT-4o",
              "pricing": {
                "prompt": "0.000005"
              }
            },
            {
              "id": "nvidia/nemotron-3-super-120b-a12b:free",
              "name": "Nemotron 3 Super 120B (free)",
              "pricing": {
                "prompt": "0"
              }
            },
            {
              "id": "anthropic/claude-3.5-sonnet",
              "name": "Claude 3.5 Sonnet",
              "pricing": {
                "prompt": "0.000003"
              }
            },
            {
              "id": "meta-llama/llama-3-8b-instruct:free",
              "name": "Llama 3 8B (free)",
              "pricing": {
                "prompt": "0.0"
              }
            }
          ]
        }
        """.trimIndent()

        val models = OpenRouterModels.parse(samplePayload)

        assertEquals(4, models.size)
        // Free models must come first, sorted by id
        assertTrue(models[0].isFree)
        assertEquals("meta-llama/llama-3-8b-instruct:free", models[0].id)

        assertTrue(models[1].isFree)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", models[1].id)

        // Paid models follow, sorted by id
        assertFalse(models[2].isFree)
        assertEquals("anthropic/claude-3.5-sonnet", models[2].id)

        assertFalse(models[3].isFree)
        assertEquals("openai/gpt-4o", models[3].id)
    }

    @Test
    fun testMalformedPayloadThrows() {
        try {
            OpenRouterModels.parse("""{"wrong":"format"}""")
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing or invalid 'data' array") == true)
        }
    }

    @Test
    fun testFetchWithHttpTransport() = runTest {
        val payload = """
        {
          "data": [
            {
              "id": "nvidia/nemotron-3-super-120b-a12b:free",
              "name": "Nemotron",
              "pricing": {"prompt": "0"}
            }
          ]
        }
        """.trimIndent()

        val transport = object : HttpTransport {
            override fun post(endpointUrl: String, headers: Map<String, String>, bodyUtf8: ByteArray): HttpResponse {
                return HttpResponse(200, payload)
            }
        }

        val result = OpenRouterModels.fetch(transport = transport)
        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertEquals(1, list?.size)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", list?.firstOrNull()?.id)
        assertTrue(list?.firstOrNull()?.isFree == true)
    }
}
