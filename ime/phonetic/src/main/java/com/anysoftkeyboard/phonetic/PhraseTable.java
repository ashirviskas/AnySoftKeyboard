/*
 * Copyright (c) 2026 AnySoftKeyboard contributors
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

package com.anysoftkeyboard.phonetic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Read-only phrase table: toneless syllable sequence → phrases with tones and scores.
 *
 * <p>The whole file stays as one {@code byte[]}, about 4 MB, and lookups binary-search it in place,
 * so no object graph is built. Format (big-endian, written by {@code build_zhuyin_phrases.py}):
 *
 * <pre>
 * "ZYP1" | int keyCount | int entryCount | int textChars
 * int[keyCount + 1]  keyOffsets   (into keyBlob, in shorts)
 * int[keyCount + 1]  entryStarts  (into entries)
 * short[...]         keyBlob      (keys sorted by (length, bases))
 * entries[entryCount]: int tones (3 bits per syllable) | int textOffset | byte textLength | byte score
 * char[textChars]    UTF-16 text pool
 * </pre>
 *
 * Entries of a key are sorted best first. {@code score} is {@code round(-ln(p) * 8)}.
 */
public final class PhraseTable {
  private static final int MAGIC = 0x5A595031; // "ZYP1"
  private static final int ENTRY_BYTES = 10;
  private static final float SCORE_SCALE = 8f;

  /** Typing ˙ also matches the dictionary tone, because McBopomofo stores full tones (覺得). */
  static final float NEUTRAL_TONE_MISMATCH_PENALTY = 3f;

  public static final int MAX_PHRASE_SYLLABLES = 6;

  private final ByteBuffer mData;
  private final int mKeyCount;
  private final int mKeyOffsetsPos;
  private final int mEntryStartsPos;
  private final int mKeyBlobPos;
  private final int mEntriesPos;
  private final int mTextPos;

  private PhraseTable(ByteBuffer data) throws IOException {
    mData = data;
    if (data.getInt(0) != MAGIC) throw new IOException("Not a Zhuyin phrase table");
    mKeyCount = data.getInt(4);
    final int entryCount = data.getInt(8);
    final int textChars = data.getInt(12);
    mKeyOffsetsPos = 16;
    mEntryStartsPos = mKeyOffsetsPos + 4 * (mKeyCount + 1);
    mKeyBlobPos = mEntryStartsPos + 4 * (mKeyCount + 1);
    mEntriesPos = mKeyBlobPos + 2 * data.getInt(mKeyOffsetsPos + 4 * mKeyCount);
    mTextPos = mEntriesPos + ENTRY_BYTES * entryCount;
    if (mTextPos + 2 * textChars != data.limit()) {
      throw new IOException("Corrupt Zhuyin phrase table");
    }
  }

  public static PhraseTable read(InputStream in) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream(4 * 1024 * 1024);
    final byte[] buffer = new byte[64 * 1024];
    int read;
    while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
    return new PhraseTable(ByteBuffer.wrap(out.toByteArray()));
  }

  /** True if some phrase is pronounced as this single (toneless) syllable. */
  public boolean isKnownSyllable(int base) {
    return findKey(new int[] {base}, 0, 1) >= 0;
  }

  /**
   * Appends the phrases for {@code readings[from, from + length)} to {@code out}, best first.
   * Syllables without a typed tone match every tone.
   */
  public void lookup(List<Syllable> readings, int from, int length, List<Phrase> out) {
    final int[] bases = new int[length];
    for (int i = 0; i < length; i++) bases[i] = readings.get(from + i).base();
    final int key = findKey(bases, 0, length);
    if (key < 0) return;
    final int firstAdded = out.size();
    boolean penalized = false;
    final int end = mData.getInt(mEntryStartsPos + 4 * (key + 1));
    for (int entry = mData.getInt(mEntryStartsPos + 4 * key); entry < end; entry++) {
      final int pos = mEntriesPos + ENTRY_BYTES * entry;
      final int tones = mData.getInt(pos);
      float penalty = 0f;
      boolean matches = true;
      for (int i = 0; i < length && matches; i++) {
        final int typed = readings.get(from + i).tone();
        final int stored = (tones >>> (3 * i)) & 7;
        if (typed == Syllable.TONE_NONE || typed == stored) continue;
        if (typed == Syllable.TONE_NEUTRAL) {
          penalty += NEUTRAL_TONE_MISMATCH_PENALTY;
        } else {
          matches = false;
        }
      }
      if (!matches) continue;
      penalized |= penalty != 0f;
      final int textOffset = mData.getInt(pos + 4);
      final int textLength = mData.get(pos + 8) & 0xFF;
      final float score = -(mData.get(pos + 9) & 0xFF) / SCORE_SCALE - penalty;
      out.add(new Phrase(text(textOffset, textLength), length, score));
    }
    // Stored order is best first; a tone penalty can break it.
    if (penalized) {
      out.subList(firstAdded, out.size()).sort((a, b) -> Float.compare(b.score, a.score));
    }
  }

  private String text(int offset, int length) {
    final char[] chars = new char[length];
    for (int i = 0; i < length; i++) chars[i] = mData.getChar(mTextPos + 2 * (offset + i));
    return new String(chars);
  }

  private int findKey(int[] bases, int from, int length) {
    int low = 0;
    int high = mKeyCount - 1;
    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int cmp = compareKey(mid, bases, from, length);
      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  private int compareKey(int key, int[] bases, int from, int length) {
    final int start = mData.getInt(mKeyOffsetsPos + 4 * key);
    final int keyLength = mData.getInt(mKeyOffsetsPos + 4 * (key + 1)) - start;
    if (keyLength != length) return Integer.compare(keyLength, length);
    for (int i = 0; i < length; i++) {
      final int stored = mData.getShort(mKeyBlobPos + 2 * (start + i)) & 0xFFFF;
      if (stored != bases[from + i]) return Integer.compare(stored, bases[from + i]);
    }
    return 0;
  }
}
