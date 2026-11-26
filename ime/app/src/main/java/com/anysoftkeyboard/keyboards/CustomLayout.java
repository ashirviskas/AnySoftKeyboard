package com.anysoftkeyboard.keyboards;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A keyboard arrangement made by the user: which keys are in which row, how wide each key is and
 * how tall each row is.
 *
 * <p>It is a complete description, not a list of changes to another layout. A key is named by its
 * code only; when the keyboard is loaded, the key with that code is taken from the keyboard the
 * layout is based on (with its label, popup and shifted codes), see {@link KeyboardLayoutArranger}.
 * What the user set for a key themselves (see {@link Key}) replaces what that key has.
 */
public final class CustomLayout {
  /** Key widths are in percent of the row; the editor moves key edges in steps of this size. */
  public static final float WIDTH_STEP = 5f;

  /** Row heights are in "rows": 1 is a normal key row of the current theme. */
  public static final float MIN_ROW_HEIGHT = 0.5f;

  public static final float MAX_ROW_HEIGHT = 1.5f;

  /** The widest gap between keys or rows a layout can ask for, in dp. */
  public static final float MAX_GAP = 16f;

  /** The code of an entry that only takes space, unless it has a text to type. */
  public static final int GAP = 0;

  /** For the gaps between keys and rows: as the theme has them. */
  public static final float THEME_GAP = -1f;

  /**
   * The kinds of text field a keyboard is shown for (the {@link Keyboard.KeyboardRowModeId}s). A
   * row can be limited to some of them, see {@link Row#fields}.
   */
  public static final int[] FIELDS = {
    Keyboard.KEYBOARD_ROW_MODE_NORMAL,
    Keyboard.KEYBOARD_ROW_MODE_IM,
    Keyboard.KEYBOARD_ROW_MODE_URL,
    Keyboard.KEYBOARD_ROW_MODE_EMAIL,
    Keyboard.KEYBOARD_ROW_MODE_PASSWORD
  };

  /** The names of {@link #FIELDS} in the JSON form. */
  private static final String[] FIELD_NAMES = {"text", "chat", "url", "email", "password"};

  public static final int ALL_FIELDS = bitsOfAllFields();

  private static final float ROW_WIDTH = 100f;

  private static int bitsOfAllFields() {
    int bits = 0;
    for (int field : FIELDS) bits |= 1 << field;
    return bits;
  }

  @NonNull public final String id;
  @NonNull public String name;

  /** The id of the keyboard add-on that provides the keys and the typing behaviour. */
  @NonNull public final String baseId;

  /** The locale of the dictionary to suggest from; null for the one of the base keyboard. */
  @Nullable public String dictionary;

  /** The gap between keys and between rows, in dp, or {@link #THEME_GAP}. */
  public float keyGap = THEME_GAP;

  public float rowGap = THEME_GAP;

  public final List<Row> rows = new ArrayList<>();

  public CustomLayout(@NonNull String id, @NonNull String name, @NonNull String baseId) {
    this.id = id;
    this.name = name;
    this.baseId = baseId;
  }

  /**
   * A place in a row: a key, or a gap. Besides its code and width a key can have values of its own;
   * each of them is null as long as the key is as its keyboard made it.
   */
  public static final class Key {
    public final int code;
    public float width;

    /** What the key types instead of its code: any text. A key can be this alone (code 0). */
    @Nullable public String text;

    /** What the key types with Shift on. */
    @Nullable public String shifted;

    /** What is written on the key. */
    @Nullable public String label;

    /** The small text in the corner of the key. */
    @Nullable public String hint;

    /** The long-press characters; empty for none. */
    @Nullable public String popup;

    /** Whether the key repeats while it is held. */
    @Nullable public Boolean repeats;

    /** Whether the key looks like a function key (Shift, Enter) instead of a letter. */
    @Nullable public Boolean functional;

    /**
     * Whether the width is held: what shares the room of the row (keys added and removed, side
     * gaps, even widths, a neighbour that is resized) leaves this entry as wide as it is, and it is
     * not resized itself.
     */
    public boolean locked;

