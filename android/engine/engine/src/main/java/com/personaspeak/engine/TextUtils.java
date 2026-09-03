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
package com.personaspeak.engine;

/**
 * Pure-JVM shim for android.text.TextUtils, covering exactly the
 * methods the ported engine code calls. Semantics match Android's.
 */
public final class TextUtils {
  public static boolean isEmpty(CharSequence s) {
    return s == null || s.length() == 0;
  }

  public static boolean equals(CharSequence a, CharSequence b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    final int length = a.length();
    if (length != b.length()) return false;
    for (int i = 0; i < length; i++) {
      if (a.charAt(i) != b.charAt(i)) return false;
    }
    return true;
  }

  private TextUtils() {}
}
