package com.anysoftkeyboard.ui.settings;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.RobolectricFragmentTestCase;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.keyboards.CustomLayout;
import com.anysoftkeyboard.keyboards.KeyboardAddOnAndBuilder;
import com.anysoftkeyboard.keyboards.KeyboardFactory;
import com.anysoftkeyboard.keyboards.views.CustomLayoutEditorView;
import com.anysoftkeyboard.rx.TestRxSchedulers;
import com.google.android.material.tabs.TabLayout;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.R;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.fakes.RoboMenuItem;

public class CustomLayoutEditorFragmentTest
    extends RobolectricFragmentTestCase<KeyboardAddOnBrowserFragment> {

  @Override
  protected int getStartFragmentNavigationId() {
    return R.id.keyboardAddOnBrowserFragment;
  }

  private static KeyboardFactory factory() {
    return AnyApplication.getKeyboardFactory(getApplicationContext());
  }

  private CustomLayoutEditorFragment openEditorFor(String keyboardId) {
    final KeyboardAddOnBrowserFragment browser = startFragment();
    browser.onEditAddOnClicked(factory().getAddOnById(keyboardId));
    TestRxSchedulers.drainAllTasks();
    return (CustomLayoutEditorFragment) getCurrentFragment();
  }

  private CustomLayoutEditorFragment openEditorForEnglish() {
    return openEditorFor(factory().getEnabledAddOn().getId());
  }

  private static CustomLayoutEditorView keyboardView(CustomLayoutEditorFragment editor) {
    return editor.requireView().findViewById(R.id.custom_layout_keyboard);
  }

  /** The chip that offers the key {@code code}, or null when it is not offered. */
  private static View chipFor(CustomLayoutEditorFragment editor, int code) {
    for (int groupId :
        new int[] {R.id.custom_layout_usual_keys, R.id.custom_layout_keyboard_keys}) {
      final ViewGroup group = editor.requireView().findViewById(groupId);
      for (int index = 0; index < group.getChildCount(); index++) {
        if (Integer.valueOf(code).equals(group.getChildAt(index).getTag())) {
          return group.getChildAt(index);
        }
      }
    }
    return null;
  }

  private static CustomLayout layoutOf(CustomLayoutEditorView view) {
    return view.getKeyboard().getCustomLayout();
  }

  private static void touch(CustomLayoutEditorView view, int action, float x, float y) {
    final long now = SystemClock.uptimeMillis();
    final MotionEvent event =
        MotionEvent.obtain(
            now, now, action, x + view.getPaddingLeft(), y + view.getPaddingTop(), 0);
    view.onTouchEvent(event);
    event.recycle();
  }

  private static void tap(CustomLayoutEditorView view, int row, int key) {
    final Rect slot = view.getKeyboard().getCustomLayoutSlots()[row][key];
    touch(view, MotionEvent.ACTION_DOWN, slot.centerX(), slot.centerY());
    touch(view, MotionEvent.ACTION_UP, slot.centerX(), slot.centerY());
  }

  private static void select(CustomLayoutEditorFragment editor, int menuItemId) {
    editor.onOptionsItemSelected(new RoboMenuItem(menuItemId));
    TestRxSchedulers.drainAllTasks();
  }

  private static void click(CustomLayoutEditorFragment editor, int viewId) {
    editor.requireView().findViewById(viewId).performClick();
    TestRxSchedulers.drainAllTasks();
  }

  private static int indexOf(CustomLayout.Row row, int code) {
    for (int index = 0; index < row.keys.size(); index++) {
      if (row.keys.get(index).code == code) return index;
    }
    return -1;
  }

  @Test
  public void testKeyboardWithLayoutsOfTheUserBecomesOneCardThatOpensThem() {
    final KeyboardAddOnAndBuilder english = factory().getEnabledAddOn();
    KeyboardAddOnBrowserFragment browser = startFragment();
    final int cards = ((RecyclerView) browser.requireView()).getAdapter().getItemCount();
    // the keyboards, the card to make a new one, the market search
    Assert.assertEquals(factory().getAllAddOns().size() + 2, cards);
    Assert.assertTrue(browser.canCreateAddOns());
    Assert.assertEquals("", browser.getEditLabel(english).toString());
    Assert.assertFalse(browser.onAddOnClicked(english));

    browser.onEditAddOnClicked(english);
    TestRxSchedulers.drainAllTasks();
    select((CustomLayoutEditorFragment) getCurrentFragment(), R.id.custom_layout_save);
    final CustomLayout stored = factory().getCustomLayouts().get(0);
    Assert.assertEquals(english.getId(), factory().getAddOnById(stored.id).getLayoutGroupId());
    Assert.assertEquals(2, factory().getLayoutsOf(english.getId()).size());

    // still one card for English; it stands for both layouts and opens them
    browser = (KeyboardAddOnBrowserFragment) getCurrentFragment();
    Assert.assertEquals(cards, ((RecyclerView) browser.requireView()).getAdapter().getItemCount());
    Assert.assertEquals("2 layouts", browser.getEditLabel(english).toString());
    factory().setAddOnEnabled(english.getId(), false);
    Assert.assertTrue(browser.isShownAsEnabled(english));
    Assert.assertTrue(browser.onAddOnClicked(english));
    TestRxSchedulers.drainAllTasks();

    // the layouts of English: the two, and the card to make another
    final KeyboardAddOnBrowserFragment layouts =
        (KeyboardAddOnBrowserFragment) getCurrentFragment();
    Assert.assertNotSame(browser, layouts);
    Assert.assertEquals(3, ((RecyclerView) layouts.requireView()).getAdapter().getItemCount());
    Assert.assertTrue(layouts.canCreateAddOns());
    Assert.assertFalse(layouts.isShownAsEnabled(english));
    Assert.assertTrue(layouts.isShownAsEnabled(factory().getAddOnById(stored.id)));
    Assert.assertFalse(layouts.onAddOnClicked(english));
    Assert.assertEquals("", layouts.getEditLabel(english).toString());
  }

  @Test
  public void testStartsAsACopyOfTheKeyboard() {
    final KeyboardAddOnAndBuilder english = factory().getEnabledAddOn();
    final CustomLayoutEditorFragment editor = openEditorForEnglish();

    final CustomLayout layout = layoutOf(keyboardView(editor));
    Assert.assertEquals(english.getId(), layout.baseId);
    Assert.assertEquals(5, layout.rows.size());
    Assert.assertEquals(
        "My " + english.getName(),
        ((EditText) editor.requireView().findViewById(R.id.custom_layout_name))
            .getText()
            .toString());
    // nothing is stored before the user saves
    Assert.assertTrue(factory().getCustomLayouts().isEmpty());
    // every key is offered, also the ones the layout has: a key can be there twice
    Assert.assertNotNull(chipFor(editor, KeyCodes.UNDO));
    Assert.assertNotNull(chipFor(editor, CustomLayout.GAP));
    Assert.assertNotNull(chipFor(editor, KeyCodes.SPACE));
    Assert.assertNotNull(chipFor(editor, 'q'));
  }

  @Test
  public void testRemovedLetterCanBePutBack() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    Assert.assertEquals('q', layoutOf(keyboard).rows.get(1).keys.get(0).code);

    tap(keyboard, 1, 0);
    Assert.assertEquals(1, keyboard.getSelectedRow());
    Assert.assertEquals(0, keyboard.getSelectedKey());
    editor.requireView().findViewById(R.id.custom_layout_remove_key).performClick();
    Assert.assertFalse(layoutOf(keyboard).contains('q'));

    // it is still among the keys; a new gap beside a key takes it
    Assert.assertNotNull(chipFor(editor, 'q'));
    tap(keyboard, 1, 3);
    click(editor, R.id.custom_layout_insert_right);
    Assert.assertEquals(4, keyboard.getSelectedKey());
    Assert.assertEquals(CustomLayout.GAP, layoutOf(keyboard).rows.get(1).keys.get(4).code);
    chipFor(editor, 'q').performClick();

    Assert.assertEquals(4, indexOf(layoutOf(keyboard).rows.get(1), 'q'));
    Assert.assertEquals(4, keyboard.getSelectedKey());
    // and once more, on the other side: a key can be there twice
    click(editor, R.id.custom_layout_insert_left);
    Assert.assertEquals(4, keyboard.getSelectedKey());
    chipFor(editor, 'q').performClick();
    Assert.assertEquals('q', layoutOf(keyboard).rows.get(1).keys.get(4).code);
    Assert.assertEquals('q', layoutOf(keyboard).rows.get(1).keys.get(5).code);
  }

  @Test
  public void testAKeyFromTheListsReplacesThePickedKey() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final float width = layoutOf(keyboard).rows.get(1).keys.get(0).width;

    tap(keyboard, 1, 0);
    chipFor(editor, KeyCodes.UNDO).performClick();

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(10, row.keys.size());
    Assert.assertEquals(KeyCodes.UNDO, row.keys.get(0).code);
    Assert.assertEquals(width, row.keys.get(0).width, 0.001f);
    Assert.assertFalse(layoutOf(keyboard).contains('q'));
    Assert.assertEquals(0, keyboard.getSelectedKey());
  }

  @Test
  public void testWithNothingPickedAKeyIsAddedToTheBottomRow() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final int bottom = layoutOf(keyboard).rows.size() - 1;
    final int keys = layoutOf(keyboard).rows.get(bottom).keys.size();
    chipFor(editor, KeyCodes.UNDO).performClick();
    Assert.assertEquals(keys + 1, layoutOf(keyboard).rows.get(bottom).keys.size());
    Assert.assertEquals(KeyCodes.UNDO, layoutOf(keyboard).rows.get(bottom).keys.get(keys).code);
    Assert.assertEquals(bottom, keyboard.getSelectedRow());
    Assert.assertEquals(keys, keyboard.getSelectedKey());
  }

  @Test
  public void testSeveralKeysCanBePickedEvenedAndRemoved() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    click(editor, R.id.custom_layout_pick_several);
    tap(keyboard, 1, 0);
    tap(keyboard, 1, 1);
    tap(keyboard, 1, 2);
    tap(keyboard, 1, 5);
    tap(keyboard, 1, 5);
    Assert.assertEquals(java.util.Arrays.asList(0, 1, 2), keyboard.getSelectedKeys());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());

    layoutOf(keyboard).rows.get(1).moveEdge(0, 15f);
    click(editor, R.id.custom_layout_even_row);
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    for (int index = 0; index < 3; index++) {
      Assert.assertEquals(10f, row.keys.get(index).width, 0.05f);
    }

    click(editor, R.id.custom_layout_remove_key);
    Assert.assertEquals(7, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertEquals('r', layoutOf(keyboard).rows.get(1).keys.get(0).code);
    Assert.assertTrue(keyboard.getSelectedKeys().isEmpty());
  }

  @Test
  public void testResizingSharesTheRoomWithTheWholeSideToBeginWith() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 0);
    final Rect slot = keyboard.getKeyboard().getCustomLayoutSlots()[1][0];
    final float step = keyboard.getKeyboard().getMinWidth() * CustomLayout.WIDTH_STEP / 100f;
    touch(keyboard, MotionEvent.ACTION_DOWN, slot.right, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, slot.right + step, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, slot.right + step, slot.centerY());

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(15f, row.keys.get(0).width, 0.3f);
    for (int index = 1; index < 10; index++) {
      Assert.assertEquals(85f / 9, row.keys.get(index).width, 0.3f);
    }
  }

  @Test
  public void testATypedEdgeIsTakenInFineSteps() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    click(editor, R.id.custom_layout_resize_evens_side);
    tap(keyboard, 1, 1);
    final EditText right = editor.requireView().findViewById(R.id.custom_layout_right_edge_value);
    Assert.assertEquals("20.0", right.getText().toString());
    Assert.assertEquals(
        "10.0",
        ((EditText) editor.requireView().findViewById(R.id.custom_layout_left_edge_value))
            .getText()
            .toString());
    right.setText("23.4");
    right.onEditorAction(EditorInfo.IME_ACTION_DONE);

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(13.4f, row.keys.get(1).width, 0.06f);
    Assert.assertEquals(6.6f, row.keys.get(2).width, 0.06f);
    Assert.assertEquals(10f, row.keys.get(3).width, 0.06f);
    Assert.assertEquals("23.4", right.getText().toString());
  }

  @Test
  public void testADragAlongARowStaysInItAndAnEmptiedRowStays() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    Rect[][] slots = keyboard.getKeyboard().getCustomLayoutSlots();
    // a drag that wanders a quarter of a row down on its way
    final Rect from = slots[1][0];
    final Rect to = slots[1][3];
    final float wander = from.height() / 4f;
    touch(keyboard, MotionEvent.ACTION_DOWN, from.centerX(), from.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, from.centerX() + 80, from.bottom + wander);
    touch(keyboard, MotionEvent.ACTION_MOVE, to.left + 2, to.bottom + wander);
    touch(keyboard, MotionEvent.ACTION_UP, to.left + 2, to.bottom + wander);
    Assert.assertEquals(10, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertEquals(2, indexOf(layoutOf(keyboard).rows.get(1), 'q'));

    // the only entry of a row dragged to another row: the row is still there
    tap(keyboard, 1, 0);
    click(editor, R.id.custom_layout_add_row_above);
    Assert.assertEquals(6, layoutOf(keyboard).rows.size());
    Assert.assertEquals(1, keyboard.getSelectedRow());
    Assert.assertEquals(1, layoutOf(keyboard).rows.get(1).keys.size());
    slots = keyboard.getKeyboard().getCustomLayoutSlots();
    final Rect gap = slots[1][0];
    final Rect target = slots[3][2];
    touch(keyboard, MotionEvent.ACTION_DOWN, gap.centerX(), gap.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, gap.centerX(), gap.centerY() + 90);
    touch(keyboard, MotionEvent.ACTION_MOVE, target.left + 2, target.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, target.left + 2, target.centerY());
    Assert.assertEquals(6, layoutOf(keyboard).rows.size());
    Assert.assertEquals(1, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertEquals(CustomLayout.GAP, layoutOf(keyboard).rows.get(1).keys.get(0).code);
    Assert.assertEquals(3, keyboard.getSelectedRow());
  }

  @Test
  public void testALayoutGoesThroughTheClipboard() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    select(editor, R.id.custom_layout_copy);
    tap(keyboard, 1, 0);
    click(editor, R.id.custom_layout_remove_key);
    Assert.assertFalse(layoutOf(keyboard).contains('q'));

    select(editor, R.id.custom_layout_paste);
    Assert.assertEquals('q', layoutOf(keyboard).rows.get(1).keys.get(0).code);
    // pasting can be undone like any change
    select(editor, R.id.custom_layout_undo);
    Assert.assertFalse(layoutOf(keyboard).contains('q'));

    // what is not a layout is refused and changes nothing
    ((android.content.ClipboardManager)
            getApplicationContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE))
        .setPrimaryClip(android.content.ClipData.newPlainText("", "hello"));
    select(editor, R.id.custom_layout_paste);
    Assert.assertFalse(layoutOf(keyboard).contains('q'));
    Assert.assertEquals(5, layoutOf(keyboard).rows.size());
  }

  @Test
  public void testDragMovesAKeyToAnotherRow() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final Rect[][] slots = keyboard.getKeyboard().getCustomLayoutSlots();
    final Rect from = slots[1][0];
    final Rect to = slots[2][2];
    final int secondRowKeys = layoutOf(keyboard).rows.get(2).keys.size();

    touch(keyboard, MotionEvent.ACTION_DOWN, from.centerX(), from.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, from.centerX() + 60, from.centerY() + 60);
    touch(keyboard, MotionEvent.ACTION_MOVE, to.left + 2, to.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, to.left + 2, to.centerY());

    final CustomLayout layout = layoutOf(keyboard);
    Assert.assertEquals(-1, indexOf(layout.rows.get(1), 'q'));
    Assert.assertEquals(secondRowKeys + 1, layout.rows.get(2).keys.size());
    Assert.assertEquals(2, keyboard.getSelectedRow());
    Assert.assertEquals('q', layout.rows.get(2).keys.get(keyboard.getSelectedKey()).code);
    Assert.assertEquals(100f, layout.rows.get(1).totalWidth(), 0.5f);
    Assert.assertEquals(100f, layout.rows.get(2).totalWidth(), 0.5f);
  }

  @Test
  public void testDragOfAnEdgeResizesThePickedKey() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final int bottom = layoutOf(keyboard).rows.size() - 1;
    final int space = indexOf(layoutOf(keyboard).rows.get(bottom), KeyCodes.SPACE);
    final float spaceWidth = layoutOf(keyboard).rows.get(bottom).keys.get(space).width;
    final float nextWidth = layoutOf(keyboard).rows.get(bottom).keys.get(space + 1).width;

    // with the neighbour only
    click(editor, R.id.custom_layout_resize_evens_side);
    tap(keyboard, bottom, space);
    final Rect slot = keyboard.getKeyboard().getCustomLayoutSlots()[bottom][space];
    final float step = keyboard.getKeyboard().getMinWidth() * CustomLayout.WIDTH_STEP / 100f;
    touch(keyboard, MotionEvent.ACTION_DOWN, slot.right, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, slot.right - step, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, slot.right - step, slot.centerY());

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(bottom);
    Assert.assertEquals(spaceWidth - CustomLayout.WIDTH_STEP, row.keys.get(space).width, 0.6f);
    Assert.assertEquals(nextWidth + CustomLayout.WIDTH_STEP, row.keys.get(space + 1).width, 0.6f);
    Assert.assertEquals(100f, row.totalWidth(), 0.5f);
  }

  @Test
  public void testRowsCanBeAddedAndRemoved() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 0);
    editor.requireView().findViewById(R.id.custom_layout_add_row).performClick();
    Assert.assertEquals(6, layoutOf(keyboard).rows.size());
    Assert.assertEquals(2, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayout.GAP, layoutOf(keyboard).rows.get(2).keys.get(0).code);

    editor.requireView().findViewById(R.id.custom_layout_remove_row).performClick();
    Assert.assertEquals(5, layoutOf(keyboard).rows.size());
  }

  @Test
  public void testSizeTabListsRowsAndSetsHeights() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final TabLayout tabs = editor.requireView().findViewById(R.id.custom_layout_tabs);
    tabs.selectTab(tabs.getTabAt(1));
    TestRxSchedulers.drainAllTasks();

    final ViewGroup rows = editor.requireView().findViewById(R.id.custom_layout_size_rows);
    // "all letter rows" and the five rows
    Assert.assertEquals(6, rows.getChildCount());
    final int heightBefore = keyboard.getKeyboard().getHeight();

    // picking a row on the keyboard opens its slider
    tap(keyboard, 2, 0);
    Assert.assertEquals(2, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());
    final ViewGroup picked = (ViewGroup) rows.getChildAt(3);
    Assert.assertEquals(View.VISIBLE, picked.findViewById(R.id.size_row_slider).getVisibility());
    Assert.assertEquals(
        View.GONE, rows.getChildAt(2).findViewById(R.id.size_row_slider).getVisibility());

    // a typed height is taken
    final EditText typed = picked.findViewById(R.id.size_row_value);
    typed.setText("0.67");
    typed.onEditorAction(EditorInfo.IME_ACTION_DONE);
    Assert.assertEquals(0.67f, layoutOf(keyboard).rows.get(2).height, 0.001f);
    Assert.assertTrue(keyboard.getKeyboard().getHeight() < heightBefore);
    Assert.assertEquals(
        "0.67", ((EditText) picked.findViewById(R.id.size_row_value)).getText().toString());
    Assert.assertEquals(
        "mixed",
        ((EditText) rows.getChildAt(0).findViewById(R.id.size_row_value)).getText().toString());
  }

  @Test
  public void testSaveStoresAnEnabledKeyboardAndItCanBeEditedAndDeleted() {
    CustomLayoutEditorFragment editor = openEditorForEnglish();
    ((EditText) editor.requireView().findViewById(R.id.custom_layout_name)).setText("Thumbs");
    select(editor, R.id.custom_layout_save);

    Assert.assertEquals(1, factory().getCustomLayouts().size());
    final CustomLayout stored = factory().getCustomLayouts().get(0);
    Assert.assertEquals("Thumbs", stored.name);
    Assert.assertTrue(factory().isAddOnEnabled(stored.id));
    Assert.assertTrue(getCurrentFragment() instanceof KeyboardAddOnBrowserFragment);

    // editing the stored one does not make another
    ((KeyboardAddOnBrowserFragment) getCurrentFragment())
        .onEditAddOnClicked(factory().getAddOnById(stored.id));
    TestRxSchedulers.drainAllTasks();
    editor = (CustomLayoutEditorFragment) getCurrentFragment();
    Assert.assertEquals(stored.id, layoutOf(keyboardView(editor)).id);
    select(editor, R.id.custom_layout_save);
    Assert.assertEquals(1, factory().getCustomLayouts().size());

    ((KeyboardAddOnBrowserFragment) getCurrentFragment())
        .onEditAddOnClicked(factory().getAddOnById(stored.id));
    TestRxSchedulers.drainAllTasks();
    select((CustomLayoutEditorFragment) getCurrentFragment(), R.id.custom_layout_delete);
    Assert.assertTrue(factory().getCustomLayouts().isEmpty());
    Assert.assertFalse(factory().isAddOnEnabled(stored.id));
  }

  @Test
  public void testDoesNotSaveWithoutBackspace() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    for (int row = 0; row < layoutOf(keyboard).rows.size(); row++) {
      final int index = indexOf(layoutOf(keyboard).rows.get(row), KeyCodes.DELETE);
      if (index >= 0) {
        tap(keyboard, row, index);
        editor.requireView().findViewById(R.id.custom_layout_remove_key).performClick();
      }
    }
    Assert.assertFalse(layoutOf(keyboard).contains(KeyCodes.DELETE));
    select(editor, R.id.custom_layout_save);
    Assert.assertTrue(factory().getCustomLayouts().isEmpty());
    Assert.assertSame(editor, getCurrentFragment());
  }

  @Test
  public void testUndoTakesChangesBackOneByOne() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final float width = layoutOf(keyboard).rows.get(1).keys.get(1).width;

    // adding to a row of equal keys makes all of them narrower
    tap(keyboard, 1, 0);
    click(editor, R.id.custom_layout_insert_right);
    Assert.assertEquals(11, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertTrue(layoutOf(keyboard).rows.get(1).keys.get(2).width < width);
    editor.requireView().findViewById(R.id.custom_layout_remove_row).performClick();
    Assert.assertEquals(4, layoutOf(keyboard).rows.size());

    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(5, layoutOf(keyboard).rows.size());
    Assert.assertEquals(11, layoutOf(keyboard).rows.get(1).keys.size());
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(10, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertEquals(width, layoutOf(keyboard).rows.get(1).keys.get(1).width, 0.001f);
    // nothing left to undo
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(5, layoutOf(keyboard).rows.size());
  }

  @Test
  public void testADragIsUndoneAsOneChange() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final int bottom = layoutOf(keyboard).rows.size() - 1;
    final int space = indexOf(layoutOf(keyboard).rows.get(bottom), KeyCodes.SPACE);
    final float spaceWidth = layoutOf(keyboard).rows.get(bottom).keys.get(space).width;

    tap(keyboard, bottom, space);
    final Rect slot = keyboard.getKeyboard().getCustomLayoutSlots()[bottom][space];
    final float step = keyboard.getKeyboard().getMinWidth() * CustomLayout.WIDTH_STEP / 100f;
    touch(keyboard, MotionEvent.ACTION_DOWN, slot.right, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, slot.right - step, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, slot.right - 2 * step, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, slot.right - 2 * step, slot.centerY());
    Assert.assertEquals(
        spaceWidth - 2 * CustomLayout.WIDTH_STEP,
        layoutOf(keyboard).rows.get(bottom).keys.get(space).width,
        0.6f);

    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(
        spaceWidth, layoutOf(keyboard).rows.get(bottom).keys.get(space).width, 0.001f);
  }

  @Test
  public void testEvenWidthsMakesTheKeysOfTheRowEqualAgain() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 0);
    click(editor, R.id.custom_layout_insert_right);
    chipFor(editor, KeyCodes.UNDO).performClick();
    layoutOf(keyboard).rows.get(1).moveEdge(0, 15f);
    editor.requireView().findViewById(R.id.custom_layout_even_row).performClick();

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(11, row.keys.size());
    for (CustomLayout.Key key : row.keys) Assert.assertEquals(100f / 11, key.width, 0.05f);
  }

  @Test
  public void testResetBringsTheOriginalBack() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 0);
    editor.requireView().findViewById(R.id.custom_layout_remove_key).performClick();
    Assert.assertFalse(layoutOf(keyboard).contains('q'));
    select(editor, R.id.custom_layout_reset);
    Assert.assertEquals('q', layoutOf(keyboard).rows.get(1).keys.get(0).code);
  }
}