    public Key(int code, float width) {
      this.code = code;
      this.width = width;
    }

    public boolean isGap() {
      return code == GAP && text == null;
    }

    /** Whether the user set anything for this key besides its place and width. */
    public boolean hasOwnValues() {
      return text != null
          || shifted != null
          || label != null
          || hint != null
          || popup != null
          || repeats != null
          || functional != null;
    }

    /** A second entry like this one, {@code width} wide. */
    @NonNull
    public Key copy(float width) {
      final Key copy = new Key(code, width);
      copy.text = text;
      copy.shifted = shifted;
      copy.label = label;
      copy.hint = hint;
      copy.popup = popup;
      copy.repeats = repeats;
      copy.functional = functional;
      copy.locked = locked;
      return copy;
    }

    @NonNull
    Object toJson() throws JSONException {
      final JSONArray json = new JSONArray().put(code).put(Math.round(width * 100) / 100.0);
      if (hasOwnValues() || locked) {
        json.put(
            new JSONObject()
                .putOpt("locked", locked ? true : null)
                .putOpt("text", text)
                .putOpt("shifted", shifted)
                .putOpt("label", label)
                .putOpt("hint", hint)
                .putOpt("popup", popup)
                .putOpt("repeats", repeats)
                .putOpt("functional", functional));
      }
      return json;
    }

    @NonNull
    static Key fromJson(@NonNull JSONArray json) throws JSONException {
      final Key key = new Key(json.getInt(0), size(json.getDouble(1)));
      final JSONObject own = json.optJSONObject(2);
      if (own != null) {
        key.text = string(own, "text");
        key.shifted = string(own, "shifted");
        key.label = string(own, "label");
        key.hint = string(own, "hint");
        key.popup = string(own, "popup");
        key.repeats = own.has("repeats") ? own.getBoolean("repeats") : null;
        key.functional = own.has("functional") ? own.getBoolean("functional") : null;
        key.locked = own.optBoolean("locked");
      } else if (json.length() > 2) {
        // the first form of a layout had the long-press characters here
        key.popup = json.getString(2);
      }
      return key;
    }

    @Nullable
    private static String string(JSONObject json, String name) throws JSONException {
      return json.has(name) ? json.getString(name) : null;
    }
  }

  public static final class Row {
    public float height = 1f;

    /**
     * The fields this row is shown for: one bit per entry of {@link #FIELDS}. A row for all fields
     * is the usual case; a row for some of them is how a layout gets, for example, a bottom row of
     * its own for web addresses.
     */
    public int fields = ALL_FIELDS;

    public final List<Key> keys = new ArrayList<>();

    /** Whether the row is part of the keyboard shown for the field kind {@code mode}. */
    public boolean showsIn(@Keyboard.KeyboardRowModeId int mode) {
      return (fields & (1 << mode)) != 0;
    }

    /** Shows or hides the row for the field kind {@code mode}; it stays in one field at least. */
    public void setShownIn(@Keyboard.KeyboardRowModeId int mode, boolean shown) {
      final int changed = shown ? fields | (1 << mode) : fields & ~(1 << mode);
      if (changed != 0) fields = changed;
    }

    /** A second row like this one. */
    @NonNull
    public Row copy() {
      final Row copy = new Row();
      copy.height = height;
      copy.fields = fields;
      for (Key key : keys) copy.keys.add(key.copy(key.width));
      return copy;
    }

    public float totalWidth() {
      float total = 0;
      for (Key key : keys) total += key.width;
      return total;
    }

    /**
     * Adds a key without changing the width of the row. The room comes from the row's stretch key
     * (see {@link #stretchKey}) if it has one, otherwise from all keys that are not locked. The key
     * takes half of the row at most, so the keys that are there keep some room.
     */
    public void add(int index, @NonNull Key key) {
      if (keys.isEmpty()) {
        key.width = ROW_WIDTH;
      } else {
        key.width = Math.min(key.width, totalWidth() / 2);
        final Key stretch = stretchKey();
        if (stretch != null && stretch.width - key.width >= key.width) {
          stretch.width -= key.width;
        } else {
          fit(totalWidth() - key.width);
        }
      }
      keys.add(index, key);
    }

