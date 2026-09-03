package com.personaspeak.engine.dictionaries;

import java.util.ArrayList;
import java.util.Collection;

public class InMemoryDictionary extends BTreeDictionary {

  private final ArrayList<WordFrequency> mWords;

  public InMemoryDictionary(
      String dictionaryName,
      int maxWordsToRead,
      Collection<WordFrequency> words,
      boolean includeTypedWord) {
    super(dictionaryName, maxWordsToRead, includeTypedWord);
    mWords = new ArrayList<>(words);
  }

  @Override
  protected void readWordsFromActualStorage(WordReadListener wordReadListener) {
    for (WordFrequency word : mWords) {
      if (!wordReadListener.onWordRead(word.word, word.frequency)) break;
    }
  }

  @Override
  protected void deleteWordFromStorage(String word) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void addWordToStorage(String word, int frequency) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void closeStorage() {
    mWords.clear();
  }

  /** Engine replacement for androidx.core.util.Pair. */
  public static class WordFrequency {
    public final String word;
    public final Integer frequency;

    public WordFrequency(String word, Integer frequency) {
      this.word = word;
      this.frequency = frequency;
    }
  }
}
