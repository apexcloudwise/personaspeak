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

import com.personaspeak.engine.EngineSettings;
import com.personaspeak.engine.Log;
import com.personaspeak.engine.TextUtils;
import com.personaspeak.engine.dictionaries.Dictionary;
import com.personaspeak.engine.dictionaries.EditableDictionary;
import com.personaspeak.engine.dictionaries.GetWordsCallback;
import com.personaspeak.engine.dictionaries.KeyCodesProvider;
import com.personaspeak.engine.nextword.NextWordSuggestions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Engine SuggestionsProvider, restructured from ASK's
 * dictionaries.SuggestionsProvider (Apache-2.0) for issue #124:
 *
 * - ASK's RxSharedPrefs subscriptions became the {@link EngineSettings}
 *   interface: the host pushes a fresh settings object, the provider
 *   applies it synchronously.
 * - ASK's addon builders/contacts/autotext/abbreviations/user-dictionary
 *   factories became host-provided inputs: the host hands over built
 *   {@link Dictionary} instances (main dictionaries), plus optional
 *   user/auto dictionaries as storage SPIs.
 * - Dictionary loading is synchronous here; hosts wrap it in their own
 *   threading if they want background loads.
 */
public class SuggestionsProvider {

  private static final String TAG = "SuggestionsProvider";

  static final EditableDictionary NullDictionary =
      new EditableDictionary("NULL") {
        @Override
        public boolean addWord(String word, int frequency) {
          return false;
        }

        @Override
        public void deleteWord(String word) {}

        @Override
        public void getLoadedWords(GetWordsCallback callback) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void getSuggestions(KeyCodesProvider composer, WordCallback callback) {}

        @Override
        public boolean isValidWord(CharSequence word) {
          return false;
        }

        @Override
        protected void closeAllResources() {}

        @Override
        protected void loadAllResources() {}
      };

  static final NextWordSuggestions NULL_NEXT_WORD_SUGGESTIONS =
      new NextWordSuggestions() {
        @Override
        public Iterable<String> getNextWords(String currentWord, int maxResults, int minWordUsage) {
          return Collections.emptyList();
        }

        @Override
        public void notifyNextTypedWord(String currentWord) {}

        @Override
        public void resetSentence() {}
      };

  private final List<Dictionary> mMainDictionary = new ArrayList<>();
  private final List<EditableDictionary> mUserDictionary = new ArrayList<>();
  private final List<NextWordSuggestions> mUserNextWordDictionary = new ArrayList<>();
  private final List<Dictionary> mAbbreviationDictionary = new ArrayList<>();
  private final List<String> mInitialSuggestionsList = new ArrayList<>();

  private boolean mQuickFixesEnabled;
  private boolean mQuickFixesSecondDisabled;
  private boolean mNextWordEnabled;
  private boolean mAlsoSuggestNextPunctuations;
  private int mMaxNextWordSuggestionsCount;
  private int mMinWordUsage;
  private boolean mUserDictionaryEnabled;
  private boolean mContactsDictionaryEnabled;
  private boolean mIncognitoMode;

  private EditableDictionary mAutoDictionary = NullDictionary;
  private Dictionary mContactsDictionary = NullDictionary;
  private NextWordSuggestions mContactsNextWordDictionary = NULL_NEXT_WORD_SUGGESTIONS;

  public SuggestionsProvider(EngineSettings settings) {
    applySettings(settings);
  }

