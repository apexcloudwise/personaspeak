package biz.pixelperfectstudios.personaspeak.ui.reply

import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The RAM-only home for the latest incoming message per conversation
 * (ADR-0011). Written by the notification listener, observed by the reply
 * strip's ViewModel.
 *
 * Boundaries, pinned by tests:
 * - No disk surface of any kind — this file imports no Android types and no
 *   I/O. Content that lives here never leaves RAM.
 * - Latest-wins per conversation key; an LRU cap of [capacity] conversations
 *   evicts the oldest.
 * - [forget] removes one conversation ("forgotten on reply"); [clearAll]
 *   wipes everything (listener disconnected / access revoked).
 */
class IncomingMessageStore(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()

    // Insertion-ordered: re-putting a key moves it to the newest slot, so the
    // last entry is always the most recently posted conversation. Reads do not
    // reorder. Size is capped at [capacity] by eviction on put.
    private val messages = LinkedHashMap<String, IncomingMessageContext>()

    private val _messages = MutableStateFlow<Map<String, IncomingMessageContext>>(emptyMap())

    /** Snapshot of the stored conversations, oldest first, for strip reactivity. */
    val state: StateFlow<Map<String, IncomingMessageContext>> = _messages.asStateFlow()

    /** The most recently posted conversation, or null when the store is empty. */
    fun peekLatest(): IncomingMessageContext? = synchronized(lock) {
        messages.entries.lastOrNull()?.value
    }

    fun peekLatestKey(): String? = synchronized(lock) {
        messages.entries.lastOrNull()?.key
    }

    fun peek(conversationKey: String): IncomingMessageContext? = synchronized(lock) {
        messages[conversationKey]
    }

    fun put(conversationKey: String, context: IncomingMessageContext) {
        val snapshot: Map<String, IncomingMessageContext> = synchronized(lock) {
            messages.remove(conversationKey)
            messages[conversationKey] = context
            while (messages.size > capacity) {
                messages.entries.iterator().let { it.next(); it.remove() }
            }
            messages.toMap()
        }
        _messages.value = snapshot
    }

    fun forget(conversationKey: String) {
        val snapshot: Map<String, IncomingMessageContext> = synchronized(lock) {
            messages.remove(conversationKey)
            messages.toMap()
        }
        _messages.value = snapshot
    }

    fun clearAll() {
        _messages.value = emptyMap()
        synchronized(lock) { messages.clear() }
    }

    companion object {
        const val DEFAULT_CAPACITY = 5

        /**
         * Process-wide instance — same pattern as PersonaSpeakSessionState:
         * the listener and the IME strip share one process, so a singleton is
         * the whole transport. Nothing here can outlive the process.
         */
        val instance = IncomingMessageStore()
    }
}
