package com.anysoftkeyboard.keyboards;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.anysoftkeyboard.keyboards.ExternalAnyKeyboardTest.SIMPLE_KeyboardDimens;

import com.anysoftkeyboard.AnySoftKeyboardRobolectricTestRunner;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.prefs.DirectBootAwareSharedPreferences;
import com.anysoftkeyboard.rx.TestRxSchedulers;
import com.menny.android.anysoftkeyboard.AnyApplication;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AnySoftKeyboardRobolectricTestRunner.class)
public class CustomLayoutTest {
  private KeyboardFactory mFactory;
  private KeyboardAddOnAndBuilder mEnglish;

  @Before
  public void setUp() {
    mFactory = AnyApplication.getKeyboardFactory(getApplicationContext());
    mEnglish = mFactory.getEnabledAddOn();
  }

  private static AnyKeyboard load(KeyboardAddOnAndBuilder addOn, int mode) {
    final AnyKeyboard keyboard = addOn.createKeyboard(mode);
    keyboard.loadKeyboard(SIMPLE_KeyboardDimens);
    return keyboard;
  }

  private static AnyKeyboard load(KeyboardAddOnAndBuilder addOn) {
    return load(addOn, Keyboard.KEYBOARD_ROW_MODE_NORMAL);
  }

  private CustomLayout captureEnglish() {
    return CustomLayout.capture("layout-test", "Mine", load(mEnglish));
  }

  private static Keyboard.Key findKey(AnyKeyboard keyboard, int code) {
    for (Keyboard.Key key : keyboard.getKeys()) {
      if (key.getPrimaryCode() == code) return key;
    }
    return null;
  }

  private static CustomLayout.Row row(float... widths) {
    final CustomLayout.Row row = new CustomLayout.Row();
    for (int i = 0; i < widths.length; i++) row.keys.add(new CustomLayout.Key('a' + i, widths[i]));
    return row;
  }

  @Test
  public void testCaptureDescribesTheWholeKeyboard() {
    final AnyKeyboard english = load(mEnglish);
    final CustomLayout layout = captureEnglish();

    Assert.assertEquals(mEnglish.getId(), layout.baseId);
    // top generic row, three letter rows, bottom generic row
    Assert.assertEquals(5, layout.rows.size());
    int keys = 0;
    for (CustomLayout.Row row : layout.rows) {
      Assert.assertEquals(100f, row.totalWidth(), 0.5f);
      for (CustomLayout.Key key : row.keys) {
        if (key.code != CustomLayout.GAP) keys++;
      }
    }
    Assert.assertEquals(english.getKeys().size(), keys);
    Assert.assertEquals(1f, layout.rows.get(1).height, 0.01f);
    Assert.assertTrue(layout.contains(KeyCodes.SPACE));
    Assert.assertTrue(layout.contains(KeyCodes.ENTER));
    Assert.assertTrue(layout.contains(KeyCodes.DELETE));
  }

  @Test
  public void testCapturedLayoutLoadsAsTheSameKeyboard() {
    final AnyKeyboard english = load(mEnglish);
    final AnyKeyboard custom = load(mEnglish.withLayout(captureEnglish(), 1));

    Assert.assertEquals("layout-test", custom.getKeyboardId());
    Assert.assertEquals("Mine", custom.getKeyboardName().toString());
    Assert.assertEquals(english.getHeight(), custom.getHeight(), 2);
    Assert.assertEquals(english.getKeys().size(), custom.getKeys().size());
    for (int i = 0; i < english.getKeys().size(); i++) {
      final Keyboard.Key expected = english.getKeys().get(i);
      final Keyboard.Key actual = custom.getKeys().get(i);
      Assert.assertEquals(expected.getPrimaryCode(), actual.getPrimaryCode());
      Assert.assertEquals(expected.x, actual.x, 2);
      Assert.assertEquals(expected.y, actual.y, 2);
      Assert.assertEquals(expected.width, actual.width, 2);
      Assert.assertEquals(expected.height, actual.height, 2);
      Assert.assertEquals(expected.popupCharacters, actual.popupCharacters);
    }
    Assert.assertNotNull(custom.getShiftKey());
  }

