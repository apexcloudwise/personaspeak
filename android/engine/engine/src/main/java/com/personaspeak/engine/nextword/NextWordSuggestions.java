package com.personaspeak.engine.nextword;


public interface NextWordSuggestions {
  Iterable<String> getNextWords(String currentWord, int maxResults, int minWordUsage);

  void notifyNextTypedWord(String currentWord);

  void resetSentence();
}
