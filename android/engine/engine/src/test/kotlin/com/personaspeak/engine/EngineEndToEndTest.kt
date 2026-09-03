package com.personaspeak.engine

import com.personaspeak.engine.dictionaries.CombinedWordListLoader
import com.personaspeak.engine.dictionaries.Dictionary
import com.personaspeak.engine.dictionaries.WordComposer
import com.personaspeak.engine.suggest.SuggestImpl
import com.personaspeak.engine.suggest.SuggestionsProvider
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Criterion 2, end-to-end: the vendored AOSP LatinIME English wordlist
 * (Apache-2.0, license-gated in the ASK tree) loaded through the engine
 * into the pure-Kotlin trie, driven through the real SuggestImpl
 * orchestration — typo correction and completions over 165k real words.
 *
 * The wordlist is NOT copied into this module (no duplicate licensed
 * data): the test reads the vendored file by relative path and skips
 * (with a loud message) when run outside the repo checkout.
 */
class EngineEndToEndTest {

    private val wordlist: File =
        File("../../keyboard/addons/languages/english/pack/dictionary/en_wordlist.combined.gz")

    private fun engineDictionaryOrFail(): Boolean {
        check(wordlist.isFile) {
            "vendored AOSP wordlist not found at $wordlist — the engine tests require the repo checkout"
        }
        return true
    }

    @Test
    fun wordlistLoadsEndToEnd() {
        if (!engineDictionaryOrFail()) return
        val dictionary = CombinedWordListLoader.load(
            FileInputStream(wordlist), "english", 200_000)
        dictionary.loadDictionary()
        assertTrue(dictionary.isValidWord("hello"))
        assertTrue(dictionary.isValidWord("the"))
        assertTrue(!dictionary.isValidWord("qqqqzzz"))
    }

    @Test
    fun completionsThroughSuggestImpl() {
        if (!engineDictionaryOrFail()) return
        val provider = SuggestionsProvider(EngineSettings.Defaults())
        val dictionary = CombinedWordListLoader.load(
            FileInputStream(wordlist), "english", 200_000)
        provider.setMainDictionaries(listOf(dictionary))

        val suggest = SuggestImpl(provider)
        suggest.setCorrectionMode(true, 1, 1, false)

        val composer = WordComposer()
        composer.simulateTypedWord("hel")

        val suggestions = suggest.getSuggestions(composer)
        assertTrue(suggestions.size > 1, "expected completions for 'hel', got $suggestions")
        assertTrue(
            suggestions.any { it.toString() == "hello" || it.toString() == "help" },
            "expected hello/help among $suggestions",
        )
    }

    @Test
    fun typoCorrectionThroughSuggestImpl() {
        if (!engineDictionaryOrFail()) return
        val provider = SuggestionsProvider(EngineSettings.Defaults())
        val dictionary = CombinedWordListLoader.load(
            FileInputStream(wordlist), "english", 200_000)
        provider.setMainDictionaries(listOf(dictionary))

        val suggest = SuggestImpl(provider)
        suggest.setCorrectionMode(true, 1, 1, false)

        // Corrections flow through nearby-codes reachability: the typed 'e'
        // carries 'h' as a nearby code (as a keyboard's proximity detector
        // would supply), making "the" reachable from the trie, and the
        // edit-distance scorer then ranks it as a fix.
        val composer = WordComposer()
        composer.add('t'.code, intArrayOf('t'.code))
        composer.add('e'.code, intArrayOf('e'.code, 'h'.code))
        composer.add('h'.code, intArrayOf('h'.code, 'e'.code))

        val suggestions = suggest.getSuggestions(composer)
        assertTrue(
            suggestions.any { it.toString() == "the" },
            "expected the-typo-fix among $suggestions",
        )
        assertTrue(suggest.lastValidSuggestionIndex > 0, "expected a valid correction index")
    }

    @Test
    fun typedWordIsFirstAndValid() {
        if (!engineDictionaryOrFail()) return
        val provider = SuggestionsProvider(EngineSettings.Defaults())
        val dictionary = CombinedWordListLoader.load(
            FileInputStream(wordlist), "english", 200_000)
        provider.setMainDictionaries(listOf(dictionary))
        val suggest = SuggestImpl(provider)
        suggest.setCorrectionMode(true, 1, 1, false)

        val composer = WordComposer()
        composer.simulateTypedWord("hello")

        val suggestions = suggest.getSuggestions(composer)
        assertEquals("hello", suggestions[0].toString())
        assertEquals(0, suggest.lastValidSuggestionIndex)
    }

    @Test
    fun dictionaryContract() {
        if (!engineDictionaryOrFail()) return
        val dictionary = CombinedWordListLoader.load(
            FileInputStream(wordlist), "english", 200_000)
        dictionary.loadDictionary()
        assertEquals(Dictionary.MAX_WORD_LENGTH.toLong(), 32L)
        assertTrue(dictionary.wordFrequency("the") > 0)
        assertTrue(dictionary.wordFrequency("qqqqzzz") == 0)
    }

    private fun Dictionary.wordFrequency(word: String): Int {
        // BTreeDictionary exposes getWordFrequency; the base type does not,
        // so route through the concrete class.
        return (this as com.personaspeak.engine.dictionaries.BTreeDictionary)
            .getWordFrequency(word)
    }
}
