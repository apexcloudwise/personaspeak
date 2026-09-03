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
package com.personaspeak.engine.dictionaries;

import com.personaspeak.engine.TextUtils;
import java.util.List;

/**
 * The three statics SuggestImpl needs, ported from ASK's utils.IMEUtil
 * (Apache-2.0): Damerau-Levenshtein edit distance with a reusable
 * workspace, duplicate removal, and suggestion-list trimming.
 */
public final class IMEUtil {

  /* Damerau-Levenshtein distance (Optimal String Alignment), O(M) space. */
  public static int editDistance(
      CharSequence lowerCaseWord, final char[] word, final int offset, final int length) {
    return editDistance(lowerCaseWord, word, offset, length, null);
  }

  public static int editDistance(
      CharSequence lowerCaseWord,
      final char[] word,
      final int offset,
      final int length,
      int[] workspace) {
    final int sl = lowerCaseWord.length();
    final int tl = length;

    final int width = tl + 1;
    if (workspace == null || workspace.length < width * 3) {
      workspace = new int[width * 3];
    }

    int prevPrev = 0;
    int prev = width;
    int curr = width * 2;

    for (int j = 0; j <= tl; j++) {
      workspace[prev + j] = j;
    }

    for (int i = 0; i < sl; ++i) {
      workspace[curr + 0] = i + 1;
      final char sc = lowerCaseWord.charAt(i);
      for (int j = 0; j < tl; j++) {
        final char tc = Character.toLowerCase(word[offset + j]);
        final int cost = sc == tc ? 0 : 1;
        int min = workspace[prev + j + 1] + 1; // deletion
        min = Math.min(min, workspace[curr + j] + 1); // insertion
        min = Math.min(min, workspace[prev + j] + cost); // substitution
        workspace[curr + j + 1] = min;

        if (i > 0
            && j > 0
            && sc == Character.toLowerCase(word[offset + j - 1])
            && tc == lowerCaseWord.charAt(i - 1)) {
          int prevPrevVal = workspace[prevPrev + j - 1];
          workspace[curr + j + 1] = Math.min(workspace[curr + j + 1], prevPrevVal + cost);
        }
      }

      int temp = prevPrev;
      prevPrev = prev;
      prev = curr;
      curr = temp;
    }

    return workspace[prev + tl];
  }

  /** Remove duplicate suggestions, keeping each string's first occurrence. */
  public static void removeDupes(final List<CharSequence> suggestions, List<CharSequence> stringsPool) {
    if (suggestions.size() < 2) return;
    int i = 1;
    while (i < suggestions.size()) {
      final CharSequence cur = suggestions.get(i);
      for (int j = 0; j < i; j++) {
        CharSequence previous = suggestions.get(j);
        if (TextUtils.equals(cur, previous)) {
          removeSuggestion(suggestions, i, stringsPool);
          i--;
          break;
        }
      }
      i++;
    }
  }

  public static void tripSuggestions(
      List<CharSequence> suggestions, final int maxSuggestions, List<CharSequence> stringsPool) {
    while (suggestions.size() > maxSuggestions) {
      removeSuggestion(suggestions, maxSuggestions, stringsPool);
    }
  }

  private static void removeSuggestion(
      List<CharSequence> suggestions, int indexToRemove, List<CharSequence> stringsPool) {
    CharSequence garbage = suggestions.remove(indexToRemove);
    if (garbage instanceof StringBuilder) {
      stringsPool.add(garbage);
    }
  }

  private IMEUtil() {}
}