  @Test
  public void testEveryKeyboardSurvivesCapture() {
    for (KeyboardAddOnAndBuilder addOn : mFactory.getAllAddOns()) {
      final AnyKeyboard original = load(addOn);
      final AnyKeyboard custom =
          load(addOn.withLayout(CustomLayout.capture("layout-test", "Mine", original), 1));
      final List<Integer> expected = new ArrayList<>();
      for (Keyboard.Key key : original.getKeys()) {
        if (key.getPrimaryCode() != KeyCodes.DISABLED) expected.add(key.getPrimaryCode());
      }
      final List<Integer> actual = new ArrayList<>();
      for (Keyboard.Key key : custom.getKeys()) actual.add(key.getPrimaryCode());
      Assert.assertEquals(addOn.getName(), expected, actual);
      Assert.assertEquals(addOn.getName(), original.getHeight(), custom.getHeight(), 4);
      Assert.assertEquals(
          addOn.getName(), original.isRadicalKeyboard(), custom.isRadicalKeyboard());
      Assert.assertEquals(
          addOn.getName(), original.getPhoneticLayout(), custom.getPhoneticLayout());
    }
  }

  @Test
  public void testCaptureOfCustomKeyboardCopiesItsLayout() {
    final CustomLayout layout = captureEnglish();
    layout.rows.get(1).height = 0.8f;
    final CustomLayout copy =
        CustomLayout.capture("layout-copy", "Copy", load(mEnglish.withLayout(layout, 1)));
    Assert.assertEquals("layout-copy", copy.id);
    Assert.assertEquals(mEnglish.getId(), copy.baseId);
    Assert.assertEquals(0.8f, copy.rows.get(1).height, 0f);
    Assert.assertNotSame(layout.rows.get(1), copy.rows.get(1));
  }