    /**
     * Halves the entry at {@code index} and puts a gap into the half that became free, after it.
     * The gap is the place for another key.
     */
    public void split(int index) {
      final Key key = keys.get(index);
      key.width /= 2;
      keys.add(index + 1, new Key(GAP, key.width));
    }

    /** Removes a key; its width goes to the row's stretch key, otherwise to all keys. */
    @NonNull
    public Key remove(int index) {
      final float total = totalWidth();
      final Key removed = keys.remove(index);
      final Key stretch = stretchKey();
      if (stretch != null) {
        stretch.width += removed.width;
      } else if (!keys.isEmpty()) {
        fit(total);
      }
      return removed;
    }

    /**
     * Moves the edge between the key at {@code index} and the next one to {@code position} (percent
     * of the row, from its start), in whole steps and leaving each of the two keys at least one
     * step wide.
     *
     * @return whether anything changed.
     */
    public boolean moveEdge(int index, float position) {
      return moveEdge(index, position, WIDTH_STEP);
    }

    /** As {@link #moveEdge(int, float)}, settling on multiples of {@code step}. */
    public boolean moveEdge(int index, float position, float step) {
      final Key before = keys.get(index);
      final Key after = keys.get(index + 1);
      if (before.locked || after.locked) return false;
      float start = 0;
      for (int i = 0; i < index; i++) start += keys.get(i).width;
      final float end = start + before.width + after.width;
      final float snapped = Math.round(position / step) * step;
      final float edge = Math.max(start + WIDTH_STEP, Math.min(end - WIDTH_STEP, snapped));
      if (end - start < 2 * WIDTH_STEP || Math.abs(edge - start - before.width) < 0.01f) {
        return false;
      }
      before.width = edge - start;
      after.width = end - edge;
      return true;
    }

    /** The width of the gap at the start ({@code left}) or the end of the row; 0 for none. */
    public float getSideGap(boolean left) {
      if (keys.size() < 2) return 0;
      final Key side = keys.get(left ? 0 : keys.size() - 1);
      return side.isGap() ? side.width : 0;
    }

    /**
     * Gives the row a gap of {@code left} at its start and of {@code right} at its end (none for
     * 0). What is between them is squeezed or stretched to fit, keeping its proportions.
     */
    public void setSideGaps(float left, float right) {
      final float total = totalWidth();
      if (!keys.isEmpty() && keys.get(0).isGap()) keys.remove(0);
      if (!keys.isEmpty() && keys.get(keys.size() - 1).isGap()) keys.remove(keys.size() - 1);
      if (keys.isEmpty()) {
        keys.add(new Key(GAP, total));
        return;
      }
      fit(total - left - right);
      if (left > 0) keys.add(0, new Key(GAP, left));
      if (right > 0) keys.add(new Key(GAP, right));
    }

    /**
     * Moves the edge between the key at {@code index} and the next one to {@code position}, as
     * {@link #moveEdge(int, float)} does, but the room comes from (or goes to) all keys on one side
     * of the edge, which end up equally wide: the keys after the edge, or the keys before it. Gaps
     * and locked keys on that side keep their width.
     *
     * @return whether anything changed.
     */
    public boolean moveEdge(int index, float position, boolean evenAfter) {
      return moveEdge(index, position, evenAfter, WIDTH_STEP, false);
    }

