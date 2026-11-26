package com.anysoftkeyboard.keyboards;

import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.keyboards.AnyKeyboard.AnyKey;
import java.util.Iterator;
import java.util.List;

/**
 * Places keys the way a {@link CustomLayout} says. The keys themselves are not built here: each
 * entry of the layout takes the loaded key that has its code, so a key keeps what its keyboard gave
 * it (label, popup, shifted codes). An entry whose key was taken already gets a copy of it, and a
 * character or text no loaded key has becomes a plain key. What the entry has of its own (see
 * {@link CustomLayout.Key}) is then put on the key.
 */
final class KeyboardLayoutArranger {
  private KeyboardLayoutArranger() {}

  /**
   * @param available the loaded keys to take from; the taken ones are removed, so what is left
   *     afterwards are the keys the layout does not use.
   * @param placed receives the keys of the layout, top to bottom and left to right.
   * @param plainKeysRow the row new plain keys are attached to.
   * @param normalKeyHeight the height, in pixels, of a row of height 1.
   * @param mode the kind of field the keyboard is for; rows that are not for it are left out.
   * @return the place of every entry of the layout (also of the gaps, which have no key), indexed
   *     as the rows and keys of the layout. A row that is left out has no places.
   */
  @NonNull
  static Rect[][] arrange(
      @NonNull CustomLayout layout,
      @NonNull List<Keyboard.Key> available,
      @NonNull List<Keyboard.Key> placed,
      @NonNull Keyboard.Row plainKeysRow,
      @NonNull KeyboardDimens dimens,
      int keyboardWidth,
      float normalKeyHeight,
      @Keyboard.KeyboardRowModeId int mode) {
    final float halfGap = dimens.getKeyHorizontalGap() / 2;
    final Rect[][] slots = new Rect[layout.rows.size()][];
    int addressKey = addressKeyFor(mode);
    int lastShownRow = -1;
    for (int rowIndex = 0; rowIndex < slots.length; rowIndex++) {
      if (layout.rows.get(rowIndex).showsIn(mode)) lastShownRow = rowIndex;
    }
    float y = dimens.getRowVerticalGap();
    for (int rowIndex = 0; rowIndex < slots.length; rowIndex++) {
      final CustomLayout.Row row = layout.rows.get(rowIndex);
      if (!row.showsIn(mode)) {
        slots[rowIndex] = new Rect[0];
        continue;
      }
      // a bottom row made for some fields only is left as the user made it
      final boolean bottomRow = rowIndex == lastShownRow && row.fields == CustomLayout.ALL_FIELDS;
      final int height = Math.round(normalKeyHeight * row.height);
      final float pixelsPerWidth = keyboardWidth / Math.max(1f, row.totalWidth());
      slots[rowIndex] = new Rect[row.keys.size()];
      float x = 0;
      for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++) {
        final CustomLayout.Key entry = row.keys.get(keyIndex);
        final float width = entry.width * pixelsPerWidth;
        final Rect slot =
            new Rect((int) (x + halfGap), (int) y, (int) (x + width - halfGap), (int) y + height);
        slots[rowIndex][keyIndex] = slot;
        x += width;

        // Address fields: the first punctuation key of the bottom row types / or @ instead,
        // as the generic bottom rows do.
        final boolean becomesAddressKey = bottomRow && addressKey != 0 && isPunctuation(entry.code);
        final int code = becomesAddressKey ? addressKey : entry.code;
        if (becomesAddressKey) addressKey = 0;

        final Keyboard.Key key =
            entry.isGap() ? null : take(available, placed, code, entry.text, plainKeysRow, dimens);
        if (key == null) continue;
        key.setBounds(slot.left, slot.top, slot.width(), slot.height());
        if (!becomesAddressKey && key instanceof AnyKey) ((AnyKey) key).customize(entry);
        placed.add(key);
      }
      y += height + dimens.getRowVerticalGap();
    }
    return slots;
  }

  private static int addressKeyFor(@Keyboard.KeyboardRowModeId int mode) {
    switch (mode) {
      case Keyboard.KEYBOARD_ROW_MODE_URL:
        return '/';
      case Keyboard.KEYBOARD_ROW_MODE_EMAIL:
        return '@';
      default:
        return 0;
    }
  }

  private static boolean isPunctuation(int code) {
    return code > ' ' && !Character.isLetterOrDigit(code);
  }

  /**
   * The key for {@code code}; a key without a code (0) is known by the {@code text} it types. Null
   * for a function key that no loaded keyboard has (anymore).
   */
  @Nullable
  private static Keyboard.Key take(
      List<Keyboard.Key> available,
      List<Keyboard.Key> placed,
      int code,
      @Nullable String text,
      Keyboard.Row plainKeysRow,
      KeyboardDimens dimens) {
    for (Iterator<Keyboard.Key> iterator = available.iterator(); iterator.hasNext(); ) {
      final Keyboard.Key key = iterator.next();
      if (isKeyFor(key, code, text)) {
        iterator.remove();
        return key;
      }
    }
    for (Keyboard.Key key : placed) {
      if (isKeyFor(key, code, text) && key instanceof AnyKey) {
        return new AnyKey((AnyKey) key, plainKeysRow, dimens);
      }
    }
    return code >= 0 ? new AnyKey(plainKeysRow, dimens, code) : null;
  }

  private static boolean isKeyFor(Keyboard.Key key, int code, @Nullable String text) {
    return key.getPrimaryCode() == code
        && (code != CustomLayout.GAP || (key.text != null && key.text.toString().equals(text)));
  }
}
