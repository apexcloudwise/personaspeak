package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberedSuggestionsTest {

    @Test
    fun `parses clean numbered lines`() {
        val result = NumberedSuggestions.parse("1. First reply\n2. Second reply\n3. Third reply", count = 3)

        assertTrue(result.isSuccess)
        assertEquals(listOf("First reply", "Second reply", "Third reply"), result.getOrNull())
    }

    @Test
    fun `parses decorated numbering a real model might emit`() {
        val completion = "1) First\n\n- Second\n* Third\n• Fourth"
        val result = NumberedSuggestions.parse(completion, count = 4)

        assertTrue(result.isSuccess)
        assertEquals(listOf("First", "Second", "Third", "Fourth"), result.getOrNull())
    }

    @Test
    fun `keeps unnumbered lines as replies`() {
        val result = NumberedSuggestions.parse("First\n2. Second", count = 3)

        assertTrue(result.isSuccess)
        assertEquals(listOf("First", "Second"), result.getOrNull())
    }

    @Test
    fun `caps the reply list at count`() {
        val result = NumberedSuggestions.parse("1. A\n2. B\n3. C\n4. D", count = 3)

        assertTrue(result.isSuccess)
        assertEquals(listOf("A", "B", "C"), result.getOrNull())
    }

    @Test
    fun `succeeds with fewer than count parseable lines`() {
        val result = NumberedSuggestions.parse("1. Only one", count = 3)

        assertTrue(result.isSuccess)
        assertEquals(listOf("Only one"), result.getOrNull())
    }

    @Test
    fun `fails on an empty completion`() {
        val result = NumberedSuggestions.parse("", count = 3)

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails on a completion with no parseable lines`() {
        val result = NumberedSuggestions.parse("   \n \n", count = 3)

        assertTrue(result.isFailure)
    }

    @Test
    fun `a line that is only a marker is dropped not kept as an empty reply`() {
        val result = NumberedSuggestions.parse("1.\n2. Real reply", count = 3)

        assertTrue(result.isSuccess)
        assertEquals(listOf("Real reply"), result.getOrNull())
    }

    @Test
    fun `count below one is a programming error`() {
        val error = kotlin.runCatching { NumberedSuggestions.parse("1. x", count = 0) }

        assertTrue(error.isFailure)
    }
}
