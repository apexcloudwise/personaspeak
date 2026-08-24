package biz.pixelperfectstudios.personaspeak.personas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodTest {

    @Test
    fun `default mood is polite`() {
        assertEquals(Mood.Polite, Mood.DEFAULT)
        assertEquals("polite", Mood.DEFAULT.id.value)
    }

    @Test
    fun `mood catalog contains all five product moods`() {
        val ids = Mood.ALL.map { it.id.value }
        assertEquals(listOf("polite", "witty", "blunt", "apologetic", "formal"), ids)
    }

    @Test
    fun `fromId resolves matching mood or defaults to polite`() {
        assertEquals(Mood.Witty, Mood.fromId(MoodId.WITTY))
        assertEquals(Mood.Formal, Mood.fromId(MoodId.FORMAL))
        assertEquals(Mood.Polite, Mood.fromId(MoodId("unknown")))
    }

    @Test
    fun `prompt builder appends mood modifier when provided`() {
        val persona = Persona(
            name = "Test Character",
            context = " (a test character)",
            speechPatterns = listOf("Speaks in tests"),
        )

        val unmoooded = PromptBuilder.build(persona)
        val witty = PromptBuilder.build(persona, Mood.Witty)

        assertTrue(witty.contains(Mood.Witty.promptModifier))
        assertTrue(witty.startsWith("You are a text style-transfer engine."))
        assertTrue(witty.endsWith("Output only the rewritten text — no preamble, no explanation, no quotation marks around it."))
        assertEquals(unmoooded, PromptBuilder.build(persona, null))
    }
}
