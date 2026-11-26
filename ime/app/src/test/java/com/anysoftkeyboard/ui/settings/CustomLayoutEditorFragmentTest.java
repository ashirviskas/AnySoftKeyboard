package com.anysoftkeyboard.ui.settings;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.DialogInterface;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.anysoftkeyboard.RobolectricFragmentTestCase;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.keyboards.CustomLayout;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.keyboards.KeyboardAddOnAndBuilder;
import com.anysoftkeyboard.keyboards.KeyboardFactory;
import com.anysoftkeyboard.keyboards.views.CustomLayoutEditorView;
import com.anysoftkeyboard.rx.TestRxSchedulers;
import com.google.android.material.tabs.TabLayout;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowDialog;

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

  private static View preview(CustomLayoutEditorFragment editor) {
    return editor.requireView().findViewById(R.id.custom_layout_preview);
  }

  /** Taps the chip tagged {@code tag} in the group {@code groupId}. */
  private static void clickChip(CustomLayoutEditorFragment editor, int groupId, String tag) {
    editor.requireView().findViewById(groupId).findViewWithTag(tag).performClick();
    TestRxSchedulers.drainAllTasks();
  }

  private static Keyboard.Key findKey(CustomLayoutEditorView view, int code) {
    for (Keyboard.Key key : view.getKeyboard().getKeys()) {
      if (key.getPrimaryCode() == code) return key;
    }
    return null;
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
  public void testSeveralTypedCharactersBecomeSeveralKeys() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 2);
    final int keys = layoutOf(keyboard).rows.get(1).keys.size();
    // "any character…" is the last of the keyboard's keys
    final ViewGroup chips = editor.requireView().findViewById(R.id.custom_layout_keyboard_keys);
    chips.getChildAt(chips.getChildCount() - 1).performClick();
    final AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    // the lists to pick from: a title and its characters for each group, of several scripts
    final ViewGroup lists = dialog.findViewById(R.id.custom_layout_characters_lists);
    final String[] groups =
        getApplicationContext()
            .getResources()
            .getStringArray(R.array.custom_layout_character_groups);
    Assert.assertEquals(2 * groups.length, lists.getChildCount());
    Assert.assertNotNull(lists.findViewWithTag((int) 'a'));
    Assert.assertNotNull(lists.findViewWithTag((int) 'ų'));
    Assert.assertNotNull(lists.findViewWithTag((int) 'λ'));
    Assert.assertNotNull(lists.findViewWithTag((int) 'ж'));
    ((EditText) dialog.findViewById(R.id.custom_layout_characters_typed)).setText("€£¥");
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    TestRxSchedulers.drainAllTasks();

    // the first takes the place of the picked key, the others follow it
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(keys + 2, row.keys.size());
    Assert.assertEquals('€', row.keys.get(2).code);
    Assert.assertEquals('£', row.keys.get(3).code);
    Assert.assertEquals('¥', row.keys.get(4).code);
  }

  @Test
  public void testWithNothingPickedAKeyIsAddedToTheBottomRowThatIsShown() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    // the last row of the layout is for web addresses only; the keyboard shown is the text one
    final int last = layoutOf(keyboard).rows.size() - 1;
    for (int field : CustomLayout.FIELDS) {
      layoutOf(keyboard).rows.get(last).setShownIn(field, field == Keyboard.KEYBOARD_ROW_MODE_URL);
    }
    final int keysOfLast = layoutOf(keyboard).rows.get(last).keys.size();
    final int keysOfShown = layoutOf(keyboard).rows.get(last - 1).keys.size();
    chipFor(editor, KeyCodes.UNDO).performClick();
    Assert.assertEquals(keysOfLast, layoutOf(keyboard).rows.get(last).keys.size());
    Assert.assertEquals(keysOfShown + 1, layoutOf(keyboard).rows.get(last - 1).keys.size());
  }

  @Test
  public void testOrderAfterADragIsOfAllKeyboards() {
    final KeyboardAddOnAndBuilder english = factory().getEnabledAddOn();
    KeyboardAddOnBrowserFragment browser = startFragment();
    browser.onEditAddOnClicked(english);
    TestRxSchedulers.drainAllTasks();
    select((CustomLayoutEditorFragment) getCurrentFragment(), R.id.custom_layout_save);
    final KeyboardAddOnAndBuilder mine =
        factory().getAddOnById(factory().getCustomLayouts().get(0).id);
    final List<KeyboardAddOnAndBuilder> before = new ArrayList<>(factory().getAllAddOns());

    // among the keyboards: the user's layout is not a card, and goes where the English card goes
    browser = (KeyboardAddOnBrowserFragment) getCurrentFragment();
    final List<KeyboardAddOnAndBuilder> cards = new ArrayList<>(browser.getAddOnsToShow());
    Assert.assertFalse(cards.contains(mine));
    Collections.reverse(cards);
    List<KeyboardAddOnAndBuilder> order = browser.getOrderOfAllAddOns(cards);
    Assert.assertEquals(before.size(), order.size());
    Assert.assertEquals(cards.get(0), order.get(0));
    Assert.assertEquals(order.indexOf(english) + 1, order.indexOf(mine));

    // among the layouts of English: the two change places, the other keyboards stay
    Assert.assertTrue(browser.onAddOnClicked(english));
    TestRxSchedulers.drainAllTasks();
    browser = (KeyboardAddOnBrowserFragment) getCurrentFragment();
    final List<KeyboardAddOnAndBuilder> all = factory().getAllAddOns();
    final List<KeyboardAddOnAndBuilder> layouts = new ArrayList<>(browser.getAddOnsToShow());
    Assert.assertEquals(2, layouts.size());
    Collections.reverse(layouts);
    order = browser.getOrderOfAllAddOns(layouts);
    Assert.assertEquals(all.size(), order.size());
    for (int index = 0; index < all.size(); index++) {
      final KeyboardAddOnAndBuilder was = all.get(index);
      final int layout = layouts.indexOf(was);
      Assert.assertEquals(layout < 0 ? was : layouts.get(1 - layout), order.get(index));
    }
  }

  private static EditText keyValue(CustomLayoutEditorFragment editor, int line) {
    // the lines of "This key" follow its divider and title
    final ViewGroup values = editor.requireView().findViewById(R.id.custom_layout_key_values);
    return values.getChildAt(2 + line).findViewById(R.id.setting_value);
  }

  @Test
  public void testThePickedKeyShowsItsValuesAndTakesNewOnes() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final View values = editor.requireView().findViewById(R.id.custom_layout_key_values);
    Assert.assertEquals(View.GONE, values.getVisibility());

    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    final int q = indexOf(row, 'q');
    tap(keyboard, 1, q);
    Assert.assertEquals(View.VISIBLE, values.getVisibility());
    // Types, With Shift, Label: as the keyboard has them
    Assert.assertEquals("q", keyValue(editor, 0).getText().toString());
    Assert.assertEquals("Q", keyValue(editor, 1).getText().toString());
    Assert.assertEquals("q", keyValue(editor, 2).getText().toString());
    Assert.assertFalse(row.keys.get(q).hasOwnValues());

    keyValue(editor, 0).setText("quite");
    keyValue(editor, 3).setText("qu");
    Assert.assertEquals("quite", row.keys.get(q).text);
    Assert.assertEquals("qu", row.keys.get(q).hint);
    final Keyboard.Key shown = findKey(keyboard, 'q');
    Assert.assertEquals("quite", shown.text.toString());
    Assert.assertEquals("quite", shown.label.toString());

    // the keyboard's own text again: the key follows the keyboard again
    keyValue(editor, 0).setText("q");
    Assert.assertNull(row.keys.get(q).text);
    // another key shows its own values
    tap(keyboard, 1, q + 1);
    Assert.assertEquals("w", keyValue(editor, 0).getText().toString());
    Assert.assertEquals("", keyValue(editor, 3).getText().toString());
    // a gap has none
    click(editor, R.id.custom_layout_insert_left);
    Assert.assertEquals(View.GONE, values.getVisibility());
  }

  @Test
  public void testAKeyCanBeDuplicatedWithItsValues() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    final int keys = row.keys.size();
    tap(keyboard, 1, 2);
    keyValue(editor, 2).setText("mine");
    click(editor, R.id.custom_layout_duplicate_key);
    Assert.assertEquals(keys + 1, row.keys.size());
    Assert.assertEquals(row.keys.get(2).code, row.keys.get(3).code);
    Assert.assertEquals("mine", row.keys.get(3).label);
    Assert.assertEquals(3, keyboard.getSelectedKey());
  }

  @Test
  public void testTheWidthOfAKeyCanBeLockedAndUnlocked() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    final TextView lock = editor.requireView().findViewById(R.id.custom_layout_lock_key);
    Assert.assertEquals(View.GONE, lock.getVisibility());
    tap(keyboard, 1, 2);
    Assert.assertEquals("Lock width", lock.getText().toString());
    final float width = row.keys.get(2).width;
    lock.performClick();
    Assert.assertTrue(row.keys.get(2).locked);
    Assert.assertEquals("Unlock width", lock.getText().toString());
    Assert.assertFalse(editor.requireView().findViewById(R.id.custom_layout_key_width).isEnabled());

    // a key added to the row takes its room from the others
    click(editor, R.id.custom_layout_insert_right);
    Assert.assertEquals(width, row.keys.get(2).width, 0.01f);
    // a key put in its place is locked too
    tap(keyboard, 1, 2);
    chipFor(editor, KeyCodes.UNDO).performClick();
    Assert.assertEquals(KeyCodes.UNDO, row.keys.get(2).code);
    Assert.assertTrue(row.keys.get(2).locked);

    lock.performClick();
    Assert.assertFalse(row.keys.get(2).locked);
    Assert.assertTrue(editor.requireView().findViewById(R.id.custom_layout_key_width).isEnabled());
  }

  @Test
  public void testSeveralPickedKeysAreResizedTogether() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    click(editor, R.id.custom_layout_pick_several);
    tap(keyboard, 1, 0);
    tap(keyboard, 1, 1);
    Assert.assertEquals(2, keyboard.getSelectedKeys().size());
    Assert.assertEquals(
        View.VISIBLE,
        editor.requireView().findViewById(R.id.custom_layout_edges_panel).getVisibility());
    Assert.assertFalse(editor.requireView().findViewById(R.id.custom_layout_left_edge).isEnabled());

    final EditText width = editor.requireView().findViewById(R.id.custom_layout_key_width_value);
    width.setText("15");
    width.onEditorAction(EditorInfo.IME_ACTION_DONE);
    Assert.assertEquals(15f, row.keys.get(0).width, 0.05f);
    Assert.assertEquals(15f, row.keys.get(1).width, 0.05f);
    Assert.assertEquals(70f / 8, row.keys.get(2).width, 0.05f);
    Assert.assertEquals(100f, row.totalWidth(), 0.05f);
    // one step back for all of it
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(10f, layoutOf(keyboard).rows.get(1).keys.get(0).width, 0.05f);
  }

  @Test
  public void testATypedWidthGrowsTheKeyOnBothSidesOrOnTheSideLeftTicked() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    tap(keyboard, 1, 3);
    final EditText width = editor.requireView().findViewById(R.id.custom_layout_key_width_value);
    width.setText("20");
    width.onEditorAction(EditorInfo.IME_ACTION_DONE);
    Assert.assertEquals(20f, row.keys.get(3).width, 0.05f);
    // the three keys before it and the six after it gave 5 each side
    Assert.assertEquals(
        25f, row.keys.get(0).width + row.keys.get(1).width + row.keys.get(2).width, 0.1f);
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(10f, layoutOf(keyboard).rows.get(1).keys.get(3).width, 0.05f);

    // the left edge stays: all of it comes from the right
    tap(keyboard, 1, 3);
    click(editor, R.id.custom_layout_width_moves_left);
    width.setText("20");
    width.onEditorAction(EditorInfo.IME_ACTION_DONE);
    final CustomLayout.Row again = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(20f, again.keys.get(3).width, 0.05f);
    Assert.assertEquals(10f, again.keys.get(2).width, 0.05f);
    // one of the two always moves
    click(editor, R.id.custom_layout_width_moves_right);
    Assert.assertTrue(
        ((android.widget.CheckBox)
                editor.requireView().findViewById(R.id.custom_layout_width_moves_left))
            .isChecked());
  }

  @Test
  public void testUndoneChangesCanBeDoneAgain() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final int keys = layoutOf(keyboard).rows.get(1).keys.size();
    tap(keyboard, 1, 2);
    click(editor, R.id.custom_layout_remove_key);
    Assert.assertEquals(keys - 1, layoutOf(keyboard).rows.get(1).keys.size());
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(keys, layoutOf(keyboard).rows.get(1).keys.size());
    select(editor, R.id.custom_layout_redo);
    Assert.assertEquals(keys - 1, layoutOf(keyboard).rows.get(1).keys.size());
    select(editor, R.id.custom_layout_undo);
    Assert.assertEquals(keys, layoutOf(keyboard).rows.get(1).keys.size());
    // a new change leaves nothing to do again
    tap(keyboard, 1, 3);
    click(editor, R.id.custom_layout_split_key);
    select(editor, R.id.custom_layout_redo);
    Assert.assertEquals(keys + 1, layoutOf(keyboard).rows.get(1).keys.size());
  }

  @Test
  public void testTheLayoutCanBeDuplicated() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    Assert.assertTrue(factory().getCustomLayouts().isEmpty());
    click(editor, R.id.custom_layout_duplicate);
    Assert.assertEquals(1, factory().getCustomLayouts().size());
    final CustomLayout copy = factory().getCustomLayouts().get(0);
    Assert.assertNotEquals(layoutOf(keyboardView(editor)).id, copy.id);
    Assert.assertTrue(factory().isAddOnEnabled(copy.id));
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

  private static void dragRightEdge(CustomLayoutEditorView keyboard, int row, int key, int steps) {
    final Rect slot = keyboard.getKeyboard().getCustomLayoutSlots()[row][key];
    final float by = steps * keyboard.getKeyboard().getMinWidth() * CustomLayout.WIDTH_STEP / 100f;
    touch(keyboard, MotionEvent.ACTION_DOWN, slot.right, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_MOVE, slot.right + by, slot.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, slot.right + by, slot.centerY());
  }

  @Test
  public void testResizingSharesTheRoomWithTheWholeSideInProportionToBeginWith() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    // the bottom row: its keys differ in width (the space bar)
    final int bottom = layoutOf(keyboard).rows.size() - 1;
    final CustomLayout.Row row = layoutOf(keyboard).rows.get(bottom);
    final float[] before = new float[row.keys.size()];
    for (int index = 0; index < before.length; index++) before[index] = row.keys.get(index).width;
    tap(keyboard, bottom, 0);
    dragRightEdge(keyboard, bottom, 0, 1);
    Assert.assertEquals(before[0] + CustomLayout.WIDTH_STEP, row.keys.get(0).width, 0.3f);
    // every other key gave the same part of itself
    final float factor = row.keys.get(1).width / before[1];
    Assert.assertTrue(factor < 1);
    for (int index = 1; index < before.length; index++) {
      Assert.assertEquals(before[index] * factor, row.keys.get(index).width, 0.05f);
    }
    // and back: all as they were
    dragRightEdge(keyboard, bottom, 0, -1);
    for (int index = 0; index < before.length; index++) {
      Assert.assertEquals(before[index], row.keys.get(index).width, 0.05f);
    }
  }

  @Test
  public void testResizingCanLeaveTheKeysOfTheSideEqual() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    click(editor, R.id.custom_layout_resize_keeps_proportions);
    tap(keyboard, 1, 0);
    dragRightEdge(keyboard, 1, 0, 1);

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

  private static void openTab(CustomLayoutEditorFragment editor, int tab) {
    final TabLayout tabs = editor.requireView().findViewById(R.id.custom_layout_tabs);
    tabs.selectTab(tabs.getTabAt(tab));
    TestRxSchedulers.drainAllTasks();
  }

  private static int shownTab(CustomLayoutEditorFragment editor) {
    return ((TabLayout) editor.requireView().findViewById(R.id.custom_layout_tabs))
        .getSelectedTabPosition();
  }

  private static String textOf(CustomLayoutEditorFragment editor, int viewId) {
    return ((TextView) editor.requireView().findViewById(viewId)).getText().toString();
  }

  @Test
  public void testRowTabIsAboutOneRow() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final View root = editor.requireView();
    Assert.assertEquals(
        View.VISIBLE, root.findViewById(R.id.custom_layout_key_panel).getVisibility());
    Assert.assertEquals(View.GONE, root.findViewById(R.id.custom_layout_row_panel).getVisibility());

    // with nothing picked the Row tab starts at the bottom row
    openTab(editor, 1);
    Assert.assertEquals(View.GONE, root.findViewById(R.id.custom_layout_key_panel).getVisibility());
    Assert.assertEquals(
        View.VISIBLE, root.findViewById(R.id.custom_layout_row_panel).getVisibility());
    Assert.assertEquals(4, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());
    Assert.assertEquals("Row 5 of 5", textOf(editor, R.id.custom_layout_row_title));

    // a tap on the keyboard picks a row here, not a key; the arrows step through the rows
    tap(keyboard, 2, 3);
    Assert.assertEquals(2, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());
    Assert.assertEquals(1, shownTab(editor));
    click(editor, R.id.custom_layout_row_up);
    Assert.assertEquals(1, keyboard.getSelectedRow());
    Assert.assertEquals("Row 2 of 5", textOf(editor, R.id.custom_layout_row_title));
    click(editor, R.id.custom_layout_row_down);
    click(editor, R.id.custom_layout_row_down);
    click(editor, R.id.custom_layout_row_down);
    click(editor, R.id.custom_layout_row_down);
    Assert.assertEquals(4, keyboard.getSelectedRow());
    click(editor, R.id.custom_layout_row_up);
    click(editor, R.id.custom_layout_row_up);
    Assert.assertEquals(2, keyboard.getSelectedRow());

    // its height: one item, with the slider, and a typed height is taken
    final ViewGroup height = root.findViewById(R.id.custom_layout_row_height);
    Assert.assertEquals(1, height.getChildCount());
    Assert.assertEquals(
        View.VISIBLE, height.getChildAt(0).findViewById(R.id.size_row_slider).getVisibility());
    final int heightBefore = keyboard.getKeyboard().getHeight();
    final EditText typed = height.getChildAt(0).findViewById(R.id.size_row_value);
    typed.setText("0.67");
    typed.onEditorAction(EditorInfo.IME_ACTION_DONE);
    Assert.assertEquals(0.67f, layoutOf(keyboard).rows.get(2).height, 0.001f);
    Assert.assertTrue(keyboard.getKeyboard().getHeight() < heightBefore);

    // rows are added and removed around the picked one, and a row stays picked
    click(editor, R.id.custom_layout_add_row_above);
    Assert.assertEquals(6, layoutOf(keyboard).rows.size());
    Assert.assertEquals(2, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());
    Assert.assertEquals("Row 3 of 6", textOf(editor, R.id.custom_layout_row_title));
    click(editor, R.id.custom_layout_remove_row);
    Assert.assertEquals(5, layoutOf(keyboard).rows.size());
    Assert.assertTrue(keyboard.getSelectedRow() >= 0);
  }

  @Test
  public void testKeyboardTabIsAboutTheWholeLayout() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 2);
    openTab(editor, 2);
    final View root = editor.requireView();
    Assert.assertEquals(
        View.VISIBLE, root.findViewById(R.id.custom_layout_keyboard_panel).getVisibility());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedRow());
    Assert.assertEquals(
        String.format(
            java.util.Locale.ROOT,
            "Total %.2f rows",
            layoutOf(keyboard).totalHeight(Keyboard.KEYBOARD_ROW_MODE_NORMAL)),
        textOf(editor, R.id.custom_layout_total_height));

    // "all rows above the bottom row" with its slider, then the five rows
    final ViewGroup rows = root.findViewById(R.id.custom_layout_size_rows);
    Assert.assertEquals(6, rows.getChildCount());
    Assert.assertEquals(
        View.VISIBLE, rows.getChildAt(0).findViewById(R.id.size_row_slider).getVisibility());
    Assert.assertEquals(
        "mixed",
        ((EditText) rows.getChildAt(0).findViewById(R.id.size_row_value)).getText().toString());
    final EditText all = rows.getChildAt(0).findViewById(R.id.size_row_value);
    all.setText("0.8");
    all.onEditorAction(EditorInfo.IME_ACTION_DONE);
    for (int row = 0; row < 4; row++) {
      Assert.assertEquals(0.8f, layoutOf(keyboard).rows.get(row).height, 0.001f);
    }
    Assert.assertEquals(1f, layoutOf(keyboard).rows.get(4).height, 0.001f);

    // a row of the list opens on the Row tab
    rows.getChildAt(3).findViewById(R.id.size_row_header).performClick();
    TestRxSchedulers.drainAllTasks();
    Assert.assertEquals(1, shownTab(editor));
    Assert.assertEquals(2, keyboard.getSelectedRow());

    // a key tapped on the keyboard from here opens on the Key tab
    openTab(editor, 2);
    tap(keyboard, 3, 1);
    Assert.assertEquals(0, shownTab(editor));
    Assert.assertEquals(3, keyboard.getSelectedRow());
    Assert.assertEquals(1, keyboard.getSelectedKey());
  }

  @Test
  public void testKeyTabFindsItsKeyAgainAndLeadsToItsRow() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    tap(keyboard, 1, 2);
    Assert.assertEquals("Row 2 ›", textOf(editor, R.id.custom_layout_go_to_row));
    click(editor, R.id.custom_layout_go_to_row);
    Assert.assertEquals(1, shownTab(editor));
    Assert.assertEquals(1, keyboard.getSelectedRow());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedKey());

    openTab(editor, 0);
    Assert.assertEquals(1, keyboard.getSelectedRow());
    Assert.assertEquals(2, keyboard.getSelectedKey());
  }

  @Test
  public void testWidthOfTheKeyAndSplit() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    click(editor, R.id.custom_layout_resize_evens_side);
    tap(keyboard, 1, 1);
    final EditText width = editor.requireView().findViewById(R.id.custom_layout_key_width_value);
    Assert.assertEquals("10.0", width.getText().toString());
    width.setText("12.5");
    width.onEditorAction(EditorInfo.IME_ACTION_DONE);
    CustomLayout.Row row = layoutOf(keyboard).rows.get(1);
    // it grows on both sides: each neighbour gives half
    Assert.assertEquals(12.5f, row.keys.get(1).width, 0.06f);
    Assert.assertEquals(8.75f, row.keys.get(2).width, 0.06f);
    Assert.assertEquals(8.75f, row.keys.get(0).width, 0.06f);

    // the last key of a row grows to the left
    tap(keyboard, 1, 9);
    width.setText("15");
    width.onEditorAction(EditorInfo.IME_ACTION_DONE);
    row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(15f, row.keys.get(9).width, 0.06f);
    Assert.assertEquals(5f, row.keys.get(8).width, 0.06f);

    // split: half the key, and a gap in the other half, picked to take a key
    click(editor, R.id.custom_layout_split_key);
    row = layoutOf(keyboard).rows.get(1);
    Assert.assertEquals(11, row.keys.size());
    Assert.assertEquals('p', row.keys.get(9).code);
    Assert.assertEquals(7.5f, row.keys.get(9).width, 0.06f);
    Assert.assertEquals(CustomLayout.GAP, row.keys.get(10).code);
    Assert.assertEquals(7.5f, row.keys.get(10).width, 0.06f);
    Assert.assertEquals(10, keyboard.getSelectedKey());
    chipFor(editor, KeyCodes.UNDO).performClick();
    Assert.assertEquals(KeyCodes.UNDO, layoutOf(keyboard).rows.get(1).keys.get(10).code);
  }

  @Test
  public void testTheKeyboardCanBeShownUnderTheControlsAndThatIsKept() {
    CustomLayoutEditorFragment editor = openEditorForEnglish();
    ViewGroup root = editor.requireView().findViewById(R.id.custom_layout_root);
    Assert.assertSame(preview(editor), root.getChildAt(0));
    Assert.assertSame(preview(editor), keyboardView(editor).getParent());

    select(editor, R.id.custom_layout_flip);
    Assert.assertSame(preview(editor), root.getChildAt(root.getChildCount() - 1));

    // another layout opens the same way
    select(editor, R.id.custom_layout_save);
    ((KeyboardAddOnBrowserFragment) getCurrentFragment())
        .onEditAddOnClicked(factory().getAddOnById(factory().getCustomLayouts().get(0).id));
    TestRxSchedulers.drainAllTasks();
    editor = (CustomLayoutEditorFragment) getCurrentFragment();
    root = editor.requireView().findViewById(R.id.custom_layout_root);
    Assert.assertSame(preview(editor), root.getChildAt(root.getChildCount() - 1));
    select(editor, R.id.custom_layout_flip);
    Assert.assertSame(preview(editor), root.getChildAt(0));
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
    // deleting asks first
    Assert.assertEquals(1, factory().getCustomLayouts().size());
    ((AlertDialog) ShadowDialog.getLatestDialog())
        .getButton(DialogInterface.BUTTON_POSITIVE)
        .performClick();
    TestRxSchedulers.drainAllTasks();
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

  @Test
  public void testTheKeyboardCanBeLookedAtForEachKindOfField() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    Assert.assertEquals(
        Keyboard.KEYBOARD_ROW_MODE_NORMAL, keyboard.getKeyboard().getKeyboardMode());
    Assert.assertNull(findKey(keyboard, '@'));

    clickChip(
        editor, R.id.custom_layout_preview_setups, "field:" + Keyboard.KEYBOARD_ROW_MODE_EMAIL);
    Assert.assertEquals(Keyboard.KEYBOARD_ROW_MODE_EMAIL, keyboard.getKeyboard().getKeyboardMode());
    Assert.assertNotNull(findKey(keyboard, '@'));

    // editing goes on there: a key is picked and removed
    final int rows = layoutOf(keyboard).rows.size();
    final int keysBefore = layoutOf(keyboard).rows.get(1).keys.size();
    tap(keyboard, 1, 0);
    click(editor, R.id.custom_layout_remove_key);
    Assert.assertEquals(keysBefore - 1, layoutOf(keyboard).rows.get(1).keys.size());
    Assert.assertEquals(Keyboard.KEYBOARD_ROW_MODE_EMAIL, keyboard.getKeyboard().getKeyboardMode());
    Assert.assertEquals(rows, layoutOf(keyboard).rows.size());
  }

  @Test
  public void testSplitIsForLookingOnly() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final View hint = editor.requireView().findViewById(R.id.custom_layout_preview_hint);
    Assert.assertEquals(View.GONE, hint.getVisibility());
    final Keyboard.Key q = findKey(keyboard, 'q');
    final Keyboard.Key p = findKey(keyboard, 'p');
    final int between = p.x - q.x;

    clickChip(editor, R.id.custom_layout_preview_setups, "shape:Split");
    Assert.assertEquals(View.VISIBLE, hint.getVisibility());
    Assert.assertTrue(
        findKey(keyboard, 'p').x - findKey(keyboard, 'q').x > between
            || findKey(keyboard, 'q').width < q.width);
    // a touch picks nothing
    final Rect slot = keyboard.getKeyboard().getCustomLayoutSlots()[1][2];
    touch(keyboard, MotionEvent.ACTION_DOWN, slot.centerX(), slot.centerY());
    touch(keyboard, MotionEvent.ACTION_UP, slot.centerX(), slot.centerY());
    Assert.assertEquals(CustomLayoutEditorView.NOTHING, keyboard.getSelectedRow());

    clickChip(editor, R.id.custom_layout_preview_setups, "shape:None");
    Assert.assertEquals(View.GONE, hint.getVisibility());
    Assert.assertEquals(q.width, findKey(keyboard, 'q').width);
    tap(keyboard, 1, 2);
    Assert.assertEquals(1, keyboard.getSelectedRow());
  }

  @Test
  public void testARowCanBeDuplicatedMovedAndShownForSomeFields() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    final int rows = layoutOf(keyboard).rows.size();
    final int heightBefore = keyboard.getKeyboard().getHeight();
    final String url = "field:" + Keyboard.KEYBOARD_ROW_MODE_URL;

    // the Row tab starts at the bottom row; its copy is under it and picked
    openTab(editor, 1);
    click(editor, R.id.custom_layout_duplicate_row);
    Assert.assertEquals(rows + 1, layoutOf(keyboard).rows.size());
    Assert.assertEquals(rows, keyboard.getSelectedRow());
    Assert.assertTrue(keyboard.getKeyboard().getHeight() > heightBefore);

    // the copy is kept for web addresses only: the keyboard shown becomes that of such a field
    for (int field : CustomLayout.FIELDS) {
      if (field != Keyboard.KEYBOARD_ROW_MODE_URL) {
        clickChip(editor, R.id.custom_layout_row_fields, "field:" + field);
      }
    }
    Assert.assertEquals(
        1 << Keyboard.KEYBOARD_ROW_MODE_URL, layoutOf(keyboard).rows.get(rows).fields);
    Assert.assertEquals(Keyboard.KEYBOARD_ROW_MODE_URL, keyboard.getKeyboard().getKeyboardMode());
    // and it cannot be taken out of its last field
    clickChip(editor, R.id.custom_layout_row_fields, url);
    Assert.assertEquals(
        1 << Keyboard.KEYBOARD_ROW_MODE_URL, layoutOf(keyboard).rows.get(rows).fields);

    // the row above it is taken out of web addresses: one bottom row per field again
    click(editor, R.id.custom_layout_row_up);
    Assert.assertEquals(rows - 1, keyboard.getSelectedRow());
    clickChip(editor, R.id.custom_layout_row_fields, url);
    Assert.assertFalse(
        layoutOf(keyboard).rows.get(rows - 1).showsIn(Keyboard.KEYBOARD_ROW_MODE_URL));
    Assert.assertEquals(
        Keyboard.KEYBOARD_ROW_MODE_NORMAL, keyboard.getKeyboard().getKeyboardMode());
    Assert.assertEquals(heightBefore, keyboard.getKeyboard().getHeight());
    Assert.assertEquals(0, keyboard.getKeyboard().getCustomLayoutSlots()[rows].length);

    // stepping to the row for web addresses shows that keyboard
    click(editor, R.id.custom_layout_row_down);
    Assert.assertEquals(rows, keyboard.getSelectedRow());
    Assert.assertEquals(Keyboard.KEYBOARD_ROW_MODE_URL, keyboard.getKeyboard().getKeyboardMode());
    Assert.assertEquals(heightBefore, keyboard.getKeyboard().getHeight());

    // the list of the Keyboard tab says what the row is for
    openTab(editor, 2);
    final ViewGroup list = editor.requireView().findViewById(R.id.custom_layout_size_rows);
    final TextView last =
        list.getChildAt(list.getChildCount() - 1).findViewById(R.id.size_row_label);
    Assert.assertTrue(last.getText().toString(), last.getText().toString().endsWith("Web address"));

    // rows move
    openTab(editor, 1);
    click(editor, R.id.custom_layout_move_row_up);
    Assert.assertEquals(rows - 1, keyboard.getSelectedRow());
    Assert.assertEquals(
        1 << Keyboard.KEYBOARD_ROW_MODE_URL, layoutOf(keyboard).rows.get(rows - 1).fields);
    click(editor, R.id.custom_layout_move_row_down);
    Assert.assertEquals(rows, keyboard.getSelectedRow());

    // it is all one keyboard, saved as such
    select(editor, R.id.custom_layout_save);
    Assert.assertEquals(1, factory().getCustomLayouts().size());
    Assert.assertEquals(
        1 << Keyboard.KEYBOARD_ROW_MODE_URL,
        factory().getCustomLayouts().get(0).rows.get(rows).fields);
  }

  @Test
  public void testDoesNotSaveWhenAFieldHasNoEnter() {
    final CustomLayoutEditorFragment editor = openEditorForEnglish();
    final CustomLayoutEditorView keyboard = keyboardView(editor);
    // the bottom row (the one with Enter) is taken out of password fields
    openTab(editor, 1);
    Assert.assertTrue(
        indexOf(layoutOf(keyboard).rows.get(keyboard.getSelectedRow()), KeyCodes.ENTER) >= 0);
    clickChip(
        editor, R.id.custom_layout_row_fields, "field:" + Keyboard.KEYBOARD_ROW_MODE_PASSWORD);
    select(editor, R.id.custom_layout_save);
    Assert.assertTrue(factory().getCustomLayouts().isEmpty());
    Assert.assertSame(editor, getCurrentFragment());

    // such a field's keyboard can still be looked at, and one with no rows at all is not shown
    clickChip(
        editor, R.id.custom_layout_preview_setups, "field:" + Keyboard.KEYBOARD_ROW_MODE_PASSWORD);
    Assert.assertEquals(
        Keyboard.KEYBOARD_ROW_MODE_PASSWORD, keyboard.getKeyboard().getKeyboardMode());
  }
}
