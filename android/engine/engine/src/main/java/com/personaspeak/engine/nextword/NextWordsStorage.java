package com.personaspeak.engine.nextword;

import com.personaspeak.engine.Log;import java.io.File;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

public class NextWordsStorage {

  private static final String TAG = "NextWordsStorage";
  private final File mStorageDir;
  private final String mNextWordsStorageFilename;

  public NextWordsStorage(File storageDir, String locale) {
    mStorageDir = storageDir;
    mNextWordsStorageFilename = "next_words_" + locale + ".txt";
  }

  private File storageFile() {
    return new File(mStorageDir, mNextWordsStorageFilename);
  }

  public Iterable<NextWordsContainer> loadStoredNextWords() {
    try (final FileInputStream inputStream = new FileInputStream(storageFile())) {
      final int version = inputStream.read();
      if (version < 1) {
        Log.w(TAG, "Failed to read version from file " + mNextWordsStorageFilename);
        return Collections.emptyList();
      }
      final NextWordsFileParser parser;
      switch (version) {
        case 1:
          parser = new NextWordsFileParserV1();
          break;
        default:
          Log.w(TAG, String.format("Version %d is not supported!", version));
          return Collections.emptyList();
      }
      return parser.loadStoredNextWords(inputStream);
    } catch (FileNotFoundException e) {
      Log.w(TAG, "" + e);
      Log.w(
          TAG,
          String.format(
              "Failed to find %s. Maybe it's just the first time.", mNextWordsStorageFilename));
      return Collections.emptyList();
    } catch (IOException e) {
      Log.w(TAG, "" + e);
      Log.w(
          TAG,
          String.format(
              "Failed to open %s. Maybe it's just the first time.", mNextWordsStorageFilename));
      return Collections.emptyList();
    }
  }

  public void storeNextWords(Iterable<NextWordsContainer> nextWords) {
    NextWordsFileParser parser = new NextWordsFileParserV1();
    FileOutputStream outputStream = null;
    try {
      Log.d(TAG, "Storing next-words into " + mNextWordsStorageFilename);
      outputStream = new FileOutputStream(storageFile());
      parser.storeNextWords(nextWords, outputStream);
      outputStream.flush();
    } catch (IOException e) {
      Log.w(TAG, "" + e);
      Log.w(TAG, String.format("Failed to store to %s. Deleting", mNextWordsStorageFilename));
      storageFile().delete();
    } catch (NullPointerException npe) {
      // related to https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/528
      // after reading
      // http://stackoverflow.com/questions/10259421/nullpointerexception-at-openfileoutput-in-activity
      // and
      // https://github.com/android/platform_frameworks_base/blob/android-sdk-4.0.3_r1/core/java/android/app/ContextImpl.java#L614
      // I'm guessing that there is not much I can do here :(
      Log.w(TAG, npe, "");
      Log.w(TAG, String.format("Failed to store to %s with an NPE.", mNextWordsStorageFilename));
    } finally {
      if (outputStream != null)
        try {
          outputStream.close();
        } catch (IOException e) {
          Log.w(TAG, "Failed to close output stream while in finally.", e);
        }
    }
  }
}
