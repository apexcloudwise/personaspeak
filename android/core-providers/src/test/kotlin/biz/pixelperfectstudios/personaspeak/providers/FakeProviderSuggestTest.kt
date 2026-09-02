package biz.pixelperfectstudios.personaspeak.providers

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeProviderSuggestTest {

    @Test
    fun `suggest returns exactly three distinct replies by default`() = runTest {
        val provider = FakeProvider()

        val result = provider.suggest("system", "Running late, start the tea without me", count = 3)

        assertTrue(result.isSuccess)
        val replies = result.getOrThrow()
        assertEquals(3, replies.size)
        assertEquals(3, replies.toSet().size, "the three registers must produce distinct replies")
        assertTrue(replies.all { it.contains("Running late, start the tea without me") })
    }

    @Test
    fun `suggest is deterministic for the same message`() = runTest {
        val provider = FakeProvider()
        val message = "Running late, start the tea without me"

        val first = provider.suggest("system", message, count = 3).getOrThrow()
        val second = provider.suggest("system", message, count = 3).getOrThrow()

        assertEquals(first, second)
    }

    @Test
    fun `suggest varies across different messages`() = runTest {
        val provider = FakeProvider()

        val forTea = provider.suggest("system", "Running late, start the tea without me", count = 3).getOrThrow()
        val forMovie = provider.suggest("system", "Movie night still on for tonight?", count = 3).getOrThrow()

        assertTrue(forTea != forMovie, "different messages should draft different lines")
    }

    @Test
    fun `suggest caps at count when fewer than three requested`() = runTest {
        val provider = FakeProvider()

        val result = provider.suggest("system", "Ping", count = 1)

        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun `suggest never returns a blank reply`() = runTest {
        val provider = FakeProvider()

        val replies = provider.suggest("system", "Ok.", count = 3).getOrThrow()

        assertTrue(replies.all { it.isNotBlank() })
    }

    @Test
    fun `suggest paces like rewrite at roughly the configured latency`() = runTest {
        val provider = FakeProvider(latencyMs = 400)

        provider.suggest("system", "hello", count = 3)

        assertEquals(400, testScheduler.currentTime)
    }
}
