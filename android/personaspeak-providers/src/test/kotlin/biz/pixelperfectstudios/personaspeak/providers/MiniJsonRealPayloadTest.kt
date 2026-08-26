package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonRealPayloadTest {

    private fun fixture(name: String): String =
        javaClass.classLoader?.getResourceAsStream(name)?.readBytes()?.decodeToString()
            ?: error("Missing fixture resource: $name")

    @Test
    fun parsesRealModelsPayload() {
        val text = fixture("or_models.json")
        val root = MiniJson.parse(text)
        val data = MiniJson.path(root, "data") as? List<*>
        assertTrue("expected large data array, got ${data?.size}", data != null && data.size > 300)
        val nemotron = data?.firstOrNull { MiniJson.path(it, "id") == "nvidia/nemotron-3-super-120b-a12b:free" }
        assertNotNull("nemotron entry missing", nemotron)
        val promptPrice = MiniJson.path(nemotron, "pricing", "prompt") as? String
        assertEquals("0", promptPrice)

        val models = OpenRouterModels.parse(text)
        assertTrue(models.isNotEmpty())
        assertTrue(models.first().isFree)
    }

    @Test
    fun parsesRealChatPayload() {
        val text = fixture("or_chat.json")
        val content = MiniJson.path(MiniJson.parse(text), "choices", 0, "message", "content") as? String
        assertNotNull("content must not be null", content)
        assertTrue(content?.isNotBlank() == true)
        val extracted = OpenRouterAdapter.extractTextFromResponse(text)
        assertEquals(content?.trim(), extracted)
    }
}
