/*
 * Copyright (c) 2013 Menny Even-Danan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anysoftkeyboard.ui.settings;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.anysoftkeyboard.addons.AddOnsFactory;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.keyboards.KeyboardAddOnAndBuilder;
import com.anysoftkeyboard.keyboards.KeyboardFactory;
import com.anysoftkeyboard.keyboards.views.DemoAnyKeyboardView;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.evendanan.pixel.UiUtils;

public class KeyboardAddOnBrowserFragment
    extends AbstractAddOnsBrowserFragment<KeyboardAddOnAndBuilder> {

  private static final String ARG_LAYOUT_GROUP = "layout_group";

  public KeyboardAddOnBrowserFragment() {
    super(
        "LanguageAddOnBrowserFragment",
        R.string.keyboards_group,
        false,
        false,
        false,
        ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
  }

  @NonNull
  @Override
  protected AddOnsFactory<KeyboardAddOnAndBuilder> getAddOnFactory() {
    return AnyApplication.getKeyboardFactory(getContext());
  }

  @Nullable
  @Override
  protected String getMarketSearchKeyword() {
    return getLayoutGroup() == null ? "language" : null;
  }

  @Override
  protected int getMarketSearchTitle() {
    return R.string.search_market_for_keyboard_addons;
  }

  /**
   * Arguments to list the layouts of one keyboard (see {@link
   * KeyboardAddOnAndBuilder#getLayoutGroupId}) instead of the keyboards.
   */
  @NonNull
  public static Bundle createArgs(@NonNull String layoutGroupId) {
    final Bundle args = new Bundle();
    args.putString(ARG_LAYOUT_GROUP, layoutGroupId);
    return args;
  }

  @Nullable
  private String getLayoutGroup() {
    return getArguments() == null ? null : getArguments().getString(ARG_LAYOUT_GROUP);
  }

  private KeyboardFactory getKeyboardFactory() {
    return AnyApplication.getKeyboardFactory(requireContext());
  }

  /** The keyboards: one card each. The layouts of a keyboard: on its own screen. */
  @NonNull
  @Override
  protected List<KeyboardAddOnAndBuilder> getAddOnsToShow() {
    final String group = getLayoutGroup();
    if (group != null) return getKeyboardFactory().getLayoutsOf(group);
    final List<KeyboardAddOnAndBuilder> keyboards = new ArrayList<>();
    for (KeyboardAddOnAndBuilder addOn : getKeyboardFactory().getAllAddOns()) {
      // a variant is found under its keyboard; if that one is not installed, it is listed itself
      final String itsGroup = addOn.getLayoutGroupId();
      if (itsGroup.equals(addOn.getId()) || getKeyboardFactory().getAddOnById(itsGroup) == null) {
        keyboards.add(addOn);
      }
    }
    return keyboards;
  }

  /**
   * Among the keyboards, the layouts of a keyboard go where its card goes. Among the layouts of one
   * keyboard, they change places with each other and every other keyboard stays where it is.
   */
  @NonNull
  @Override
  protected List<KeyboardAddOnAndBuilder> getOrderOfAllAddOns(
      @NonNull List<KeyboardAddOnAndBuilder> shown) {
    final String group = getLayoutGroup();
    final List<KeyboardAddOnAndBuilder> all = new ArrayList<>();
    if (group == null) {
      for (KeyboardAddOnAndBuilder keyboard : shown) {
        all.addAll(getKeyboardFactory().getLayoutsOf(keyboard.getLayoutGroupId()));
      }
    } else {
      final Iterator<KeyboardAddOnAndBuilder> layouts = shown.iterator();
      for (KeyboardAddOnAndBuilder addOn : getKeyboardFactory().getAllAddOns()) {
        all.add(
            addOn.getLayoutGroupId().equals(group) && layouts.hasNext() ? layouts.next() : addOn);
      }
    }
    return all;
  }

  /** Whether {@code addOn} is listed here for itself and its other layouts. */
  private boolean standsForItsLayouts(@NonNull KeyboardAddOnAndBuilder addOn) {
    return getLayoutGroup() == null && getKeyboardFactory().getLayoutsOf(addOn.getId()).size() > 1;
  }

  @Override
  protected boolean isShownAsEnabled(@NonNull KeyboardAddOnAndBuilder addOn) {
    if (!standsForItsLayouts(addOn)) return super.isShownAsEnabled(addOn);
    for (KeyboardAddOnAndBuilder layout : getKeyboardFactory().getLayoutsOf(addOn.getId())) {
      if (getKeyboardFactory().isAddOnEnabled(layout.getId())) return true;
    }
    return false;
  }

  /** A keyboard with several layouts opens them; which of them are in use is chosen there. */
  @Override
  protected boolean onAddOnClicked(@NonNull KeyboardAddOnAndBuilder addOn) {
    if (!standsForItsLayouts(addOn)) return false;
    onEditAddOnClicked(addOn);
    return true;
  }

  @Override
  protected boolean isEditable(@NonNull KeyboardAddOnAndBuilder addOn) {
    return true;
  }

  @NonNull
  @Override
  protected CharSequence getEditLabel(@NonNull KeyboardAddOnAndBuilder addOn) {
    if (!standsForItsLayouts(addOn)) return super.getEditLabel(addOn);
    final int layouts = getKeyboardFactory().getLayoutsOf(addOn.getId()).size();
    return getResources().getQuantityString(R.plurals.custom_layout_layouts, layouts, layouts);
  }

  /** A keyboard that stands for several layouts opens them; any other is customized. */
  @Override
  protected void onEditAddOnClicked(@NonNull KeyboardAddOnAndBuilder addOn) {
    if (standsForItsLayouts(addOn)) {
      Navigation.findNavController(requireView())
          .navigate(R.id.action_keyboardAddOnBrowserFragment_self, createArgs(addOn.getId()));
    } else {
      editLayoutOf(addOn);
    }
  }

  /** A pack's keyboard is copied into a new layout and that is edited; the user's own is edited. */
  private void editLayoutOf(@NonNull KeyboardAddOnAndBuilder addOn) {
    Navigation.findNavController(requireView())
        .navigate(
            R.id.action_keyboardAddOnBrowserFragment_to_customLayoutEditorFragment,
            CustomLayoutEditorFragment.createArgs(addOn.getId()));
  }

  /**
   * The empty card at the end, among the keyboards and among the layouts of a keyboard: a new
   * layout, starting as a copy of one that is there.
   */
  @Override
  protected boolean canCreateAddOns() {
    return true;
  }

  @Override
  protected void onCreateAddOnClicked() {
    // the ones in use first: a new layout most likely starts from one of them
    final List<KeyboardAddOnAndBuilder> choices = new ArrayList<>();
    for (KeyboardAddOnAndBuilder addOn : getAddOnsToShow()) {
      if (getKeyboardFactory().isAddOnEnabled(addOn.getId())) choices.add(addOn);
    }
    for (KeyboardAddOnAndBuilder addOn : getAddOnsToShow()) {
      if (!choices.contains(addOn)) choices.add(addOn);
    }
    if (choices.size() == 1) {
      editLayoutOf(choices.get(0));
      return;
    }
    final CharSequence[] names = new CharSequence[choices.size()];
    for (int i = 0; i < names.length; i++) names[i] = choices.get(i).getName();
    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.custom_layout_start_from)
        .setItems(names, (dialog, which) -> editLayoutOf(choices.get(which)))
        .show();
  }

  @Override
  public void onStart() {
    super.onStart();
    final KeyboardAddOnAndBuilder keyboard =
        getLayoutGroup() == null ? null : getKeyboardFactory().getAddOnById(getLayoutGroup());
    if (keyboard != null) UiUtils.setActivityTitle(this, keyboard.getName());
  }

  @Override
  protected void applyAddOnToDemoKeyboardView(
      @NonNull KeyboardAddOnAndBuilder addOn, @NonNull DemoAnyKeyboardView demoKeyboardView) {
    AnyKeyboard defaultKeyboard = addOn.createKeyboard(Keyboard.KEYBOARD_ROW_MODE_NORMAL);
    defaultKeyboard.loadKeyboard(demoKeyboardView.getThemedKeyboardDimens());
    demoKeyboardView.setKeyboard(defaultKeyboard, null, null);
  }
}
