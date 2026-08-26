package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    @Test
    fun testParseSimpleObject() {
        val json = """{"name":"Jeeves","age":42,"active":true,"bio":null}"""
        val parsed = MiniJson.parse(json) as Map<*, *>

        assertEquals("Jeeves", parsed["name"])
        assertEquals(42L, parsed["age"])
        assertEquals(true, parsed["active"])
        assertNull(parsed["bio"])
    }

    @Test
    fun testParseArray() {
        val json = """["apple", 123, false, {"nested":"val"}]"""
        val parsed = MiniJson.parse(json) as List<*>

        assertEquals(4, parsed.size)
        assertEquals("apple", parsed[0])
        assertEquals(123L, parsed[1])
        assertEquals(false, parsed[2])
        assertEquals(mapOf("nested" to "val"), parsed[3])
    }

    @Test
    fun testParseEscapedStrings() {
        val json = """{"msg":"Hello\nWorld\t\"quoted\"\\slash\u0041"}"""
        val parsed = MiniJson.parse(json) as Map<*, *>

        assertEquals("Hello\nWorld\t\"quoted\"\\slashA", parsed["msg"])
    }

    @Test
    fun testQuoteEscaping() {
        val original = "Test\n\"quotes\"\tand \\slashes"
        val quoted = MiniJson.quote(original)
        assertEquals("\"Test\\n\\\"quotes\\\"\\tand \\\\slashes\"", quoted)

        val reparsed = MiniJson.parse("""{"val":$quoted}""") as Map<*, *>
        assertEquals(original, reparsed["val"])
    }

    @Test
    fun testPathNavigation() {
        val json = """
        {
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Result text"
              }
            }
          ]
        }
        """.trimIndent()
        val parsed = MiniJson.parse(json)

        val content = MiniJson.path(parsed, "choices", 0, "message", "content")
        assertEquals("Result text", content)

        val nonExistent = MiniJson.path(parsed, "choices", 1, "message", "content")
        assertNull(nonExistent)

        val wrongKey = MiniJson.path(parsed, "wrong", "key")
        assertNull(wrongKey)
    }
}