    /**
     * As {@link #moveEdge(int, float, boolean)}, settling on multiples of {@code step}. With {@code
     * keepProportions} the keys of that side do not end up equal: each keeps its share of the side
     * (a space bar stays a space bar), so moving the edge back brings every width back.
     */
    public boolean moveEdge(
        int index, float position, boolean evenAfter, float step, boolean keepProportions) {
      float edge = 0;
      for (int i = 0; i <= index; i++) edge += keys.get(i).width;
      final Key resized = keys.get(evenAfter ? index : index + 1);
      final List<Key> side = new ArrayList<>();
      float sideWidth = 0;
      for (int i = evenAfter ? index + 1 : 0; i < (evenAfter ? keys.size() : index + 1); i++) {
        if (keys.get(i).isGap() || keys.get(i).locked) continue;
        side.add(keys.get(i));
        sideWidth += keys.get(i).width;
      }
      if (side.isEmpty() || resized.locked) return false;
      final float snapped = Math.round(position / step) * step;
      // how much the resized key grows; every key keeps at least one step
      float grow = evenAfter ? snapped - edge : edge - snapped;
      float narrowest = Float.MAX_VALUE;
      for (Key key : side) narrowest = Math.min(narrowest, key.width);
      // in proportion, the narrowest key of the side is the first to be one step wide
      grow =
          Math.min(
              grow,
              keepProportions
                  ? sideWidth * Math.max(0, 1 - WIDTH_STEP / narrowest)
                  : sideWidth - side.size() * WIDTH_STEP);
      grow = Math.max(grow, WIDTH_STEP - resized.width);
      final float factor = (sideWidth - grow) / sideWidth;
      final float each = (sideWidth - grow) / side.size();
      boolean changed = Math.abs(grow) >= 0.01f;
      for (Key key : side) {
        final float width = keepProportions ? key.width * factor : each;
        changed |= Math.abs(key.width - width) >= 0.01f;
        key.width = width;
      }
      resized.width += grow;
      return changed;
    }

    /**
     * Gives the keys at {@code indices} the same width, within the room they have together. Locked
     * ones stay as they are.
     */
    public void evenWidths(@NonNull List<Integer> indices) {
      final List<Key> sharing = new ArrayList<>();
      float total = 0;
      for (int index : indices) {
        final Key key = keys.get(index);
        if (key.locked) continue;
        sharing.add(key);
        total += key.width;
      }
      for (Key key : sharing) key.width = total / sharing.size();
    }

    /**
     * Makes each of the keys at {@code indices} {@code width} wide (at least one step, and no wider
     * than the rest of the row allows). The other entries share what is left, each keeping its
     * share; locked ones, picked or not, stay as they are.
     *
     * @return whether anything changed.
     */
    public boolean setWidths(@NonNull List<Integer> indices, float width) {
      final List<Key> sized = new ArrayList<>();
      final List<Key> others = new ArrayList<>();
      float room = 0;
      float othersWidth = 0;
      float narrowest = Float.MAX_VALUE;
      for (int index = 0; index < keys.size(); index++) {
        final Key key = keys.get(index);
        if (key.locked) continue;
        room += key.width;
        if (indices.contains(index)) {
          sized.add(key);
        } else {
          others.add(key);
          othersWidth += key.width;
          narrowest = Math.min(narrowest, key.width);
        }
      }
      if (sized.isEmpty() || others.isEmpty()) return false;
      // the narrowest of the others is the first to be one step wide
      final float leastForOthers = othersWidth * Math.min(1, WIDTH_STEP / narrowest);
      width = Math.max(WIDTH_STEP, Math.min((room - leastForOthers) / sized.size(), width));
      final float factor = (room - width * sized.size()) / othersWidth;
      boolean changed = false;
      for (Key key : sized) {
        changed |= Math.abs(key.width - width) >= 0.01f;
        key.width = width;
      }
      if (changed) {
        for (Key key : others) key.width *= factor;
      }
      return changed;
    }

    /** Gives every key of the row the same width. Gaps and locked keys stay as they are. */
    public void evenWidths() {
      final List<Integer> sharing = new ArrayList<>();
      for (int index = 0; index < keys.size(); index++) {
        if (!keys.get(index).isGap()) sharing.add(index);
      }
      evenWidths(sharing);
    }

