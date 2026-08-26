package biz.pixelperfectstudios.personaspeak.providers

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class MiniJsonRealPayloadTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().decodeToString()

    @Test
    fun parsesRealModelsPayload() {
        val text = fixture("or_models.json")
        val data = MiniJson.path(MiniJson.parse(text), "data") as? List<*>
        assertTrue(data != null && data.size > 300, "expected large data array, got ${data?.size}")
        val nemotron = data!!.firstOrNull { MiniJson.path(it, "id") == "nvidia/nemotron-3-super-120b-a12b:free" }
        assertTrue(nemotron != null, "nemotron entry missing")
        println("prompt price: " + MiniJson.path(nemotron, "pricing", "prompt"))
    }

    @Test
    fun parsesRealChatPayload() {
        val text = fixture("or_chat.json")
        val content = MiniJson.path(MiniJson.parse(text), "choices", 0, "message", "content") as? String
        if (content == null) {
            val root = MiniJson.parse(text)
            fail("content null; root keys=${(root as? Map<*, *>)?.keys} choices=${MiniJson.path(root, "choices")}")
        }
        assertTrue(content!!.isNotBlank())
    }
}
