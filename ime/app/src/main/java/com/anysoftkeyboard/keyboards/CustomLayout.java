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
 */
public final class CustomLayout {
  /** Key widths are in percent of the row; the editor moves key edges in steps of this size. */
  public static final float WIDTH_STEP = 5f;

  /** Row heights are in "rows": 1 is a normal key row of the current theme. */
  public static final float MIN_ROW_HEIGHT = 0.5f;

  public static final float MAX_ROW_HEIGHT = 1.5f;

  /** The code of an entry that only takes space. */
  public static final int GAP = 0;

  private static final float ROW_WIDTH = 100f;

  @NonNull public final String id;
  @NonNull public String name;

  /** The id of the keyboard add-on that provides the keys and the typing behaviour. */
  @NonNull public final String baseId;

  public final List<Row> rows = new ArrayList<>();

  public CustomLayout(@NonNull String id, @NonNull String name, @NonNull String baseId) {
    this.id = id;
    this.name = name;
    this.baseId = baseId;
  }

  public static final class Key {
    public final int code;
    public float width;

    /** Long-press characters set by the user; null keeps the ones of the original key. */
    @Nullable public String popup;

    public Key(int code, float width) {
      this.code = code;
      this.width = width;
    }
  }

  public static final class Row {
    public float height = 1f;
    public final List<Key> keys = new ArrayList<>();

    public float totalWidth() {
      float total = 0;
      for (Key key : keys) total += key.width;
      return total;
    }

