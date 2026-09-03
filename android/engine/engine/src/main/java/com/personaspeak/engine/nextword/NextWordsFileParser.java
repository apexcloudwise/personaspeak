package com.personaspeak.engine.nextword;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface NextWordsFileParser {
  Iterable<NextWordsContainer> loadStoredNextWords(InputStream inputStream) throws IOException;

  void storeNextWords(Iterable<NextWordsContainer> nextWords, OutputStream outputStream)
      throws IOException;
}
