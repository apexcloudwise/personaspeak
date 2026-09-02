package biz.pixelperfectstudios.personaspeak.ui.reply

import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingMessageStoreTest {

    private fun context(text: String) = IncomingMessageContext(
        sender = "Sam",
        appLabel = "Messages",
        text = text,
    )

    @Test
    fun `put exposes the context through the state flow`() = runTest {
        val store = IncomingMessageStore()

        store.put("key-1", context("hello"))

        assertEquals(mapOf("key-1" to context("hello")), store.state.first())
        assertEquals("hello", store.peekLatest()?.text)
        assertEquals("key-1", store.peekLatestKey())
    }

    @Test
    fun `latest wins per conversation key`() = runTest {
        val store = IncomingMessageStore()

        store.put("key-1", context("first"))
        store.put("key-1", context("second"))

        assertEquals("second", store.peek("key-1")?.text)
        assertEquals(1, store.state.first().size)
    }

    @Test
    fun `putting a key again makes it the newest conversation`() = runTest {
        val store = IncomingMessageStore(capacity = 2)

        store.put("a", context("a"))
        store.put("b", context("b"))
        store.put("a", context("a2")) // refresh a: b is now the oldest
        store.put("c", context("c"))  // evicts b, not a

        assertTrue(store.peek("b") == null)
        assertEquals("a2", store.peek("a")?.text)
        assertEquals("c", store.peek("c")?.text)
    }

    @Test
    fun `lru cap of five by default`() {
        val store = IncomingMessageStore()

        for (i in 1..7) {
            store.put("key-$i", context("msg-$i"))
        }

        val snapshot = store.state.value
        assertEquals(5, snapshot.size)
        assertNull(snapshot["key-1"])
        assertNull(snapshot["key-2"])
        assertEquals("msg-7", snapshot["key-7"]?.text)
        assertEquals("key-7", store.peekLatestKey())
    }

    @Test
    fun `forget removes exactly one conversation`() = runTest {
        val store = IncomingMessageStore()
        store.put("key-1", context("one"))
        store.put("key-2", context("two"))

        store.forget("key-1")

        assertNull(store.peek("key-1"))
        assertEquals("two", store.peek("key-2")?.text)
        assertTrue(store.state.first().keys.contains("key-2"))
    }

    @Test
    fun `clearAll wipes everything`() = runTest {
        val store = IncomingMessageStore()
        store.put("key-1", context("one"))
        store.put("key-2", context("two"))

        store.clearAll()

        assertTrue(store.state.first().isEmpty())
        assertNull(store.peekLatest())
    }

    @Test
    fun `custom capacity is honored`() {
        val store = IncomingMessageStore(capacity = 2)
        store.put("a", context("a"))
        store.put("b", context("b"))
        store.put("c", context("c"))

        assertEquals(2, store.state.value.size)
        assertNull(store.peek("a"))
    }
}
