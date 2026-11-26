package com.anysoftkeyboard.keyboards;

/** The dimensions of another {@link KeyboardDimens}, with gaps between keys and rows of its own. */
final class KeyboardDimensWithGaps implements KeyboardDimens {
  private final KeyboardDimens mDimens;
  private final float mKeyHorizontalGap;
  private final float mRowVerticalGap;

  KeyboardDimensWithGaps(KeyboardDimens dimens, float keyHorizontalGap, float rowVerticalGap) {
    mDimens = dimens;
    mKeyHorizontalGap = keyHorizontalGap;
    mRowVerticalGap = rowVerticalGap;
  }

  @Override
  public int getKeyboardMaxWidth() {
    return mDimens.getKeyboardMaxWidth();
  }

  @Override
  public float getKeyHorizontalGap() {
    return mKeyHorizontalGap;
  }

  @Override
  public float getRowVerticalGap() {
    return mRowVerticalGap;
  }

  @Override
  public int getNormalKeyHeight() {
    return mDimens.getNormalKeyHeight();
  }

  @Override
  public int getSmallKeyHeight() {
    return mDimens.getSmallKeyHeight();
  }

  @Override
  public int getLargeKeyHeight() {
    return mDimens.getLargeKeyHeight();
  }

  @Override
  public float getPaddingBottom() {
    return mDimens.getPaddingBottom();
  }
}