    /**
     * The key that gives and takes room when keys are added and removed: the widest one, when it is
     * clearly wider than the rest (a space bar) and not locked. Rows of equal keys have none.
     */
    @Nullable
    private Key stretchKey() {
      Key widest = null;
      float narrowest = Float.MAX_VALUE;
      for (Key key : keys) {
        if (!key.locked && (widest == null || key.width > widest.width)) widest = key;
        narrowest = Math.min(narrowest, key.width);
      }
      return widest != null && widest.width >= 2 * narrowest ? widest : null;
    }

    /**
     * Makes the row {@code total} wide. The entries that are not locked share, in their
     * proportions, the room the locked ones leave; when that is none, the locked give way too.
     */
    private void fit(float total) {
      float locked = 0;
      float free = 0;
      for (Key key : keys) {
        if (key.locked) locked += key.width;
        else free += key.width;
      }
      final boolean hold = free > 0 && total > locked;
      final float factor = hold ? (total - locked) / free : total / (locked + free);
      for (Key key : keys) {
        if (!hold || !key.locked) key.width *= factor;
      }
    }
  }

  public boolean contains(int code) {
    for (Row row : rows) {
      for (Key key : row.keys) {
        if (key.code == code) return true;
      }
    }
    return false;
  }

  /** Whether a row that is shown for the field kind {@code mode} has the key {@code code}. */
  public boolean contains(int code, @Keyboard.KeyboardRowModeId int mode) {
    for (Row row : rows) {
      if (!row.showsIn(mode)) continue;
      for (Key key : row.keys) {
        if (key.code == code) return true;
      }
    }
    return false;
  }

  /** The height, in rows, of the keyboard shown for the field kind {@code mode}. */
  public float totalHeight(@Keyboard.KeyboardRowModeId int mode) {
    float total = 0;
    for (Row row : rows) {
      if (row.showsIn(mode)) total += row.height;
    }
    return total;
  }