  /** The host pushes settings; applied synchronously (ASK: rx subscriptions). */
  public void applySettings(EngineSettings settings) {
    mQuickFixesEnabled = settings.getQuickFixEnabled();
    mQuickFixesSecondDisabled = settings.getQuickFixSecondDisabled();
    mContactsDictionaryEnabled = settings.getUseContactsDictionary();
    if (!mContactsDictionaryEnabled) {
      mContactsDictionary.close();
      mContactsDictionary = NullDictionary;
      mContactsNextWordDictionary = NULL_NEXT_WORD_SUGGESTIONS;
    }
    mUserDictionaryEnabled = settings.getUseUserDictionary();
    switch (settings.getNextWordSuggestionAggressiveness()) {
      case "medium_aggressiveness":
        mMaxNextWordSuggestionsCount = 5;
        mMinWordUsage = 3;
        break;
      case "maximum_aggressiveness":
        mMaxNextWordSuggestionsCount = 8;
        mMinWordUsage = 1;
        break;
      case "minimal_aggressiveness":
      default:
        mMaxNextWordSuggestionsCount = 3;
        mMinWordUsage = 5;
        break;
    }
    switch (settings.getNextWordDictionaryType()) {
      case "off":
        mNextWordEnabled = false;
        mAlsoSuggestNextPunctuations = false;
        break;
      case "words_punctuations":
        mNextWordEnabled = true;
        mAlsoSuggestNextPunctuations = true;
        break;
      case "read_only":
      case "read_write":
      case "word":
      default:
        mNextWordEnabled = true;
        mAlsoSuggestNextPunctuations = false;
        break;
    }
  }

  /**
   * The host's main dictionaries, replacing ASK's addon-builder factory
   * walk. Loaded synchronously; call from whatever thread the host
   * prefers.
   */
  public void setMainDictionaries(List<Dictionary> dictionaries) {
    for (Dictionary dictionary : mMainDictionary) {
      dictionary.close();
    }
    mMainDictionary.clear();
    mMainDictionary.addAll(dictionaries);
    for (Dictionary dictionary : mMainDictionary) {
      Log.d(TAG, "Loading dictionary %s...", dictionary);
      dictionary.loadDictionary();
    }
  }

  /** Host-provided user dictionary plus its next-word source, if any. */
  public void addUserDictionary(EditableDictionary userDictionary, NextWordSuggestions nextWords) {
    mUserDictionary.add(userDictionary);
    userDictionary.loadDictionary();
    if (nextWords != null) {
      mUserNextWordDictionary.add(nextWords);
    }
  }

  /** Host-provided learning dictionary (ASK: AutoDictionary). */
  public void setAutoDictionary(EditableDictionary autoDictionary) {
    mAutoDictionary = autoDictionary;
    autoDictionary.loadDictionary();
  }

  /**
   * Host-provided contacts dictionary (ASK built its own against the
   * Contacts ContentProvider; the engine ships none).
   */
  public void setContactsDictionary(Dictionary contactsDictionary, NextWordSuggestions nextWords) {
    mContactsDictionary = contactsDictionary;
    mContactsNextWordDictionary = nextWords == null ? NULL_NEXT_WORD_SUGGESTIONS : nextWords;
    contactsDictionary.loadDictionary();
  }

  public void addAbbreviationDictionary(Dictionary abbreviationDictionary) {
    mAbbreviationDictionary.add(abbreviationDictionary);
    abbreviationDictionary.loadDictionary();
  }

  private static boolean allDictionariesIsValid(
      List<? extends Dictionary> dictionaries, CharSequence word) {
    for (Dictionary dictionary : dictionaries) {
      if (dictionary.isValidWord(word)) return true;
    }
    return false;
  }

  private static void allDictionariesGetWords(
      List<? extends Dictionary> dictionaries,
      KeyCodesProvider wordComposer,
      Dictionary.WordCallback wordCallback) {
    for (Dictionary dictionary : dictionaries) {
      dictionary.getSuggestions(wordComposer, wordCallback);
    }
  }

  public void removeWordFromUserDictionary(String word) {
    for (EditableDictionary dictionary : mUserDictionary) {
      dictionary.deleteWord(word);
    }
  }

  public boolean addWordToUserDictionary(String word) {
    if (mIncognitoMode) return false;
    if (mUserDictionary.size() > 0) {
      return mUserDictionary.get(0).addWord(word, 128);
    }
    return false;
  }

  public boolean isValidWord(CharSequence word) {
    if (TextUtils.isEmpty(word)) {
      return false;
    }
    return allDictionariesIsValid(mMainDictionary, word)
        || allDictionariesIsValid(mUserDictionary, word)
        || mContactsDictionary.isValidWord(word);
  }

