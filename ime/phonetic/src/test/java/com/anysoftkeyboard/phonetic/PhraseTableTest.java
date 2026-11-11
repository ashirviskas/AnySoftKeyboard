package com.anysoftkeyboard.phonetic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PhraseTableTest {

  private static List<Syllable> readings(String... syllables) {
    final List<Syllable> list = new ArrayList<>();
    for (String s : syllables) list.add(Syllable.parse(s));
    return list;
  }

  private static List<String> texts(List<Phrase> phrases) {
    final List<String> texts = new ArrayList<>();
    for (Phrase p : phrases) texts.add(p.text);
    return texts;
  }

  @Test
  public void testLookupPhrase() {
    final List<Phrase> out = new ArrayList<>();
    TestTables.zhuyin().lookup(readings("ㄋㄧˇ", "ㄏㄠˇ"), 0, 2, out);
    assertEquals("你好", out.get(0).text);
  }

  @Test
  public void testLookupSingleCharsBestFirst() {
    final List<Phrase> out = new ArrayList<>();
    TestTables.zhuyin().lookup(readings("ㄕˋ"), 0, 1, out);
    assertEquals("是", out.get(0).text);
    for (int i = 1; i < out.size(); i++) {
      assertTrue(out.get(i - 1).score >= out.get(i).score);
    }
  }

  @Test
  public void testToneFiltersAndTonelessMatchesAll() {
    final List<Phrase> toned = new ArrayList<>();
    TestTables.zhuyin().lookup(readings("ㄇㄚˇ"), 0, 1, toned);
    assertTrue(texts(toned).contains("馬"));
    assertFalse(texts(toned).contains("媽"));

    final List<Syllable> toneless = Arrays.asList(Syllable.parse("ㄇㄚ").withTone(0));
    final List<Phrase> all = new ArrayList<>();
    TestTables.zhuyin().lookup(toneless, 0, 1, all);
    assertTrue(texts(all).contains("馬"));
    assertTrue(texts(all).contains("媽"));
  }

  @Test
  public void testNeutralToneMatchesFullToneWithPenalty() {
    // McBopomofo stores 得 in 覺得 with a full tone; typing ˙ must still find the phrase.
    final List<Phrase> out = new ArrayList<>();
    TestTables.zhuyin().lookup(readings("ㄐㄩㄝˊ", "ㄉㄜ˙"), 0, 2, out);
    assertTrue(texts(out).contains("覺得"));
  }

  @Test
  public void testUnknownKey() {
    final List<Phrase> out = new ArrayList<>();
    TestTables.zhuyin().lookup(readings("ㄅㄩ"), 0, 1, out);
    assertTrue(out.isEmpty());
    assertFalse(TestTables.zhuyin().isKnownSyllable(Syllable.parse("ㄅㄩ").base()));
    assertTrue(TestTables.zhuyin().isKnownSyllable(Syllable.parse("ㄕ").base()));
  }

  @Test(expected = IOException.class)
  public void testRejectsGarbage() throws IOException {
    PhraseTable.read(new ByteArrayInputStream(new byte[64]));
  }
}
