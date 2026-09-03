/*
 * Copyright (c) 2013 Menny Even-Danan
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
package com.personaspeak.engine.suggest;

import com.personaspeak.engine.dictionaries.WordComposer;
import java.util.List;

/**
 * Engine-narrowed Suggest interface, from ASK's dictionaries.Suggest
 * (Apache-2.0). Dropped from the host-side original: keyboard/addon
 * setup (hosts hand built Dictionary instances to the provider), and
 * quick-text tag search (not prediction-engine core).
 */
public interface Suggest {
  enum AdditionType {
    ALWAYS_ADD(255),
    ADD_IF_IN_DICTIONARY(128),
    ADD_IF_AT_LEAST_2_EDIT_DISTANCE(64);

    private final int mFrequencyDelta;

    AdditionType(int frequencyDelta) {
      mFrequencyDelta = frequencyDelta;
    }

    public int getFrequencyDelta() {
      return mFrequencyDelta;
    }
  }

  void setCorrectionMode(boolean enabledSuggestions, int maxLengthDiff, int maxDistance, boolean splitWords);

  boolean isSuggestionsEnabled();

  void closeDictionaries();

  void setMaxSuggestions(int maxSuggestions);

  void resetNextWordSentence();

  List<CharSequence> getNextSuggestions(CharSequence previousWord, boolean inAllUpperCaseState);

  List<CharSequence> getSuggestions(WordComposer wordComposer);

  int getLastValidSuggestionIndex();

  boolean isValidWord(CharSequence word);

  boolean addWordToUserDictionary(String word);

  void removeWordFromUserDictionary(String word);

  boolean tryToLearnNewWord(CharSequence newWord, AdditionType additionType);

  void setIncognitoMode(boolean incognitoMode);

  boolean isIncognitoMode();

  void destroy();
}