  @Test
  public void testRemovedKeyIsStillAChoice() {
    final CustomLayout layout = captureEnglish();
    // 'q' is the first key of the first letter row
    Assert.assertEquals('q', layout.rows.get(1).keys.get(0).code);
    layout.rows.get(1).remove(0);
    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));

    Assert.assertNull(findKey(custom, 'q'));
    // the choices are all keys of the keyboard and the usual keys, on the layout or not
    Assert.assertTrue(custom.getCustomLayoutKeyChoices().contains((int) 'q'));
    Assert.assertTrue(custom.getCustomLayoutKeyChoices().contains((int) 'w'));
    Assert.assertTrue(custom.getCustomLayoutKeyChoices().contains(KeyCodes.UNDO));
    Assert.assertEquals(
        custom.getCustomLayoutKeyChoices().indexOf(KeyCodes.SPACE),
        custom.getCustomLayoutKeyChoices().lastIndexOf(KeyCodes.SPACE));
    // the other keys of the row took the room
    Assert.assertEquals(100f, layout.rows.get(1).totalWidth(), 0.5f);
    Assert.assertTrue(findKey(custom, 'w').x < 20);
  }

  @Test
  public void testAKeyCanBeOnTheLayoutTwice() {
    final CustomLayout layout = captureEnglish();
    final CustomLayout.Row bottom = layout.rows.get(layout.rows.size() - 1);
    bottom.add(0, new CustomLayout.Key(KeyCodes.DELETE, 10));
    bottom.add(1, new CustomLayout.Key('q', 10));
    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));

    final List<Keyboard.Key> deletes = new ArrayList<>();
    final List<Keyboard.Key> qs = new ArrayList<>();
    for (Keyboard.Key key : custom.getKeys()) {
      if (key.getPrimaryCode() == KeyCodes.DELETE) deletes.add(key);
      if (key.getPrimaryCode() == 'q') qs.add(key);
    }
    Assert.assertEquals(2, deletes.size());
    Assert.assertEquals(2, qs.size());
    Assert.assertNotSame(deletes.get(0), deletes.get(1));
    Assert.assertNotEquals(deletes.get(0).y, deletes.get(1).y);
    // the second one is like the first
    Assert.assertTrue(deletes.get(1).repeatable);
    Assert.assertEquals(qs.get(0).popupCharacters, qs.get(1).popupCharacters);
    Assert.assertTrue(qs.get(1).isInside(qs.get(1).x + 1, qs.get(1).y + 1));
  }

  @Test
  public void testSideGaps() {
    final CustomLayout.Row row = row(25, 25, 25, 25);
    Assert.assertEquals(0f, row.getSideGaps(), 0f);
    row.setSideGaps(10);
    Assert.assertEquals(6, row.keys.size());
    Assert.assertEquals(10f, row.getSideGaps(), 0.01f);
    Assert.assertEquals(20f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
    row.setSideGaps(5);
    Assert.assertEquals(6, row.keys.size());
    Assert.assertEquals(22.5f, row.keys.get(1).width, 0.01f);
    row.setSideGaps(0);
    Assert.assertEquals(4, row.keys.size());
    Assert.assertEquals(25f, row.keys.get(0).width, 0.01f);
  }

  @Test
  public void testUsualKeysAndPlainCharactersCanBePlaced() {
    final CustomLayout layout = captureEnglish();
    final CustomLayout.Row bottom = layout.rows.get(layout.rows.size() - 1);
    Assert.assertNull(findKey(load(mEnglish), KeyCodes.UNDO));

    bottom.add(0, new CustomLayout.Key(KeyCodes.UNDO, 10));
    final CustomLayout.Key euro = new CustomLayout.Key('€', 10);
    euro.popup = "$£";
    bottom.add(1, euro);
    Assert.assertEquals(100f, bottom.totalWidth(), 0.5f);

    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));
    final Keyboard.Key undo = findKey(custom, KeyCodes.UNDO);
    Assert.assertNotNull(undo);
    Assert.assertTrue(((AnyKeyboard.AnyKey) undo).isFunctional());
    final Keyboard.Key euroKey = findKey(custom, '€');
    Assert.assertNotNull(euroKey);
    Assert.assertEquals("€", euroKey.label.toString());
    Assert.assertEquals("$£", euroKey.popupCharacters.toString());
    Assert.assertTrue(euroKey.isInside(euroKey.x + 1, euroKey.y + 1));
    Assert.assertEquals(undo.y, euroKey.y);
    Assert.assertTrue(undo.x < euroKey.x);
  }

  @Test
  public void testRowHeightsSetTheKeyboardHeight() {
    final CustomLayout layout = captureEnglish();
    final int before = load(mEnglish.withLayout(layout, 1)).getHeight();
    layout.rows.get(1).height = 0.5f;
    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));

    final int normal = SIMPLE_KeyboardDimens.getNormalKeyHeight();
    Assert.assertEquals(before - normal / 2, custom.getHeight(), 2);
    Assert.assertEquals(normal / 2, findKey(custom, 'q').height, 1);
    Assert.assertEquals(normal, findKey(custom, 'a').height, 1);
  }

  @Test
  public void testEnterKeyKeepsItsCustomHeightWhenEnabled() {
    final CustomLayout layout = captureEnglish();
    layout.rows.get(layout.rows.size() - 1).height = 0.5f;
    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));
    custom.setImeOptions(getApplicationContext().getResources(), null);
    Assert.assertEquals(
        SIMPLE_KeyboardDimens.getNormalKeyHeight() / 2, findKey(custom, KeyCodes.ENTER).height, 1);
  }

  @Test
  public void testAddressFieldsGetTheirKeyInTheBottomRow() {
    final CustomLayout layout = captureEnglish();
    Assert.assertNull(
        findKey(load(mEnglish.withLayout(layout, 1), Keyboard.KEYBOARD_ROW_MODE_NORMAL), '@'));
    Assert.assertNotNull(
        findKey(load(mEnglish.withLayout(layout, 1), Keyboard.KEYBOARD_ROW_MODE_EMAIL), '@'));
    final AnyKeyboard url = load(mEnglish.withLayout(layout, 1), Keyboard.KEYBOARD_ROW_MODE_URL);
    final Keyboard.Key slash = findKey(url, '/');
    Assert.assertNotNull(slash);
    Assert.assertEquals(findKey(url, KeyCodes.SPACE).y, slash.y);
    Assert.assertEquals(
        load(mEnglish.withLayout(layout, 1)).getKeys().size(), url.getKeys().size());
  }

  @Test
  public void testSlotsFollowTheLayoutIncludingGaps() {
    final CustomLayout layout = captureEnglish();
    layout.rows.get(1).add(3, new CustomLayout.Key(CustomLayout.GAP, 10));
    final AnyKeyboard custom = load(mEnglish.withLayout(layout, 1));
    Assert.assertEquals(layout.rows.size(), custom.getCustomLayoutSlots().length);
    for (int row = 0; row < layout.rows.size(); row++) {
      Assert.assertEquals(
          layout.rows.get(row).keys.size(), custom.getCustomLayoutSlots()[row].length);
    }
    final Keyboard.Key r = findKey(custom, 'r');
    Assert.assertEquals(custom.getCustomLayoutSlots()[1][4].left, r.x);
    Assert.assertTrue(custom.getCustomLayoutSlots()[1][3].right <= r.x);
  }

  @Test
  public void testJsonRoundTrip() throws Exception {
    final CustomLayout layout = captureEnglish();
    layout.rows.get(2).keys.get(1).popup = "åä";
    layout.rows.get(0).height = 0.67f;
    final String json = layout.toJson().toString();
    final CustomLayout read = CustomLayout.fromJson(new JSONObject(json));

    Assert.assertEquals(json, read.toJson().toString());
    Assert.assertEquals("layout-test", read.id);
    Assert.assertEquals("Mine", read.name);
    Assert.assertEquals(layout.baseId, read.baseId);
    Assert.assertEquals("åä", read.rows.get(2).keys.get(1).popup);
    Assert.assertNull(read.rows.get(2).keys.get(0).popup);
    Assert.assertEquals(0.67f, read.rows.get(0).height, 0f);
  }

  @Test
  public void testAddTakesRoomFromTheStretchKey() {
    final CustomLayout.Row row = row(10, 10, 50, 15, 15);
    row.add(1, new CustomLayout.Key('x', 10));
    Assert.assertEquals(6, row.keys.size());
    Assert.assertEquals('x', row.keys.get(1).code);
    Assert.assertEquals(40f, row.keys.get(3).width, 0.01f);
    Assert.assertEquals(10f, row.keys.get(0).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);

    row.remove(1);
    Assert.assertEquals(50f, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
  }

  @Test
  public void testAddToRowOfEqualKeysShrinksAll() {
    final CustomLayout.Row row = row(25, 25, 25, 25);
    row.add(4, new CustomLayout.Key('x', 20));
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
    Assert.assertEquals(20f, row.keys.get(0).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(4).width, 0.01f);

    row.remove(0);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
    Assert.assertEquals(25f, row.keys.get(0).width, 0.01f);
  }

  @Test
  public void testAddToEmptyRowFillsIt() {
    final CustomLayout.Row row = new CustomLayout.Row();
    row.add(0, new CustomLayout.Key('x', 10));
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
  }

  @Test
  public void testSplitHalvesAKeyAndLeavesAGap() {
    final CustomLayout.Row row = row(20, 30, 50);
    row.split(1);
    Assert.assertEquals(4, row.keys.size());
    Assert.assertEquals('b', row.keys.get(1).code);
    Assert.assertEquals(15f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(CustomLayout.GAP, row.keys.get(2).code);
    Assert.assertEquals(15f, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
  }

  @Test
  public void testEvenWidthsKeepsGaps() {
    final CustomLayout.Row row = row(10, 35, 25, 20);
    row.keys.add(0, new CustomLayout.Key(CustomLayout.GAP, 10));
    row.evenWidths();
    Assert.assertEquals(10f, row.keys.get(0).width, 0.01f);
    for (int index = 1; index < 5; index++) {
      Assert.assertEquals(22.5f, row.keys.get(index).width, 0.01f);
    }
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
  }

  @Test
  public void testEvenWidthsOfSomeKeys() {
    final CustomLayout.Row row = row(10, 30, 20, 40);
    row.evenWidths(java.util.Arrays.asList(0, 1, 2));
    Assert.assertEquals(20f, row.keys.get(0).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(40f, row.keys.get(3).width, 0.01f);
  }

  @Test
  public void testMoveEdgeSharingWithTheWholeSide() {
    // the right edge of the first key: the three keys after it share
    CustomLayout.Row row = row(25, 25, 20, 30);
    Assert.assertTrue(row.moveEdge(0, 40f, true));
    Assert.assertEquals(40f, row.keys.get(0).width, 0.01f);
    for (int index = 1; index < 4; index++) {
      Assert.assertEquals(20f, row.keys.get(index).width, 0.01f);
    }
    // the same place again only if nothing is left to even
    Assert.assertFalse(row.moveEdge(0, 40f, true));

    // the left edge of the last key: the keys before it share, gaps keep their width
    row = row(20, 30, 30, 10);
    row.keys.add(0, new CustomLayout.Key(CustomLayout.GAP, 10));
    Assert.assertTrue(row.moveEdge(3, 70f, false));
    Assert.assertEquals(10f, row.keys.get(0).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(20f, row.keys.get(3).width, 0.01f);
    Assert.assertEquals(30f, row.keys.get(4).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);

    // every key keeps one step, however far the edge is pulled
    row = row(25, 25, 25, 25);
    Assert.assertTrue(row.moveEdge(0, 500f, true));
    Assert.assertEquals(85f, row.keys.get(0).width, 0.01f);
    Assert.assertEquals(CustomLayout.WIDTH_STEP, row.keys.get(3).width, 0.01f);
    Assert.assertTrue(row.moveEdge(0, 0f, true));
    Assert.assertEquals(CustomLayout.WIDTH_STEP, row.keys.get(0).width, 0.01f);
  }

  @Test
  public void testMoveEdgeInFineSteps() {
    final CustomLayout.Row row = row(10, 30, 60);
    Assert.assertTrue(row.moveEdge(1, 41.26f, 0.5f));
    Assert.assertEquals(31.5f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(58.5f, row.keys.get(2).width, 0.01f);
  }

  @Test
  public void testMoveEdgeSnapsAndKeepsBothKeys() {
    final CustomLayout.Row row = row(10, 30, 60);
    // the edge between the second and third key is at 40
    Assert.assertTrue(row.moveEdge(1, 52f));
    Assert.assertEquals(40f, row.keys.get(1).width, 0.01f);
    Assert.assertEquals(50f, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(10f, row.keys.get(0).width, 0.01f);
    // nothing to do when the edge is already there
    Assert.assertFalse(row.moveEdge(1, 51f));
    // never past the neighbour: each key keeps one step
    Assert.assertTrue(row.moveEdge(1, 0f));
    Assert.assertEquals(CustomLayout.WIDTH_STEP, row.keys.get(1).width, 0.01f);
    Assert.assertTrue(row.moveEdge(1, 500f));
    Assert.assertEquals(CustomLayout.WIDTH_STEP, row.keys.get(2).width, 0.01f);
    Assert.assertEquals(100f, row.totalWidth(), 0.01f);
  }

  @Test
  public void testStoredLayoutIsAKeyboard() {
    final int keyboardsBefore = mFactory.getAllAddOns().size();
    final CustomLayout layout = captureEnglish();
    mFactory.saveCustomLayout(layout);

    Assert.assertEquals(keyboardsBefore + 1, mFactory.getAllAddOns().size());
    final KeyboardAddOnAndBuilder addOn = mFactory.getAddOnById("layout-test");
    Assert.assertNotNull(addOn);
    Assert.assertEquals("Mine", addOn.getName());
    Assert.assertEquals(mEnglish.getKeyboardLocale(), addOn.getKeyboardLocale());
    Assert.assertFalse(mFactory.isAddOnEnabled("layout-test"));
    Assert.assertNotNull(load(addOn).getCustomLayout());
    Assert.assertNull(load(mEnglish).getCustomLayout());

    // saving again replaces
    layout.name = "Renamed";
    mFactory.saveCustomLayout(layout);
    Assert.assertEquals(keyboardsBefore + 1, mFactory.getAllAddOns().size());
    Assert.assertEquals("Renamed", mFactory.getAddOnById("layout-test").getName());
    Assert.assertEquals("Renamed", mFactory.getCustomLayout("layout-test").name);

    mFactory.setAddOnEnabled("layout-test", true);
    final List<String> enabled = mFactory.getEnabledIds();
    Assert.assertTrue(enabled.contains("layout-test"));

    mFactory.deleteCustomLayout("layout-test");
    Assert.assertEquals(keyboardsBefore, mFactory.getAllAddOns().size());
    Assert.assertNull(mFactory.getAddOnById("layout-test"));
    Assert.assertNull(mFactory.getCustomLayout("layout-test"));
  }

  @Test
  public void testLayoutsWrittenByOthersAreSeen() throws Exception {
    // as restoring a backup does: the preference changes without the factory being asked
    Assert.assertNull(mFactory.getAddOnById("layout-test"));
    final JSONArray stored = new JSONArray().put(captureEnglish().toJson());
    DirectBootAwareSharedPreferences.create(getApplicationContext())
        .edit()
        .putString("keyboard_custom_layouts", stored.toString())
        .commit();
    TestRxSchedulers.drainAllTasks();
    Assert.assertNotNull(mFactory.getAddOnById("layout-test"));
  }

  @Test
  public void testLayoutOfMissingKeyboardIsKeptAndNotOffered() {
    final int keyboardsBefore = mFactory.getAllAddOns().size();
    mFactory.saveCustomLayout(new CustomLayout("layout-orphan", "Orphan", "no-such-keyboard"));
    Assert.assertEquals(keyboardsBefore, mFactory.getAllAddOns().size());
    Assert.assertNull(mFactory.getAddOnById("layout-orphan"));
    Assert.assertNotNull(mFactory.getCustomLayout("layout-orphan"));
  }

  @Test
  public void testARowCanBeForSomeFieldsOnly() throws Exception {
    final CustomLayout layout = captureEnglish();
    final int rows = layout.rows.size();
    // a second bottom row, for web addresses; the first one for everything else
    final CustomLayout.Row forUrl = layout.rows.get(rows - 1).copy();
    forUrl.fields = 1 << Keyboard.KEYBOARD_ROW_MODE_URL;
    forUrl.keys.set(0, new CustomLayout.Key('~', forUrl.keys.get(0).width));
    layout.rows.get(rows - 1).setShownIn(Keyboard.KEYBOARD_ROW_MODE_URL, false);
    layout.rows.add(forUrl);
    Assert.assertTrue(layout.rows.get(rows - 1).showsIn(Keyboard.KEYBOARD_ROW_MODE_EMAIL));
    Assert.assertFalse(layout.rows.get(rows - 1).showsIn(Keyboard.KEYBOARD_ROW_MODE_URL));
    Assert.assertEquals(
        layout.totalHeight(Keyboard.KEYBOARD_ROW_MODE_NORMAL),
        layout.totalHeight(Keyboard.KEYBOARD_ROW_MODE_URL),
        0.001f);
    Assert.assertTrue(layout.contains('~', Keyboard.KEYBOARD_ROW_MODE_URL));
    Assert.assertFalse(layout.contains('~', Keyboard.KEYBOARD_ROW_MODE_NORMAL));

    final AnyKeyboard normal = load(mEnglish.withLayout(layout, 1));
    final AnyKeyboard url = load(mEnglish.withLayout(layout, 1), Keyboard.KEYBOARD_ROW_MODE_URL);
    Assert.assertNull(findKey(normal, '~'));
    Assert.assertNotNull(findKey(url, '~'));
    Assert.assertEquals(normal.getHeight(), url.getHeight());
    Assert.assertEquals(findKey(normal, KeyCodes.SPACE).y, findKey(url, '~').y);
    // a row that is left out has no places; the others keep theirs
    Assert.assertEquals(0, normal.getCustomLayoutSlots()[rows].length);
    Assert.assertEquals(0, url.getCustomLayoutSlots()[rows - 1].length);
    Assert.assertEquals(forUrl.keys.size(), url.getCustomLayoutSlots()[rows].length);
    // a row made for the field is not given the / key by itself
    Assert.assertNull(findKey(url, '/'));

    final CustomLayout read = CustomLayout.fromJson(new JSONObject(layout.toJson().toString()));
    Assert.assertEquals(forUrl.fields, read.rows.get(rows).fields);
    Assert.assertEquals(layout.rows.get(rows - 1).fields, read.rows.get(rows - 1).fields);
    Assert.assertEquals(CustomLayout.ALL_FIELDS, read.rows.get(0).fields);
    Assert.assertFalse(layout.toJson().getJSONArray("rows").getJSONObject(0).has("fields"));
  }

  @Test
  public void testARowStaysInOneFieldAtLeast() {
    final CustomLayout.Row row = row(50, 50);
    for (int field : CustomLayout.FIELDS) row.setShownIn(field, false);
    Assert.assertNotEquals(0, row.fields);
    Assert.assertTrue(row.showsIn(Keyboard.KEYBOARD_ROW_MODE_PASSWORD));
  }
}
