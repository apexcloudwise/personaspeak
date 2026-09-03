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
package com.personaspeak.engine.dictionaries;

import com.personaspeak.engine.EngineCharsets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Loads the AOSP LatinIME "combined" wordlist format (Apache-2.0, the
 * vendored en_wordlist.combined.gz) into an InMemoryDictionary — the
 * engine's end-to-end dictionary path for the #124 spike (criterion 2).
 *
 * <p>Format: an optional header line starting with "dictionary=", then
 * whitespace-indented entries "word=the,f=222,flags=,originalFreq=222".
 * Frequencies are clamped to the engine's 1..255 contract; out-of-range
 * entries are skipped, not guessed.
 */
public final class CombinedWordListLoader {

  public static InMemoryDictionary load(
      InputStream gzInput, String dictionaryName, int maxWordsToRead) throws IOException {
    List<InMemoryDictionary.WordFrequency> words = new ArrayList<>(1 << 18);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new GZIPInputStream(gzInput), EngineCharsets.UTF8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("dictionary=") || line.isEmpty()) continue;
        final String trimmed = line.trim();
        if (!trimmed.startsWith("word=")) continue;
        final String word = fieldValue(trimmed, "word=");
        final String freqText = fieldValue(trimmed, ",f=");
        if (word == null || word.isEmpty() || freqText == null) continue;
        final int frequency;
        try {
          frequency = Integer.parseInt(freqText);
        } catch (NumberFormatException e) {
          continue;
        }
        if (frequency <= 0) continue;
        words.add(
            new InMemoryDictionary.WordFrequency(
                word, Math.min(frequency, 255 /*MAX_WORD_FREQUENCY*/)));
      }
    }
    return new InMemoryDictionary(dictionaryName, maxWordsToRead, words, true);
  }

  private static String fieldValue(String line, String prefix) {
    final int start = line.indexOf(prefix);
    if (start < 0) return null;
    final int from = start + prefix.length();
    final int end = line.indexOf(',', from);
    return end < 0 ? line.substring(from) : line.substring(from, end);
  }

  private CombinedWordListLoader() {}
}