  public void setIncognitoMode(boolean incognitoMode) {
    mIncognitoMode = incognitoMode;
  }

  public boolean isIncognitoMode() {
    return mIncognitoMode;
  }

  public void close() {
    Log.d(TAG, "closeDictionaries");
    for (Dictionary dictionary : mMainDictionary) {
      dictionary.close();
    }
    for (EditableDictionary dictionary : mUserDictionary) {
      dictionary.close();
    }
    mMainDictionary.clear();
    mAbbreviationDictionary.clear();
    mUserDictionary.clear();
    mUserNextWordDictionary.clear();
    mInitialSuggestionsList.clear();
    resetNextWordSentence();
    mContactsNextWordDictionary = NULL_NEXT_WORD_SUGGESTIONS;
    mAutoDictionary = NullDictionary;
    mContactsDictionary = NullDictionary;
  }

  public void destroy() {
    close();
  }

  public void resetNextWordSentence() {
    for (NextWordSuggestions nextWordSuggestions : mUserNextWordDictionary) {
      nextWordSuggestions.resetSentence();
    }
    mContactsNextWordDictionary.resetSentence();
  }

  public void getSuggestions(KeyCodesProvider wordComposer, Dictionary.WordCallback wordCallback) {
    mContactsDictionary.getSuggestions(wordComposer, wordCallback);
    allDictionariesGetWords(mUserDictionary, wordComposer, wordCallback);
    allDictionariesGetWords(mMainDictionary, wordComposer, wordCallback);
  }

  public void getAbbreviations(
      KeyCodesProvider wordComposer, Dictionary.WordCallback wordCallback) {
    allDictionariesGetWords(mAbbreviationDictionary, wordComposer, wordCallback);
  }

  /** Quick-fix autotext is a host-provided surface (ASK: xml autotext packs). No-op default. */
  public void getAutoText(KeyCodesProvider wordComposer, Dictionary.WordCallback wordCallback) {}

  public void getNextWords(
      String currentWord, Collection<CharSequence> suggestionsHolder, int maxSuggestions) {
    if (!mNextWordEnabled) return;

    allDictionariesGetNextWord(mUserNextWordDictionary, currentWord, suggestionsHolder, maxSuggestions);
    maxSuggestions = maxSuggestions - suggestionsHolder.size();
    if (maxSuggestions == 0) return;

    for (String nextWordSuggestion :
        mContactsNextWordDictionary.getNextWords(
            currentWord, mMaxNextWordSuggestionsCount, mMinWordUsage)) {
      suggestionsHolder.add(nextWordSuggestion);
      maxSuggestions--;
      if (maxSuggestions == 0) return;
    }

    if (mAlsoSuggestNextPunctuations) {
      for (String evenMoreSuggestions : mInitialSuggestionsList) {
        suggestionsHolder.add(evenMoreSuggestions);
        maxSuggestions--;
        if (maxSuggestions == 0) return;
      }
    }
  }

  private void allDictionariesGetNextWord(
      List<NextWordSuggestions> nextWordDictionaries,
      String currentWord,
      Collection<CharSequence> suggestionsHolder,
      int maxSuggestions) {
    for (NextWordSuggestions nextWordDictionary : nextWordDictionaries) {
      if (!mIncognitoMode) nextWordDictionary.notifyNextTypedWord(currentWord);
      for (String nextWordSuggestion :
          nextWordDictionary.getNextWords(
              currentWord, mMaxNextWordSuggestionsCount, mMinWordUsage)) {
        suggestionsHolder.add(nextWordSuggestion);
        maxSuggestions--;
        if (maxSuggestions == 0) return;
      }
    }
  }

  public boolean tryToLearnNewWord(CharSequence newWord, int frequencyDelta) {
    if (mIncognitoMode || !mNextWordEnabled) return false;
    if (!isValidWord(newWord)) {
      return mAutoDictionary.addWord(newWord.toString(), frequencyDelta);
    }
    return false;
  }
}
