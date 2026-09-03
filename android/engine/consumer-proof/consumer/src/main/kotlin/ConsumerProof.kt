package com.personaspeak.engine.consumer

import com.personaspeak.engine.dictionaries.CombinedWordListLoader
import com.personaspeak.engine.dictionaries.WordComposer
import com.personaspeak.engine.suggest.SuggestImpl
import com.personaspeak.engine.suggest.SuggestionsProvider
import java.io.File
import java.io.FileInputStream

/**
 * Consumption proof: resolves the engine artifact via composite build
 * and drives the full stack. Prints PROOF-LINE on success.
 */
fun main() {
    // Gradle's run task works from this project dir (engine/consumer-proof/consumer).
    val wordlist =
        File("../../../keyboard/addons/languages/english/pack/dictionary/en_wordlist.combined.gz")
    val provider = SuggestionsProvider(com.personaspeak.engine.EngineSettings.Defaults())
    val dictionary = CombinedWordListLoader.load(FileInputStream(wordlist), "english", 200_000)
    provider.setMainDictionaries(listOf(dictionary))

    val suggest = SuggestImpl(provider)
    suggest.setCorrectionMode(true, 1, 1, false)

    // Corrections flow through nearby-codes reachability + edit-distance
    // scoring: "teh" reaches "the" when the caller supplies nearby codes
    // the way a keyboard's proximity detector does.
    val composer = WordComposer()
    composer.add('t'.code, intArrayOf('t'.code))
    composer.add('e'.code, intArrayOf('e'.code, 'h'.code))
    composer.add('h'.code, intArrayOf('h'.code, 'e'.code))
    val suggestions = suggest.getSuggestions(composer)

    check("the" in suggestions.map { it.toString() }) { "no typo fix: $suggestions" }
    println("PROOF-LINE: engine artifact consumed via composite build; teh -> the OK")
}
