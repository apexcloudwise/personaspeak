package com.personaspeak.engine.dictionaries;


/** Interface used from JNI to javaland. Must never be removed or renamed with R8. */
public interface GetWordsCallback {
  void onGetWordsFinished(char[][] words, int[] frequencies);
}
