package com.anysoftkeyboard.ui.settings.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.List;

/**
 * A seek bar that marks some values on its track, names them, and settles on a marked value when
 * the thumb is let near one, while still allowing every value between.
 *
 * <p>The names are drawn in the top and bottom padding, which the layout has to leave room for.
 */
public class TickSeekBar extends AppCompatSeekBar {
  private static final class Tick {
    final int value;
    final String[] lines;
    final boolean emphasized;

    Tick(int value, String label, boolean emphasized) {
      this.value = value;
      this.lines = label.split("\n");
      this.emphasized = emphasized;
    }
  }

  private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final List<Tick> mTicks = new ArrayList<>();
  private final float mDensity;
  private final int mPlainColor;
  private final int mEmphasisColor;

  public TickSeekBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    mDensity = getResources().getDisplayMetrics().density;
    mPaint.setTextAlign(Paint.Align.CENTER);
    mPaint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
    mPaint.setStrokeWidth(1.5f * mDensity);
    mPlainColor = ContextCompat.getColor(context, R.color.custom_layout_muted);
    mEmphasisColor = ContextCompat.getColor(context, R.color.custom_layout_accent);
  }

  public void clearTicks() {
    mTicks.clear();
    invalidate();
  }

  /**
   * Marks {@code value} (in the units of {@link #getProgress()}). Plain marks are named under the
   * track, emphasized ones above it.
   */
  public void addTick(int value, @NonNull String label, boolean emphasized) {
    mTicks.add(new Tick(value, label, emphasized));
    invalidate();
  }

  /** {@code progress}, or the marked value it is near to. */
  public int snap(int progress) {
    final int reach = Math.max(1, getMax() / 50);
    for (Tick tick : mTicks) {
      if (Math.abs(tick.value - progress) <= reach) return tick.value;
    }
    return progress;
  }

  @Override
  protected synchronized void onDraw(Canvas canvas) {
    final float trackWidth = getWidth() - getPaddingLeft() - getPaddingRight();
    final float trackY =
        getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    final float lineHeight = mPaint.getTextSize() * 1.2f;
    int plainIndex = 0;
    for (Tick tick : mTicks) {
      final float x = getPaddingLeft() + trackWidth * tick.value / getMax();
      mPaint.setColor(tick.emphasized ? mEmphasisColor : mPlainColor);
      final float reach = (tick.emphasized ? 8 : 5) * mDensity;
      canvas.drawLine(x, trackY - reach, x, trackY + reach, mPaint);
      // names under the track take turns on two levels, so neighbours do not run into each other
      float textY =
          tick.emphasized
              ? trackY - 14 * mDensity - (tick.lines.length - 1) * lineHeight
              : trackY + 20 * mDensity + (plainIndex++ % 2) * 2 * lineHeight;
      for (String line : tick.lines) {
        canvas.drawText(line, x, textY, mPaint);
        textY += lineHeight;
      }
    }
    super.onDraw(canvas);
  }
}
