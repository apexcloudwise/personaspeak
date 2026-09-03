/*
 * Copyright (c) 2026 Pixel Perfect Studios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.personaspeak.engine

/**
 * The second narrow host interface (#124): everything SuggestionsProvider
 * read through ASK's RxSharedPrefs, as synchronous properties. The host
 * owns where these live (its own prefs system) and pushes a fresh
 * instance when they change; the engine applies it immediately — the
 * observable-subscription semantics of ASK collapse into "new settings
 * object, applied".
 */
interface EngineSettings {
    val quickFixEnabled: Boolean
    val quickFixSecondDisabled: Boolean

    /** Hosts without a contacts dictionary simply report false. */
    val useContactsDictionary: Boolean

    /** Hosts without a user dictionary simply report false. */
    val useUserDictionary: Boolean

    /**
     * ASK's `settings_key_next_word_suggestion_aggressiveness`:
     * "none" | "minimal" | "minimal_aggressive" | "aggressive".
     */
    val nextWordSuggestionAggressiveness: String

    /** ASK's `settings_key_next_word_dictionary_type`: "read_only" | "read_write". */
    val nextWordDictionaryType: String

    data class Defaults(
        override val quickFixEnabled: Boolean = true,
        override val quickFixSecondDisabled: Boolean = true,
        override val useContactsDictionary: Boolean = false,
        override val useUserDictionary: Boolean = false,
        override val nextWordSuggestionAggressiveness: String = "aggressive",
        override val nextWordDictionaryType: String = "read_write",
    ) : EngineSettings
}
