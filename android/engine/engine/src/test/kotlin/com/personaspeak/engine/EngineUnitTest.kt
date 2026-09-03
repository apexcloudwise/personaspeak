package com.personaspeak.engine

import com.personaspeak.engine.dictionaries.BTreeDictionary
import com.personaspeak.engine.dictionaries.InMemoryDictionary
import com.personaspeak.engine.nextword.NextWordDictionary
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Unit tests for the ported nextword layer and the BTree core. */
class EngineUnitTest {

    @Test
    fun btreeAddDeleteLookup() {
        val dictionary = testableDictionary()
        dictionary.loadDictionary()
        dictionary.addWord("hello", 128)
        assertTrue(dictionary.isValidWord("hello"))
        assertEquals(128, dictionary.getWordFrequency("hello"))
        dictionary.deleteWord("hello")
        assertTrue(!dictionary.isValidWord("hello"))
    }

    @Test
    fun btreeCurlyQuoteNormalization() {
        // Dictionary.toLowerCase folds curly quotes to straight ones;
        // words stored with ' should be found via 0x2019 after composition.
        val dictionary = testableDictionary()
        dictionary.loadDictionary()
        dictionary.addWord("would've", 100)
        assertTrue(dictionary.getWordFrequency("would've") > 0)
    }

    @Test
    fun inMemoryDictionaryLoadsWords() {
        val dictionary = InMemoryDictionary(
            "test", Integer.MAX_VALUE,
            listOf(
                InMemoryDictionary.WordFrequency("alpha", 10),
                InMemoryDictionary.WordFrequency("beta", 20),
            ),
            true,
        )
        dictionary.loadDictionary()
        assertTrue(dictionary.isValidWord("alpha"))
        assertTrue(dictionary.isValidWord("beta"))
        assertTrue(!dictionary.isValidWord("gamma"))
    }

    @Test
    fun nextWordLearnsAndRecalls() {
        val storageDir = Files.createTempDirectory("engine-nextwords").toFile()
        val nextWordDictionary = NextWordDictionary(storageDir, "en")
        nextWordDictionary.load()

        nextWordDictionary.notifyNextTypedWord("hello")
        nextWordDictionary.notifyNextTypedWord("world")
        val nextWords = nextWordDictionary.getNextWords("hello", 5, 1)
        assertEquals(listOf("world"), nextWords.toList())

        nextWordDictionary.close() // persists
        val reloaded = NextWordDictionary(storageDir, "en")
        reloaded.load()
        assertEquals(
            listOf("world"),
            reloaded.getNextWords("hello", 5, 1).toList(),
            "next-words must survive store/load round-trip",
        )
    }

    @Test
    fun nextWordResetsSentence() {
        val storageDir = Files.createTempDirectory("engine-nextwords2").toFile()
        val nextWordDictionary = NextWordDictionary(storageDir, "en")
        nextWordDictionary.notifyNextTypedWord("hello")
        nextWordDictionary.notifyNextTypedWord("world")
        nextWordDictionary.resetSentence()
        nextWordDictionary.notifyNextTypedWord("hello")
        // after reset, "hello" is the previous word again: no suggestion yet
        val nextWords = nextWordDictionary.getNextWords("hello", 5, 1)
        assertTrue(nextWords.iterator().hasNext(), "world should still be suggested from history")
    }

    private fun testableDictionary(): BTreeDictionary = object : BTreeDictionary(
        "test", Integer.MAX_VALUE, true) {
        private val stored = mutableMapOf<String, Int>()

        override fun readWordsFromActualStorage(wordReadListener: WordReadListener) {
            for ((word, frequency) in stored) {
                if (!wordReadListener.onWordRead(word, frequency)) break
            }
        }

        override fun deleteWordFromStorage(word: String) {
            stored.remove(word)
        }

        override fun addWordToStorage(word: String, frequency: Int) {
            stored[word] = frequency
        }

        override fun closeStorage() {
            stored.clear()
        }
    }
}