    /**
     * Adds a key without changing the width of the row. The room comes from the row's stretch key
     * (see {@link #stretchKey}) if it has one, otherwise from all keys.
     */
    public void add(int index, @NonNull Key key) {
      if (keys.isEmpty()) {
        key.width = ROW_WIDTH;
      } else {
        final Key stretch = stretchKey();
        if (stretch != null && stretch.width - key.width >= key.width) {
          stretch.width -= key.width;
        } else {
          final float total = totalWidth();
          scale((total - key.width) / total);
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
        scale(total / totalWidth());
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

    /** The width of the gap the row has on both of its sides; 0 when a side has none. */
    public float getSideGaps() {
      if (keys.size() < 3) return 0;
      final Key first = keys.get(0);
      final Key last = keys.get(keys.size() - 1);
      return first.code == GAP && last.code == GAP ? Math.min(first.width, last.width) : 0;
    }

    /**
     * Gives the row a gap of {@code width} on each side (none for 0). What is between them is
     * squeezed or stretched to fit, keeping its proportions.
     */
    public void setSideGaps(float width) {
      final float total = totalWidth();
      if (!keys.isEmpty() && keys.get(0).code == GAP) keys.remove(0);
      if (!keys.isEmpty() && keys.get(keys.size() - 1).code == GAP) keys.remove(keys.size() - 1);
      if (keys.isEmpty()) {
        keys.add(new Key(GAP, total));
        return;
      }
      scale((total - 2 * width) / totalWidth());
      if (width > 0) {
        keys.add(0, new Key(GAP, width));
        keys.add(new Key(GAP, width));
      }
    }

    /**
     * Moves the edge between the key at {@code index} and the next one to {@code position}, as
     * {@link #moveEdge(int, float)} does, but the room comes from (or goes to) all keys on one side
     * of the edge, which end up equally wide: the keys after the edge, or the keys before it. Gaps
     * on that side keep their width.
     *
     * @return whether anything changed.
     */
    public boolean moveEdge(int index, float position, boolean evenAfter) {
      return moveEdge(index, position, evenAfter, WIDTH_STEP);
    }

    /** As {@link #moveEdge(int, float, boolean)}, settling on multiples of {@code step}. */
    public boolean moveEdge(int index, float position, boolean evenAfter, float step) {
      float edge = 0;
      for (int i = 0; i <= index; i++) edge += keys.get(i).width;
      final Key resized = keys.get(evenAfter ? index : index + 1);
      final List<Key> side = new ArrayList<>();
      float sideWidth = 0;
      for (int i = evenAfter ? index + 1 : 0; i < (evenAfter ? keys.size() : index + 1); i++) {
        if (keys.get(i).code == GAP) continue;
        side.add(keys.get(i));
        sideWidth += keys.get(i).width;
      }
      if (side.isEmpty()) return false;
      final float snapped = Math.round(position / step) * step;
      // how much the resized key grows; every key keeps at least one step
      float grow = evenAfter ? snapped - edge : edge - snapped;
      grow = Math.min(grow, sideWidth - side.size() * WIDTH_STEP);
      grow = Math.max(grow, WIDTH_STEP - resized.width);
      final float each = (sideWidth - grow) / side.size();
      boolean changed = Math.abs(grow) >= 0.01f;
      for (Key key : side) {
        changed |= Math.abs(key.width - each) >= 0.01f;
        key.width = each;
      }
      resized.width += grow;
      return changed;
    }

    /** Gives the keys at {@code indices} the same width, within the room they have together. */
    public void evenWidths(@NonNull List<Integer> indices) {
      float total = 0;
      for (int index : indices) total += keys.get(index).width;
      for (int index : indices) keys.get(index).width = total / indices.size();
    }

    /** Gives every key of the row the same width. Gaps stay as they are. */
    public void evenWidths() {
      float gaps = 0;
      int keyCount = 0;
      for (Key key : keys) {
        if (key.code == GAP) gaps += key.width;
        else keyCount++;
      }
      final float width = (totalWidth() - gaps) / Math.max(1, keyCount);
      for (Key key : keys) {
        if (key.code != GAP) key.width = width;
      }
    }

    /**
     * The key that gives and takes room when keys are added and removed: the widest one, when it is
     * clearly wider than the rest (a space bar). Rows of equal keys have none.
     */
    @Nullable
    private Key stretchKey() {
      Key widest = null;
      float narrowest = Float.MAX_VALUE;
      for (Key key : keys) {
        if (widest == null || key.width > widest.width) widest = key;
        narrowest = Math.min(narrowest, key.width);
      }
      return widest != null && widest.width >= 2 * narrowest ? widest : null;
    }

    private void scale(float factor) {
      for (Key key : keys) key.width *= factor;
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

  public float totalHeight() {
    float total = 0;
    for (Row row : rows) total += row.height;
    return total;
  }

  /** A copy of the rows of this layout under another identity. */
  @NonNull
  public CustomLayout copy(@NonNull String newId, @NonNull String newName) {
    final CustomLayout copy = new CustomLayout(newId, newName, baseId);
    try {
      readRows(toJson().getJSONArray("rows"), copy);
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
      row.keys.add(new Key(key.getPrimaryCode(), round((key.width + 2 * halfGap) * percent)));
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
      for (Key key : row.keys) {
        final JSONArray jsonKey = new JSONArray().put(key.code).put((double) key.width);
        if (key.popup != null) jsonKey.put(key.popup);
        jsonKeys.put(jsonKey);
      }
      jsonRows.put(new JSONObject().put("height", (double) row.height).put("keys", jsonKeys));
    }
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("base", baseId)
        .put("rows", jsonRows);
  }

  @NonNull
  public static CustomLayout fromJson(@NonNull JSONObject json) throws JSONException {
    final CustomLayout layout =
        new CustomLayout(json.getString("id"), json.getString("name"), json.getString("base"));
    readRows(json.getJSONArray("rows"), layout);
    return layout;
  }

  private static void readRows(JSONArray jsonRows, CustomLayout into) throws JSONException {
    for (int rowIndex = 0; rowIndex < jsonRows.length(); rowIndex++) {
      final JSONObject jsonRow = jsonRows.getJSONObject(rowIndex);
      final Row row = new Row();
      row.height = (float) jsonRow.getDouble("height");
      final JSONArray jsonKeys = jsonRow.getJSONArray("keys");
      for (int keyIndex = 0; keyIndex < jsonKeys.length(); keyIndex++) {
        final JSONArray jsonKey = jsonKeys.getJSONArray(keyIndex);
        final Key key = new Key(jsonKey.getInt(0), (float) jsonKey.getDouble(1));
        if (jsonKey.length() > 2) key.popup = jsonKey.getString(2);
        row.keys.add(key);
      }
      into.rows.add(row);
    }
  }
}
