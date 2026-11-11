package com.anysoftkeyboard.ime;

import com.anysoftkeyboard.AnySoftKeyboardBaseTest;
import com.anysoftkeyboard.TestableAnySoftKeyboard;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.keyboards.views.CandidatesPanelView;
import com.anysoftkeyboard.phonetic.PhraseTable;
import com.menny.android.anysoftkeyboard.R;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Zhuyin composition through the IME: key routing, field sync, picks and commits. */
public class AnySoftKeyboardPhoneticTest extends AnySoftKeyboardBaseTest {
  private static final String TABLE_PATH =
      "addons/languages/chinese_traditional/pack/src/main/res/raw/zhuyin_phrases";
  private static PhraseTable sTable;

  /** Forces phonetic mode with the real Traditional Chinese table on the default keyboard. */
  public static class PhoneticTestableAnySoftKeyboard extends TestableAnySoftKeyboard {
    @Override
    protected boolean isPhoneticInputMode() {
      return true;
    }

    @Override
    protected PhraseTable getPhoneticTable() {
      return table();
    }
  }

  @Override
  protected Class<? extends TestableAnySoftKeyboard> getServiceClass() {
    return PhoneticTestableAnySoftKeyboard.class;
  }

  private static synchronized PhraseTable table() {
    if (sTable == null) {
      File dir = new File("").getAbsoluteFile();
      while (!new File(dir, TABLE_PATH).exists()) dir = dir.getParentFile();
      try (InputStream in = new FileInputStream(new File(dir, TABLE_PATH))) {
        sTable = PhraseTable.read(in);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return sTable;
  }

  private void type(String bopomofo) {
    bopomofo.codePoints().forEach(cp -> mAnySoftKeyboardUnderTest.simulateKeyPress(cp));
  }

  private String text() {
    return mAnySoftKeyboardUnderTest.getCurrentInputConnectionText();
  }

  private void pick(String candidate) {
    final List<?> shown = verifyAndCaptureSuggestion(false);
    final int index = shown.indexOf(candidate);
    Assert.assertTrue(candidate + " not in " + shown, index >= 0);
    mAnySoftKeyboardUnderTest.pickSuggestionManually(index, candidate);
  }

  @Test
  public void testTypingShowsZhuyinAndOffersConversion() {
    type("ㄋㄧˇㄏㄠˇㄇㄚ˙");
    Assert.assertEquals("ㄋㄧˇㄏㄠˇㄇㄚ˙", text());
    Assert.assertEquals(9, getCurrentTestInputConnection().getCurrentStartPosition());
    Assert.assertEquals("你好嗎", verifyAndCaptureSuggestion(false).get(0).toString());
  }

  @Test
  public void testPendingSyllableIsShownAsTyped() {
    type("ㄋㄧˇㄏㄠ");
    Assert.assertEquals("ㄋㄧˇㄏㄠ", text());
  }

  @Test
  public void testSpaceCommitsThenTypesSpace() {
    type("ㄋㄧˇㄏㄠˇ");
    mAnySoftKeyboardUnderTest.simulateKeyPress(' ');
    Assert.assertEquals("你好", text());
    mAnySoftKeyboardUnderTest.simulateKeyPress(' ');
    Assert.assertEquals("你好 ", text());
  }

  @Test
  public void testEnterCommitsWithoutNewLine() {
    type("ㄋㄧˇ");
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.ENTER);
    Assert.assertEquals("你", text());
  }

  @Test
  public void testPickingHeadPhraseCommitsItAndKeepsComposing() {
    type("ㄋㄧˇㄏㄠˇㄇㄚ˙");
    pick("妳好");
    Assert.assertEquals("妳好ㄇㄚ˙", text());
    // ㄇㄚ˙ is still composing: deleting edits it, not the committed text.
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.DELETE);
    Assert.assertEquals("妳好ㄇㄚ", text());
  }

  @Test
  public void testDeleteEditsSymbolBySymbol() {
    type("ㄋㄧˇ");
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.DELETE);
    Assert.assertEquals("ㄋㄧ", text());
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.DELETE);
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.DELETE);
    Assert.assertEquals("", text());
    // Nothing composing: delete works on the field as usual.
    mAnySoftKeyboardUnderTest.simulateTextTyping("ab");
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.DELETE);
    Assert.assertEquals("a", text());
  }

  @Test
  public void testPunctuationCommitsCompositionFirst() {
    type("ㄋㄧˇㄏㄠˇ");
    mAnySoftKeyboardUnderTest.simulateKeyPress('，');
    Assert.assertEquals("你好，", text());
  }

  @Test
  public void testCursorPickFixesMiddleWord() {
    type("ㄋㄧˇㄏㄠˇㄇㄚ˙");
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.ARROW_LEFT);
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.ARROW_LEFT);
    Assert.assertEquals(3, getCurrentTestInputConnection().getCurrentStartPosition());
    pick("郝");
    Assert.assertEquals("ㄋㄧˇ郝ㄇㄚ˙", text());
    Assert.assertEquals(4, getCurrentTestInputConnection().getCurrentStartPosition());
    mAnySoftKeyboardUnderTest.simulateKeyPress(KeyCodes.ENTER);
    Assert.assertEquals("你郝嗎", text());
  }

  private CandidatesPanelView panel() {
    return mAnySoftKeyboardUnderTest
        .getInputViewContainer()
        .findViewById(R.id.candidates_panel_root);
  }

  @Test
  public void testSwipeUpOpensCandidatesPanelAndPickCommits() {
    type("ㄋㄧˇㄏㄠˇ");
    Assert.assertNull(panel());
    Assert.assertTrue(mAnySoftKeyboardUnderTest.onCandidateStripSwipeUp());
    final CandidatesPanelView panel = panel();
    Assert.assertNotNull(panel);
    Assert.assertEquals(
        android.view.View.GONE,
        ((android.view.View) mAnySoftKeyboardUnderTest.getInputView()).getVisibility());

    // The grid holds every candidate; pick the 2nd one (你).
    final android.view.ViewGroup scroll = (android.view.ViewGroup) panel.getChildAt(1);
    final android.view.ViewGroup grid = (android.view.ViewGroup) scroll.getChildAt(0);
    final android.widget.TextView you = findText(grid, "你");
    you.performClick();

    Assert.assertNull(panel());
    Assert.assertEquals(
        android.view.View.VISIBLE,
        ((android.view.View) mAnySoftKeyboardUnderTest.getInputView()).getVisibility());
    Assert.assertEquals("你ㄏㄠˇ", text());
  }

  @Test
  public void testSwipeUpWithoutCompositionKeepsStripBehavior() {
    Assert.assertFalse(mAnySoftKeyboardUnderTest.onCandidateStripSwipeUp());
    Assert.assertNull(panel());
  }

  @Test
  public void testBackClosesCandidatesPanel() {
    type("ㄋㄧˇ");
    mAnySoftKeyboardUnderTest.onCandidateStripSwipeUp();
    Assert.assertNotNull(panel());
    mAnySoftKeyboardUnderTest.hideWindow();
    Assert.assertNull(panel());
  }

  private static android.widget.TextView findText(android.view.ViewGroup grid, String text) {
    for (int i = 0; i < grid.getChildCount(); i++) {
      final android.widget.TextView item = (android.widget.TextView) grid.getChildAt(i);
      if (item.getText().toString().equals(text)) return item;
    }
    throw new AssertionError(text + " not in the panel");
  }

  @Test
  public void testNonBopomofoWithoutCompositionIsTypedNormally() {
    mAnySoftKeyboardUnderTest.simulateKeyPress('a');
    Assert.assertEquals("a", text());
  }
}