  /** A copy of the rows of this layout under another identity. */
  @NonNull
  public CustomLayout copy(@NonNull String newId, @NonNull String newName) {
    final CustomLayout copy = new CustomLayout(newId, newName, baseId);
    try {
      copy.readSettingsAndRows(toJson());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    return copy;
  }

  /**
   * The arrangement {@code keyboard} has right now, generic rows included, so that a new layout
   * starts as what the user already sees.
   */
  @NonNull
  public static CustomLayout capture(
      @NonNull String id, @NonNull String name, @NonNull AnyKeyboard keyboard) {
    final CustomLayout current = keyboard.getCustomLayout();
    if (current != null) return current.copy(id, name);

    final CustomLayout layout = new CustomLayout(id, name, keyboard.getKeyboardAddOn().getId());
    final KeyboardDimens dimens = keyboard.getKeyboardDimens();
    final float percent = ROW_WIDTH / keyboard.mDisplayWidth;
    final float halfGap = dimens.getKeyHorizontalGap() / 2;
    final float normalHeight = dimens.getNormalKeyHeight() * keyboard.mKeysHeightFactor;
    Row row = null;
    int rowY = Integer.MIN_VALUE;
    float rowEnd = 0;
    for (Keyboard.Key key : keyboard.getKeys()) {
      if (row == null || key.y != rowY) {
        closeRow(row, rowEnd);
        row = new Row();
        row.height = 0;
        layout.rows.add(row);
        rowY = key.y;
        rowEnd = 0;
      }
      final float start = (key.x - halfGap) * percent;
      if (start - rowEnd >= 1f) row.keys.add(new Key(GAP, round(start - rowEnd)));
      rowEnd = (key.x + key.width + halfGap) * percent;
      final Key entry = new Key(key.getPrimaryCode(), round((key.width + 2 * halfGap) * percent));
      // a key without a code is known by the text it types
      if (entry.code == GAP && key.text != null) entry.text = key.text.toString();
      row.keys.add(entry);
      row.height =
          Math.max(
              row.height,
              Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, round(key.height / normalHeight))));
    }
    closeRow(row, rowEnd);
    return layout;
  }

  private static void closeRow(@Nullable Row row, float rowEnd) {
    if (row != null && ROW_WIDTH - rowEnd >= 1f)
      row.keys.add(new Key(GAP, round(ROW_WIDTH - rowEnd)));
  }

  private static float round(float value) {
    return Math.round(value * 100) / 100f;
  }

  @NonNull
  public JSONObject toJson() throws JSONException {
    final JSONArray jsonRows = new JSONArray();
    for (Row row : rows) {
      final JSONArray jsonKeys = new JSONArray();
      for (Key key : row.keys) jsonKeys.put(key.toJson());
      final JSONObject jsonRow = new JSONObject().put("height", (double) row.height);
      if (row.fields != ALL_FIELDS) {
        final JSONArray jsonFields = new JSONArray();
        for (int i = 0; i < FIELDS.length; i++) {
          if (row.showsIn(FIELDS[i])) jsonFields.put(FIELD_NAMES[i]);
        }
        jsonRow.put("fields", jsonFields);
      }
      jsonRows.put(jsonRow.put("keys", jsonKeys));
    }
    final JSONObject json =
        new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("base", baseId)
            .putOpt("dictionary", dictionary);
    if (keyGap != THEME_GAP) json.put("keyGap", (double) keyGap);
    if (rowGap != THEME_GAP) json.put("rowGap", (double) rowGap);
    return json.put("rows", jsonRows);
  }

  @NonNull
  public static CustomLayout fromJson(@NonNull JSONObject json) throws JSONException {
    final CustomLayout layout =
        new CustomLayout(json.getString("id"), json.getString("name"), json.getString("base"));
    layout.readSettingsAndRows(json);
    return layout;
  }

  /** Takes the dictionary, the gaps and the rows of the layout {@code json} describes. */
  public void readSettingsAndRows(@NonNull JSONObject json) throws JSONException {
    final List<Row> read = new ArrayList<>();
    readRows(json.getJSONArray("rows"), read);
    if (read.isEmpty()) throw new JSONException("no rows");
    dictionary = json.has("dictionary") ? json.getString("dictionary") : null;
    keyGap = gap(json, "keyGap");
    rowGap = gap(json, "rowGap");
    rows.clear();
    rows.addAll(read);
  }

  private static float gap(JSONObject json, String name) throws JSONException {
    final double gap = json.optDouble(name, THEME_GAP);
    return gap >= 0 && gap <= MAX_GAP ? (float) gap : THEME_GAP;
  }

  private static void readRows(JSONArray jsonRows, List<Row> into) throws JSONException {
    for (int rowIndex = 0; rowIndex < jsonRows.length(); rowIndex++) {
      final JSONObject jsonRow = jsonRows.getJSONObject(rowIndex);
      final Row row = new Row();
      row.height =
          Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, size(jsonRow.getDouble("height"))));
      final JSONArray jsonFields = jsonRow.optJSONArray("fields");
      if (jsonFields != null) {
        int fields = 0;
        for (int i = 0; i < jsonFields.length(); i++) {
          for (int field = 0; field < FIELDS.length; field++) {
            if (FIELD_NAMES[field].equals(jsonFields.getString(i))) fields |= 1 << FIELDS[field];
          }
        }
        // names this version does not know leave nothing: such a row is shown everywhere
        if (fields != 0) row.fields = fields;
      }
      final JSONArray jsonKeys = jsonRow.getJSONArray("keys");
      for (int keyIndex = 0; keyIndex < jsonKeys.length(); keyIndex++) {
        row.keys.add(Key.fromJson(jsonKeys.getJSONArray(keyIndex)));
      }
      if (row.keys.isEmpty()) throw new JSONException("a row without keys");
      into.add(row);
    }
  }

  /** A width or height that was read: the text may come from anywhere (a file, the clipboard). */
  private static float size(double value) throws JSONException {
    if (!(value > 0) || Double.isInfinite(value)) throw new JSONException("not a size: " + value);
    return (float) value;
  }
}
