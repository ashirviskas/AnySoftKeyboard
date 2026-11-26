package com.anysoftkeyboard.keyboards.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.CustomLayout;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Shows a keyboard that has a {@link CustomLayout} and lets the user pick a key or a row of it, and
 * (when {@link #setEditable editable}) drag a key to another place and drag the edges of the picked
 * key to resize it. The layout object is changed in place; the owner is told and loads the keyboard
 * again.
 */
public class CustomLayoutEditorView extends DemoAnyKeyboardView {
  public static final int NOTHING = -1;

  public interface Listener {
    /** The picked row or key changed (see {@link #getSelectedRow}, {@link #getSelectedKey}). */
    void onSelectionChanged();

    /** The layout was changed by a gesture. */
    void onLayoutChanged();
  }

  private enum Gesture {
    NONE,
    TAP,
    MOVE,
    RESIZE
  }

  private final Paint mEditPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final DashPathEffect mDashes;
  private final float mDensity;
  private final int mTouchSlop;
  private final int mAccentColor;

  @Nullable private Listener mListener;
  private boolean mEditable;
  private int mSelectedRow = NOTHING;
  // sorted; several only while picking several is on
  private final TreeSet<Integer> mSelectedKeys = new TreeSet<>();
  private boolean mPickSeveral;
  private boolean mResizeEvensSide;

  private Gesture mGesture = Gesture.NONE;
  private int mGestureCount;
  private float mDownX;
  private float mDownY;
  private int mGestureRow = NOTHING;
  private int mGestureKey = NOTHING;
  // RESIZE: the key before the dragged edge. MOVE: where the key would be dropped.
  private int mTargetRow = NOTHING;
  private int mTargetKey = NOTHING;
  // RESIZE: whether the dragged edge is the right one of the picked key
  private boolean mResizeAfter;

  public CustomLayoutEditorView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
    mDashes = new DashPathEffect(new float[] {4 * mDensity, 3 * mDensity}, 0);
    mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    mAccentColor = ContextCompat.getColor(context, R.color.custom_layout_accent);
  }

  public void setListener(@Nullable Listener listener) {
    mListener = listener;
  }

  /** Whether keys can be dragged and resized; otherwise touches only pick. */
  public void setEditable(boolean editable) {
    mEditable = editable;
    invalidate();
  }

  /** Picks a key, or a whole row with {@code key} = {@link #NOTHING}. */
  public void setSelection(int row, int key) {
    mSelectedRow = row;
    mSelectedKeys.clear();
    if (key != NOTHING) mSelectedKeys.add(key);
    invalidate();
  }

  /** While on, a tap on a key of the picked row adds it to the picked keys, or takes it out. */
  public void setPickSeveral(boolean pickSeveral) {
    mPickSeveral = pickSeveral;
  }

  /**
   * While on, dragging an edge of the picked key resizes all keys on that side of it, to equal
   * widths; otherwise only the neighbour.
   */
  public void setResizeEvensSide(boolean resizeEvensSide) {
    mResizeEvensSide = resizeEvensSide;
  }

  /** Counts the touches so far; changes with the same count came from one drag. */
  public int getGestureCount() {
    return mGestureCount;
  }

  public int getSelectedRow() {
    return mSelectedRow;
  }

  /** The picked key when exactly one is picked. */
  public int getSelectedKey() {
    return mSelectedKeys.size() == 1 ? mSelectedKeys.first() : NOTHING;
  }

  /** All picked keys of the picked row, left to right. */
  @NonNull
  public List<Integer> getSelectedKeys() {
    return new ArrayList<>(mSelectedKeys);
  }

  @NonNull
  private Rect[][] slots() {
    final AnyKeyboard keyboard = getKeyboard();
    return keyboard == null ? new Rect[0][] : keyboard.getCustomLayoutSlots();
  }

  @Nullable
  private CustomLayout layout() {
    final AnyKeyboard keyboard = getKeyboard();
    return keyboard == null ? null : keyboard.getCustomLayout();
  }

  private float keyboardX(MotionEvent event) {
    return event.getX() / getKeyboardScale() - getPaddingLeft();
  }

  private float keyboardY(MotionEvent event) {
    return event.getY() / getKeyboardScale() - getPaddingTop();
  }

  /** The row at {@code y}; touches between rows and beyond the last row count for the nearest. */
  private int rowAt(float y) {
    final Rect[][] slots = slots();
    int nearest = NOTHING;
    float nearestDistance = Float.MAX_VALUE;
    for (int row = 0; row < slots.length; row++) {
      if (slots[row].length == 0) continue;
      final Rect first = slots[row][0];
      final float distance = y < first.top ? first.top - y : Math.max(0, y - first.bottom);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = row;
      }
    }
    return nearest;
  }

  /**
   * The row a dragged key would land in. It leaves its own row only when the finger is clearly in
   * another one: a drag along a row wanders up and down a little.
   */
  private int dropRowAt(float y) {
    final Rect own = slots()[mGestureRow][0];
    final float margin = own.height() / 3f;
    return y > own.top - margin && y < own.bottom + margin ? mGestureRow : rowAt(y);
  }

  /** How many entries of {@code row} have their middle before {@code x}. */
  private int entriesBefore(int row, float x) {
    int count = 0;
    for (Rect slot : slots()[row]) {
      if (slot.centerX() < x) count++;
    }
    return count;
  }

  private int keyAt(int row, float x) {
    final Rect[] slots = slots()[row];
    for (int key = 0; key < slots.length; key++) {
      if (x < slots[key].right || key == slots.length - 1) return key;
    }
    return NOTHING;
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    final CustomLayout layout = layout();
    if (layout == null) return false;
    final float x = keyboardX(event);
    final float y = keyboardY(event);
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        mGestureCount++;
        mGestureRow = rowAt(y);
        if (mGestureRow == NOTHING) return false;
        mGestureKey = keyAt(mGestureRow, x);
        mDownX = x;
        mDownY = y;
        mGesture = Gesture.TAP;
        final int single = getSelectedKey();
        if (mEditable && mGestureRow == mSelectedRow && single != NOTHING) {
          // the edges of the picked key are handles, also from just outside the key
          final Rect picked = slots()[mSelectedRow][single];
          final float reach = 18 * mDensity;
          final boolean last = single == slots()[mSelectedRow].length - 1;
          if (!last && Math.abs(x - picked.right) < reach) {
            mGesture = Gesture.RESIZE;
            mResizeAfter = true;
            mTargetKey = single;
          } else if (single > 0 && Math.abs(x - picked.left) < reach) {
            mGesture = Gesture.RESIZE;
            mResizeAfter = false;
            mTargetKey = single - 1;
          }
        }
        getParent().requestDisallowInterceptTouchEvent(mEditable);
        return true;
      case MotionEvent.ACTION_MOVE:
        if (mGesture == Gesture.RESIZE) {
          final CustomLayout.Row row = layout.rows.get(mGestureRow);
          final float position = x * row.totalWidth() / getKeyboard().getMinWidth();
          final boolean changed =
              mResizeEvensSide
                  ? row.moveEdge(mTargetKey, position, mResizeAfter)
                  : row.moveEdge(mTargetKey, position);
          if (changed && mListener != null) mListener.onLayoutChanged();
        } else if (mGesture == Gesture.TAP
            && mEditable
            && Math.hypot(x - mDownX, y - mDownY) > 2 * mTouchSlop) {
          mGesture = Gesture.MOVE;
          setSelection(mGestureRow, mGestureKey);
          if (mListener != null) mListener.onSelectionChanged();
        }
        if (mGesture == Gesture.MOVE) {
          mTargetRow = dropRowAt(y);
          mTargetKey = entriesBefore(mTargetRow, x);
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (mGesture == Gesture.TAP) {
          if (mEditable && mPickSeveral && mGestureRow == mSelectedRow) {
            if (!mSelectedKeys.remove(mGestureKey)) mSelectedKeys.add(mGestureKey);
          } else {
            setSelection(mGestureRow, mGestureKey);
          }
          if (mListener != null) mListener.onSelectionChanged();
        } else if (mGesture == Gesture.MOVE) {
          moveKey(layout, mGestureRow, mGestureKey, mTargetRow, mTargetKey);
        }
        mGesture = Gesture.NONE;
        invalidate();
        return true;
      case MotionEvent.ACTION_CANCEL:
        mGesture = Gesture.NONE;
        invalidate();
        return true;
      default:
        return true;
    }
  }

  private void moveKey(CustomLayout layout, int fromRow, int fromKey, int toRow, int toKey) {
    final CustomLayout.Row source = layout.rows.get(fromRow);
    if (fromRow == toRow) {
      // inside a row the keys keep their widths
      if (toKey > fromKey) toKey--;
      if (toKey == fromKey) return;
      source.keys.add(toKey, source.keys.remove(fromKey));
    } else {
      layout.rows.get(toRow).add(toKey, source.remove(fromKey));
      // a row whose last key was dragged away stays, as a gap: rows go only when asked to
      if (source.keys.isEmpty()) source.keys.add(new CustomLayout.Key(CustomLayout.GAP, 100));
    }
    setSelection(toRow, toKey);
    if (mListener != null) {
      mListener.onLayoutChanged();
      mListener.onSelectionChanged();
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    final Rect[][] slots = slots();
    final CustomLayout layout = layout();
    if (layout == null || slots.length != layout.rows.size()) return;

    canvas.save();
    canvas.translate(getPaddingLeft(), getPaddingTop());
    mEditPaint.setColor(mAccentColor);
    mEditPaint.setStyle(Paint.Style.STROKE);

    // gaps have no key to show them
    mEditPaint.setStrokeWidth(mDensity);
    mEditPaint.setPathEffect(mDashes);
    mEditPaint.setAlpha(140);
    for (int row = 0; row < slots.length; row++) {
      for (int key = 0; key < slots[row].length; key++) {
        if (layout.rows.get(row).keys.get(key).code == CustomLayout.GAP) {
          canvas.drawRect(slots[row][key], mEditPaint);
        }
      }
    }
    mEditPaint.setPathEffect(null);
    mEditPaint.setAlpha(255);

    if (mSelectedRow >= 0 && mSelectedRow < slots.length && slots[mSelectedRow].length > 0) {
      final Rect[] row = slots[mSelectedRow];
      mEditPaint.setStrokeWidth(2.5f * mDensity);
      final int single = getSelectedKey();
      if (mSelectedKeys.isEmpty() || mSelectedKeys.last() >= row.length) {
        canvas.drawRect(
            row[0].left, row[0].top, row[row.length - 1].right, row[0].bottom, mEditPaint);
      } else if (single == NOTHING) {
        for (int picked : mSelectedKeys) canvas.drawRect(row[picked], mEditPaint);
      } else {
        final Rect key = row[single];
        canvas.drawRect(key, mEditPaint);
        if (mEditable) {
          // a ruler of width steps along the row, and a handle on each edge that can be dragged
          mEditPaint.setStrokeWidth(mDensity);
          final float width = getKeyboard().getMinWidth();
          final int steps = Math.round(100 / CustomLayout.WIDTH_STEP);
          for (int step = 0; step <= steps; step++) {
            final float tickX = width * step / steps;
            canvas.drawLine(tickX, key.top - 5 * mDensity, tickX, key.top, mEditPaint);
          }
          mEditPaint.setStyle(Paint.Style.FILL);
          final float handle = 3 * mDensity;
          final float inset = key.height() / 4f;
          if (single > 0) {
            canvas.drawRect(
                key.left - handle,
                key.top + inset,
                key.left + handle,
                key.bottom - inset,
                mEditPaint);
          }
          if (single < row.length - 1) {
            canvas.drawRect(
                key.right - handle,
                key.top + inset,
                key.right + handle,
                key.bottom - inset,
                mEditPaint);
          }
        }
      }
    }

    if (mGesture == Gesture.MOVE && mTargetRow != NOTHING) {
      final Rect[] row = slots[mTargetRow];
      final float dropX =
          mTargetKey < row.length ? row[mTargetKey].left : row[row.length - 1].right;
      mEditPaint.setStyle(Paint.Style.FILL);
      canvas.drawRect(
          dropX - 2 * mDensity, row[0].top, dropX + 2 * mDensity, row[0].bottom, mEditPaint);
    }
    canvas.restore();
  }
}
