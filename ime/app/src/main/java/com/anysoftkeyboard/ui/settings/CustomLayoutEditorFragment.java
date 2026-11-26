package com.anysoftkeyboard.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;
import com.anysoftkeyboard.api.KeyCodes;
import com.anysoftkeyboard.keyboards.AnyKeyboard;
import com.anysoftkeyboard.keyboards.CondenseType;
import com.anysoftkeyboard.keyboards.CustomLayout;
import com.anysoftkeyboard.keyboards.Keyboard;
import com.anysoftkeyboard.keyboards.KeyboardAddOnAndBuilder;
import com.anysoftkeyboard.keyboards.KeyboardFactory;
import com.anysoftkeyboard.keyboards.views.CustomLayoutEditorView;
import com.anysoftkeyboard.ui.settings.widget.TickSeekBar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.menny.android.anysoftkeyboard.AnyApplication;
import com.menny.android.anysoftkeyboard.R;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;
import net.evendanan.pixel.UiUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Lets the user arrange a keyboard of their own. The keyboard is shown whole and is what the user
 * works on; the tabs are named after what is changed: a key (which one, where, how wide), a row
 * (height, side gaps, the fields it is shown for) or the whole keyboard (name, heights, sharing).
 * The keyboard can be looked at as it is for each kind of field, and split. The result is stored as
 * a {@link CustomLayout} and shows up as one more keyboard.
 */
public class CustomLayoutEditorFragment extends Fragment
    implements CustomLayoutEditorView.Listener {
  private static final String ARG_KEYBOARD_ID = "keyboard_id";
  private static final String STATE_LAYOUT = "layout";

  private static final int TAB_KEY = 0;
  private static final int TAB_ROW = 1;
  private static final int TAB_KEYBOARD = 2;

  /** Whether the keyboard is shown under the controls instead of over them; kept for the app. */
  private static final String PREF_PREVIEW_AT_BOTTOM = "custom_layout_editor_keyboard_at_bottom";

  // the edges of the picked key, in percent of the row: by slider, and typed
  private static final float EDGE_SLIDER_STEP = 0.5f;
  private static final float EDGE_TYPED_STEP = 0.1f;

  // the gap on each side of a row, in percent of the row
  private static final float SIDE_GAP_STEP = 2.5f;
  private static final float MAX_SIDE_GAP = 25f;
  private static final float NEW_KEY_WIDTH = 2 * CustomLayout.WIDTH_STEP;

  /** The row heights the size slider marks, next to the heights of the other rows. */
  private static final float[] STANDARD_HEIGHTS = {0.67f, 0.75f, 1f, 1.13f};

  private static final int[] STANDARD_HEIGHT_NAMES = {
    R.string.custom_layout_height_short,
    R.string.custom_layout_height_compact,
    R.string.custom_layout_height_normal,
    R.string.custom_layout_height_tall
  };

  /** The names of {@link CustomLayout#FIELDS}. */
  private static final int[] FIELD_NAMES = {
    R.string.custom_layout_field_text,
    R.string.custom_layout_field_chat,
    R.string.custom_layout_field_url,
    R.string.custom_layout_field_email,
    R.string.custom_layout_field_password
  };

  /** The ways the keys can be squeezed that the keyboard can be looked at in. */
  private static final CondenseType[] SHAPES = {
    CondenseType.None, CondenseType.Split, CondenseType.CompactToLeft, CondenseType.CompactToRight
  };

  private static final int[] SHAPE_NAMES = {
    R.string.custom_layout_shape_whole,
    R.string.custom_layout_shape_split,
    R.string.custom_layout_shape_left,
    R.string.custom_layout_shape_right
  };

  /** In the row list of the Size tab: the entry that stands for every row but the bottom one. */
  private static final int ALL_LETTER_ROWS = -2;

  private KeyboardFactory mFactory;
  private KeyboardAddOnAndBuilder mBase;
  private CustomLayout mLayout;
  private boolean mStored;

  private EditText mNameView;
  private TabLayout mTabs;
  private CustomLayoutEditorView mKeyboardView;
  private ChipGroup mUsualKeys;
  private ChipGroup mKeyboardKeys;
  private TextView mSelectionView;
  private TextView mSelectionHintView;
  private SeekBar mSideGaps;
  private SeekBar mLeftEdge;
  private SeekBar mRightEdge;
  private SeekBar mKeyWidth;
  private LinearLayout mSizeRows;
  private LinearLayout mRowHeight;
  private LinearLayout mPreviewSetups;

  // what the keyboard is shown as: for which kind of field, and whole or squeezed
  @Keyboard.KeyboardRowModeId private int mPreviewField = Keyboard.KEYBOARD_ROW_MODE_NORMAL;
  private CondenseType mPreviewShape = CondenseType.None;

  // the tab that is shown, and what was picked when the Key and Row tabs were left, to come back to
  private int mShownTab = TAB_KEY;
  private int mRememberedRow = CustomLayoutEditorView.NOTHING;
  private int mRememberedKey = CustomLayoutEditorView.NOTHING;

  // what the layout was before each change, newest first
  private final ArrayDeque<String> mUndo = new ArrayDeque<>();
  private String mSnapshot;
  @Nullable private String mLastChangeKind;

  /** Arguments to edit the keyboard {@code keyboardId}, or a new copy of it if it is not custom. */
  @NonNull
  public static Bundle createArgs(@NonNull String keyboardId) {
    final Bundle args = new Bundle();
    args.putString(ARG_KEYBOARD_ID, keyboardId);
    return args;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
    mFactory = AnyApplication.getKeyboardFactory(requireContext());
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.custom_layout_editor, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    mNameView = view.findViewById(R.id.custom_layout_name);
    mSelectionView = view.findViewById(R.id.custom_layout_selection);
    mSizeRows = view.findViewById(R.id.custom_layout_size_rows);
    mRowHeight = view.findViewById(R.id.custom_layout_row_height);
    mKeyboardView = view.findViewById(R.id.custom_layout_keyboard);
    mKeyboardView.setListener(this);
    mPreviewSetups = view.findViewById(R.id.custom_layout_preview_setups);
    mSelectionHintView = view.findViewById(R.id.custom_layout_selection_hint);
    mUsualKeys = view.findViewById(R.id.custom_layout_usual_keys);
    mKeyboardKeys = view.findViewById(R.id.custom_layout_keyboard_keys);

    final String keyboardId = requireArguments().getString(ARG_KEYBOARD_ID);
    final CustomLayout stored = mFactory.getCustomLayout(keyboardId);
    mStored = stored != null;
    try {
      if (savedInstanceState != null) {
        mLayout = CustomLayout.fromJson(new JSONObject(savedInstanceState.getString(STATE_LAYOUT)));
      } else if (stored != null) {
        mLayout = stored;
      } else {
        mLayout = captureAsNew(mFactory.getAddOnById(keyboardId));
      }
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    mBase = mFactory.getAddOnById(mLayout.baseId);
    mSnapshot = toJson();
    mNameView.setText(mLayout.name);

    view.findViewById(R.id.custom_layout_remove_key).setOnClickListener(v -> removeSelectedKeys());
    view.findViewById(R.id.custom_layout_long_press).setOnClickListener(v -> editLongPress());
    view.findViewById(R.id.custom_layout_add_row).setOnClickListener(v -> addRow(false));
    view.findViewById(R.id.custom_layout_add_row_above).setOnClickListener(v -> addRow(true));
    view.findViewById(R.id.custom_layout_even_row).setOnClickListener(v -> evenSelectedRow());
    view.findViewById(R.id.custom_layout_even_keys).setOnClickListener(v -> evenSelectedRow());
    view.findViewById(R.id.custom_layout_insert_left).setOnClickListener(v -> insertGap(false));
    view.findViewById(R.id.custom_layout_insert_right).setOnClickListener(v -> insertGap(true));
    ((CheckBox) view.findViewById(R.id.custom_layout_pick_several))
        .setOnCheckedChangeListener(
            (button, checked) -> {
              mKeyboardView.setPickSeveral(checked);
              // back to one key at most
              if (!checked && mKeyboardView.getSelectedKeys().size() > 1) {
                mKeyboardView.setSelection(selectedRow(), CustomLayoutEditorView.NOTHING);
                showLayout();
              }
            });
    final CheckBox resizeEvensSide = view.findViewById(R.id.custom_layout_resize_evens_side);
    resizeEvensSide.setOnCheckedChangeListener(
        (button, checked) -> mKeyboardView.setResizeEvensSide(checked));
    // on to begin with: a resized key leaves the others of its side equal
    resizeEvensSide.setChecked(true);
    mLeftEdge = view.findViewById(R.id.custom_layout_left_edge);
    mRightEdge = view.findViewById(R.id.custom_layout_right_edge);
    mKeyWidth = view.findViewById(R.id.custom_layout_key_width);
    setUpEdge(mLeftEdge, (value, step) -> moveEdgeOfSelected(false, value, step));
    setUpEdge(mRightEdge, (value, step) -> moveEdgeOfSelected(true, value, step));
    setUpEdge(mKeyWidth, this::setWidthOfSelected);
    view.findViewById(R.id.custom_layout_split_key).setOnClickListener(v -> splitSelectedKey());
    view.findViewById(R.id.custom_layout_go_to_row)
        .setOnClickListener(v -> mTabs.selectTab(mTabs.getTabAt(TAB_ROW)));
    view.findViewById(R.id.custom_layout_row_up).setOnClickListener(v -> stepRow(-1));
    view.findViewById(R.id.custom_layout_row_down).setOnClickListener(v -> stepRow(1));
    view.findViewById(R.id.custom_layout_duplicate_row).setOnClickListener(v -> duplicateRow());
    view.findViewById(R.id.custom_layout_move_row_up).setOnClickListener(v -> moveRow(-1));
    view.findViewById(R.id.custom_layout_move_row_down).setOnClickListener(v -> moveRow(1));
    for (int action :
        new int[] {
          R.id.custom_layout_copy,
          R.id.custom_layout_paste,
          R.id.custom_layout_export,
          R.id.custom_layout_import,
          R.id.custom_layout_reset
        }) {
      view.findViewById(action).setOnClickListener(v -> perform(action));
    }
    showPreviewPlace();
    showPreviewSetups();
    mSideGaps = view.findViewById(R.id.custom_layout_side_gaps);
    mSideGaps.setMax(Math.round(MAX_SIDE_GAP / SIDE_GAP_STEP));
    mSideGaps.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser || selectedRow() == CustomLayoutEditorView.NOTHING) return;
            final CustomLayout.Row row = mLayout.rows.get(selectedRow());
            // the picked key may move by one when a gap appears or goes
            final CustomLayout.Key picked = selectedKey();
            row.setSideGaps(progress * SIDE_GAP_STEP * row.totalWidth() / 100f);
            mKeyboardView.setSelection(
                selectedRow(),
                picked == null ? CustomLayoutEditorView.NOTHING : row.keys.indexOf(picked));
            noteChange("side gaps " + selectedRow());
            showLayout();
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    view.findViewById(R.id.custom_layout_remove_row).setOnClickListener(v -> removeSelectedRow());
    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(getViewLifecycleOwner(), mAskBeforeLeaving);

    mTabs = view.findViewById(R.id.custom_layout_tabs);
    mTabs.addTab(mTabs.newTab().setText(R.string.custom_layout_tab_key));
    mTabs.addTab(mTabs.newTab().setText(R.string.custom_layout_tab_row));
    mTabs.addTab(mTabs.newTab().setText(R.string.custom_layout_tab_keyboard));
    mTabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            showTab();
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {}
        });
    showTab();
  }

  /** Back with changes that were not saved asks before dropping them. */
  private final OnBackPressedCallback mAskBeforeLeaving =
      new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
          new AlertDialog.Builder(requireContext())
              .setMessage(R.string.custom_layout_discard_question)
              .setNegativeButton(R.string.custom_layout_keep_editing, null)
              .setPositiveButton(
                  R.string.custom_layout_discard,
                  (dialog, which) -> {
                    setEnabled(false);
                    Navigation.findNavController(requireView()).popBackStack();
                  })
              .show();
        }
      };

  @Override
  public void onStart() {
    super.onStart();
    UiUtils.setActivityTitle(this, getString(R.string.custom_layout_edit));
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    try {
      outState.putString(STATE_LAYOUT, mLayout.toJson().toString());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  @NonNull
  private AnyKeyboard load(@NonNull KeyboardAddOnAndBuilder addOn) {
    return load(addOn, Keyboard.KEYBOARD_ROW_MODE_NORMAL);
  }

  @NonNull
  private AnyKeyboard load(
      @NonNull KeyboardAddOnAndBuilder addOn, @Keyboard.KeyboardRowModeId int field) {
    final AnyKeyboard keyboard = addOn.createKeyboard(field);
    keyboard.loadKeyboard(mKeyboardView.getThemedKeyboardDimens());
    return keyboard;
  }

  // ---- what the keyboard is shown as ----

  /**
   * Shows the layout on the keyboard, as it is for the picked kind of field. Squeezed (split, to a
   * side) it is only looked at: the keys are then not where the layout puts them.
   */
  private void showKeyboard() {
    final AnyKeyboard keyboard = load(mBase.withLayout(mLayout, 0), mPreviewField);
    final boolean squeezed = mPreviewShape != CondenseType.None;
    if (squeezed) keyboard.setCondensedKeys(mPreviewShape, landscapeCondensing(mPreviewShape));
    mKeyboardView.setLookOnly(squeezed);
    requireView()
        .findViewById(R.id.custom_layout_preview_hint)
        .setVisibility(squeezed ? View.VISIBLE : View.GONE);
    mKeyboardView.setKeyboard(keyboard, null, null);
  }

  /**
   * The part of the width the keys are squeezed into when the screen is on its side. Upright, a
   * phone does not squeeze them at all, and there would be nothing to see.
   */
  private float landscapeCondensing(CondenseType shape) {
    final Configuration landscape = new Configuration(getResources().getConfiguration());
    landscape.orientation = Configuration.ORIENTATION_LANDSCAPE;
    return requireContext()
            .createConfigurationContext(landscape)
            .getResources()
            .getInteger(
                shape == CondenseType.Split
                    ? R.integer.condensing_precentage
                    : R.integer.condensing_precentage_edge)
        / 100f;
  }

  /** The choices over the keyboard: the kind of field, then whole or squeezed. */
  private void showPreviewSetups() {
    mPreviewSetups.removeAllViews();
    final int gap = Math.round(6 * getResources().getDisplayMetrics().density);
    for (int i = 0; i < CustomLayout.FIELDS.length; i++) {
      final int field = CustomLayout.FIELDS[i];
      final TextView chip =
          addChip(
              mPreviewSetups,
              getString(FIELD_NAMES[i]),
              "field:" + field,
              v -> setPreviewField(field));
      ((ViewGroup.MarginLayoutParams) chip.getLayoutParams()).setMarginEnd(gap);
      if (field == mPreviewField) markChip(chip);
    }
    for (int i = 0; i < SHAPES.length; i++) {
      final CondenseType shape = SHAPES[i];
      final TextView chip =
          addChip(
              mPreviewSetups,
              getString(SHAPE_NAMES[i]),
              "shape:" + shape,
              v -> {
                mPreviewShape = shape;
                showPreviewSetups();
                showLayout();
              });
      final ViewGroup.MarginLayoutParams params =
          (ViewGroup.MarginLayoutParams) chip.getLayoutParams();
      params.setMarginEnd(gap);
      // a wider gap sets the two groups apart
      if (i == 0) params.setMarginStart(2 * gap);
      if (shape == mPreviewShape) markChip(chip);
    }
  }

  /** Makes a chip look picked (the look of the keys that are not on the layout yet). */
  private void markChip(TextView chip) {
    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.custom_layout_accent));
    chip.setBackgroundResource(R.drawable.custom_layout_chip_missing);
  }

  private void setPreviewField(@Keyboard.KeyboardRowModeId int field) {
    if (mLayout.totalHeight(field) == 0) {
      Toast.makeText(
              requireContext(),
              getString(R.string.custom_layout_no_rows_for_field, fieldName(field)),
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    mPreviewField = field;
    // what is picked has to be on the keyboard that is shown
    final int row = selectedRow();
    if (row != CustomLayoutEditorView.NOTHING && !mLayout.rows.get(row).showsIn(field)) {
      mKeyboardView.setSelection(
          mShownTab == TAB_ROW ? lastShownRow() : CustomLayoutEditorView.NOTHING,
          CustomLayoutEditorView.NOTHING);
    }
    showPreviewSetups();
    showLayout();
  }

  @NonNull
  private String fieldName(@Keyboard.KeyboardRowModeId int field) {
    for (int i = 0; i < CustomLayout.FIELDS.length; i++) {
      if (CustomLayout.FIELDS[i] == field) return getString(FIELD_NAMES[i]);
    }
    throw new IllegalArgumentException("not a field: " + field);
  }

  /** The names of the fields {@code row} is shown for. */
  @NonNull
  private String fieldNames(@NonNull CustomLayout.Row row) {
    final StringBuilder names = new StringBuilder();
    for (int field : CustomLayout.FIELDS) {
      if (!row.showsIn(field)) continue;
      if (names.length() > 0) names.append(", ");
      names.append(fieldName(field));
    }
    return names.toString();
  }

  /** The lowest row of the keyboard that is shown. */
  private int lastShownRow() {
    for (int row = mLayout.rows.size() - 1; row >= 0; row--) {
      if (mLayout.rows.get(row).showsIn(mPreviewField)) return row;
    }
    return mLayout.rows.size() - 1;
  }

  /** Shows the keyboard for a field that has {@code rowIndex}, if the shown one does not. */
  private void showFieldOf(int rowIndex) {
    final CustomLayout.Row row = mLayout.rows.get(rowIndex);
    if (row.showsIn(mPreviewField)) return;
    for (int field : CustomLayout.FIELDS) {
      if (row.showsIn(field)) {
        mPreviewField = field;
        break;
      }
    }
    showPreviewSetups();
  }

  /** A new layout that starts as what {@code keyboard} looks like now. */
  @NonNull
  private CustomLayout captureAsNew(@NonNull KeyboardAddOnAndBuilder keyboard) {
    return CustomLayout.capture(
        "layout-" + UUID.randomUUID(),
        getString(R.string.custom_layout_default_name, keyboard.getName()),
        load(keyboard));
  }

  /**
   * Shows the tab that is picked: Key, Row or Keyboard. What is picked on the keyboard follows the
   * tab: a key (or keys) on Key, a whole row on Row, nothing on Keyboard. Coming back to Key or Row
   * finds what was picked there before.
   */
  private void showTab() {
    final int tab = mTabs.getSelectedTabPosition();
    if (selectedRow() != CustomLayoutEditorView.NOTHING) {
      if (mShownTab == TAB_KEY || mRememberedRow != selectedRow()) {
        mRememberedKey =
            mShownTab == TAB_KEY ? mKeyboardView.getSelectedKey() : CustomLayoutEditorView.NOTHING;
      }
      mRememberedRow = selectedRow();
    }
    mShownTab = tab;
    final View root = requireView();
    root.findViewById(R.id.custom_layout_key_panel)
        .setVisibility(tab == TAB_KEY ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.custom_layout_row_panel)
        .setVisibility(tab == TAB_ROW ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.custom_layout_keyboard_panel)
        .setVisibility(tab == TAB_KEYBOARD ? View.VISIBLE : View.GONE);
    mKeyboardView.setEditable(tab == TAB_KEY);

    final int rows = mLayout.rows.size();
    int row = selectedRow() != CustomLayoutEditorView.NOTHING ? selectedRow() : mRememberedRow;
    if (row >= rows) row = CustomLayoutEditorView.NOTHING;
    if (tab == TAB_KEY) {
      // a key can only be worked on where it is seen
      if (row != CustomLayoutEditorView.NOTHING && !mLayout.rows.get(row).showsIn(mPreviewField)) {
        row = CustomLayoutEditorView.NOTHING;
        mKeyboardView.setSelection(row, row);
      }
      // a key that was just tapped on the keyboard stays picked; otherwise the remembered one
      if (mKeyboardView.getSelectedKeys().isEmpty()) {
        final boolean remembered =
            row != CustomLayoutEditorView.NOTHING
                && row == mRememberedRow
                && mRememberedKey < mLayout.rows.get(row).keys.size();
        mKeyboardView.setSelection(
            row, remembered ? mRememberedKey : CustomLayoutEditorView.NOTHING);
      }
    } else if (tab == TAB_ROW) {
      mKeyboardView.setSelection(
          row == CustomLayoutEditorView.NOTHING ? lastShownRow() : row,
          CustomLayoutEditorView.NOTHING);
    } else {
      mKeyboardView.setSelection(CustomLayoutEditorView.NOTHING, CustomLayoutEditorView.NOTHING);
    }
    showLayout();
  }

  /** Puts the keyboard over the controls or under them, as the user chose last. */
  private void showPreviewPlace() {
    final boolean atBottom =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(PREF_PREVIEW_AT_BOTTOM, false);
    final LinearLayout root = requireView().findViewById(R.id.custom_layout_root);
    final View preview = root.findViewById(R.id.custom_layout_preview);
    root.removeView(preview);
    root.addView(preview, atBottom ? root.getChildCount() : 0);
  }

  private void flipPreviewPlace() {
    final SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(requireContext());
    preferences
        .edit()
        .putBoolean(PREF_PREVIEW_AT_BOTTOM, !preferences.getBoolean(PREF_PREVIEW_AT_BOTTOM, false))
        .apply();
    showPreviewPlace();
  }

  /** On the Row tab: picks the row above or under the picked one. */
  private void stepRow(int by) {
    final int row = Math.max(0, Math.min(mLayout.rows.size() - 1, selectedRow() + by));
    mKeyboardView.setSelection(row, CustomLayoutEditorView.NOTHING);
    showLayout();
  }

  private void splitSelectedKey() {
    final CustomLayout.Key picked = selectedKey();
    if (picked == null) return;
    final CustomLayout.Row row = mLayout.rows.get(selectedRow());
    final int index = row.keys.indexOf(picked);
    row.split(index);
    // the new gap is picked: the next key tapped in the lists goes there
    mKeyboardView.setSelection(selectedRow(), index + 1);
    showLayout();
  }

  /** Shows the layout as it is now: the keyboard, the keys left to add, the picked key. */
  private void showLayout() {
    noteChange(null);
    if (mShownTab == TAB_ROW) {
      // on this tab a whole row is picked, also after a row was added or removed, and the
      // keyboard is the one of a field that has the row
      final int row =
          selectedRow() == CustomLayoutEditorView.NOTHING ? lastShownRow() : selectedRow();
      mKeyboardView.setSelection(row, CustomLayoutEditorView.NOTHING);
      showFieldOf(row);
    }
    showKeyboard();
    final AnyKeyboard keyboard = mKeyboardView.getKeyboard();
    if (mShownTab == TAB_ROW) {
      showRowPanel();
      return;
    } else if (mShownTab == TAB_KEYBOARD) {
      showHeights(mSizeRows, true);
      return;
    }

    // the keys that can be added: all of them, also the ones the layout has (a key can be there
    // twice). The ones it does not have stand out, so a letter that was removed is easy to find.
    mUsualKeys.removeAllViews();
    mKeyboardKeys.removeAllViews();
    final List<Integer> choices = keyboard.getCustomLayoutKeyChoices();
    for (int code : choices) {
      // two key codes open the symbols; offer the other one only where the layout uses it
      if (code == KeyCodes.KEYBOARD_MODE_CHANGE
          && choices.contains(KeyCodes.MODE_SYMBOLS)
          && !mLayout.contains(code)) {
        continue;
      }
      final TextView chip =
          addChip(
              code > ' ' ? mKeyboardKeys : mUsualKeys, labelOf(code), code, v -> placeKey(code));
      if (!mLayout.contains(code)) {
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.custom_layout_accent));
        chip.setBackgroundResource(R.drawable.custom_layout_chip_missing);
      }
    }
    addChip(
        mUsualKeys,
        getString(R.string.custom_layout_add_gap),
        CustomLayout.GAP,
        v -> placeKey(CustomLayout.GAP));
    addChip(
        mKeyboardKeys,
        getString(R.string.custom_layout_any_character),
        null,
        v -> pickCharacters());

    final CustomLayout.Key selected = selectedKey();
    final int pickedKeys = mKeyboardView.getSelectedKeys().size();
    if (selected != null) {
      mSelectionView.setText(
          getString(R.string.custom_layout_selected_key, labelOf(selected.code)));
      mSelectionHintView.setText(R.string.custom_layout_selected_key_hint);
    } else if (pickedKeys > 1) {
      mSelectionView.setText(getString(R.string.custom_layout_selected_keys, pickedKeys));
      mSelectionHintView.setText(R.string.custom_layout_selected_keys_hint);
    } else {
      mSelectionView.setText(R.string.custom_layout_nothing_selected);
      mSelectionHintView.setText(R.string.custom_layout_nothing_selected_hint);
    }
    requireView()
        .findViewById(R.id.custom_layout_edges_panel)
        .setVisibility(selected == null ? View.GONE : View.VISIBLE);
    if (selected != null) {
      final CustomLayout.Row row = mLayout.rows.get(selectedRow());
      final int index = row.keys.indexOf(selected);
      float left = 0;
      for (int i = 0; i < index; i++) left += row.keys.get(i).width;
      final float percent = 100 / row.totalWidth();
      showEdge(mLeftEdge, left * percent, index > 0);
      showEdge(mRightEdge, (left + selected.width) * percent, index < row.keys.size() - 1);
      showEdge(mKeyWidth, selected.width * percent, row.keys.size() > 1);
    }
    final TextView removeKey = requireView().findViewById(R.id.custom_layout_remove_key);
    removeKey.setText(
        pickedKeys > 1
            ? getString(R.string.custom_layout_remove_keys, pickedKeys)
            : getString(R.string.custom_layout_remove_key));
    removeKey.setVisibility(pickedKeys == 0 ? View.GONE : View.VISIBLE);
    requireView()
        .findViewById(R.id.custom_layout_long_press)
        .setVisibility(
            selected == null || selected.code == CustomLayout.GAP ? View.GONE : View.VISIBLE);

    final TextView evenKeys = requireView().findViewById(R.id.custom_layout_even_keys);
    evenKeys.setText(getString(R.string.custom_layout_even_keys, pickedKeys));
    evenKeys.setVisibility(pickedKeys > 1 ? View.VISIBLE : View.GONE);
    final TextView goToRow = requireView().findViewById(R.id.custom_layout_go_to_row);
    goToRow.setVisibility(
        selectedRow() == CustomLayoutEditorView.NOTHING ? View.GONE : View.VISIBLE);
    goToRow.setText(getString(R.string.custom_layout_go_to_row, selectedRow() + 1));
    requireView()
        .findViewById(R.id.custom_layout_split_key)
        .setVisibility(selected == null ? View.GONE : View.VISIBLE);
  }

  /** The Row tab: the picked row's height, side gaps and actions. */
  private void showRowPanel() {
    final int pickedRow = selectedRow();
    final CustomLayout.Row row = mLayout.rows.get(pickedRow);
    final View root = requireView();
    ((TextView) root.findViewById(R.id.custom_layout_row_title))
        .setText(getString(R.string.custom_layout_row_of, pickedRow + 1, mLayout.rows.size()));
    int keys = 0;
    for (CustomLayout.Key key : row.keys) {
      if (key.code != CustomLayout.GAP) keys++;
    }
    ((TextView) root.findViewById(R.id.custom_layout_row_keys))
        .setText(getString(R.string.custom_layout_row_keys, keys));
    showHeights(mRowHeight, false);

    final float gaps = 100 * row.getSideGaps() / row.totalWidth();
    mSideGaps.setProgress(Math.round(gaps / SIDE_GAP_STEP));
    ((TextView) root.findViewById(R.id.custom_layout_side_gaps_value))
        .setText(getString(R.string.custom_layout_side_gaps_value, gaps));

    final TextView removeRow = root.findViewById(R.id.custom_layout_remove_row);
    removeRow.setText(getString(R.string.custom_layout_remove_row, pickedRow + 1));
    removeRow.setVisibility(mLayout.rows.size() > 1 ? View.VISIBLE : View.GONE);
    ((TextView) root.findViewById(R.id.custom_layout_even_row))
        .setText(R.string.custom_layout_even_row);
    root.findViewById(R.id.custom_layout_move_row_up)
        .setVisibility(pickedRow > 0 ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.custom_layout_move_row_down)
        .setVisibility(pickedRow < mLayout.rows.size() - 1 ? View.VISIBLE : View.GONE);

    final ChipGroup fields = root.findViewById(R.id.custom_layout_row_fields);
    fields.removeAllViews();
    for (int i = 0; i < CustomLayout.FIELDS.length; i++) {
      final int field = CustomLayout.FIELDS[i];
      final TextView chip =
          addChip(
              fields,
              getString(FIELD_NAMES[i]),
              "field:" + field,
              v -> {
                row.setShownIn(field, !row.showsIn(field));
                showLayout();
              });
      if (row.showsIn(field)) markChip(chip);
    }
  }

  /** A second row like the picked one, under it. It is picked. */
  private void duplicateRow() {
    final int row = selectedRow();
    if (row == CustomLayoutEditorView.NOTHING) return;
    mLayout.rows.add(row + 1, mLayout.rows.get(row).copy());
    mKeyboardView.setSelection(row + 1, CustomLayoutEditorView.NOTHING);
    showLayout();
  }

  /** Moves the picked row one place up or down. */
  private void moveRow(int by) {
    final int row = selectedRow();
    final int target = row + by;
    if (row == CustomLayoutEditorView.NOTHING || target < 0 || target >= mLayout.rows.size()) {
      return;
    }
    mLayout.rows.add(target, mLayout.rows.remove(row));
    mKeyboardView.setSelection(target, CustomLayoutEditorView.NOTHING);
    showLayout();
  }

  /** Shows where an edge of the picked key is, in percent of the row from its start. */
  private void showEdge(SeekBar slider, float position, boolean movable) {
    slider.setEnabled(movable);
    slider.setProgress(Math.round(position / EDGE_SLIDER_STEP));
    final EditText value = edgeValueOf(slider);
    value.setEnabled(movable);
    final String text = String.format(Locale.ROOT, "%.1f", position);
    if (!value.hasFocus() || !text.contentEquals(value.getText())) value.setText(text);
  }

  private EditText edgeValueOf(SeekBar slider) {
    final int id;
    if (slider == mLeftEdge) id = R.id.custom_layout_left_edge_value;
    else if (slider == mRightEdge) id = R.id.custom_layout_right_edge_value;
    else id = R.id.custom_layout_key_width_value;
    return requireView().findViewById(id);
  }

  /**
   * Moves the left or right edge of the picked key to {@code position} (percent of the row), in
   * multiples of {@code step}. The room is shared with the neighbour, or with all keys on that side
   * when that way of resizing is on.
   */
  private void moveEdgeOfSelected(boolean right, float position, float step) {
    final CustomLayout.Key picked = selectedKey();
    if (picked == null) return;
    final CustomLayout.Row row = mLayout.rows.get(selectedRow());
    final int edge = row.keys.indexOf(picked) - (right ? 0 : 1);
    if (edge < 0 || edge >= row.keys.size() - 1) return;
    final float unit = row.totalWidth() / 100;
    final boolean evenSide =
        ((CheckBox) requireView().findViewById(R.id.custom_layout_resize_evens_side)).isChecked();
    final boolean changed =
        evenSide
            ? row.moveEdge(edge, position * unit, right, step * unit)
            : row.moveEdge(edge, position * unit, step * unit);
    if (!changed) return;
    noteChange("edge " + selectedRow() + " " + edge);
    showLayout();
  }

  private interface EdgeMover {
    void move(float value, float step);
  }

  /** Wires a slider and its typed value: both give a value in percent of the row. */
  private void setUpEdge(SeekBar slider, EdgeMover mover) {
    slider.setMax(Math.round(100 / EDGE_SLIDER_STEP));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) mover.move(progress * EDGE_SLIDER_STEP, EDGE_SLIDER_STEP);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            // the thumb may have been dragged further than the edge can go
            showLayout();
          }
        });
    final EditText value = edgeValueOf(slider);
    value.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId != EditorInfo.IME_ACTION_DONE) return false;
          try {
            mover.move(
                Float.parseFloat(value.getText().toString().replace(',', '.')), EDGE_TYPED_STEP);
          } catch (NumberFormatException e) {
            // not a number: nothing moves
          }
          value.clearFocus();
          showLayout();
          return false;
        });
  }

  /**
   * Makes the picked key {@code width} wide (percent of the row) by moving its right edge, or its
   * left edge when it is the last of its row.
   */
  private void setWidthOfSelected(float width, float step) {
    final CustomLayout.Key picked = selectedKey();
    if (picked == null) return;
    final CustomLayout.Row row = mLayout.rows.get(selectedRow());
    final int index = row.keys.indexOf(picked);
    float left = 0;
    for (int i = 0; i < index; i++) left += row.keys.get(i).width;
    final float percent = 100 / row.totalWidth();
    if (index < row.keys.size() - 1) {
      moveEdgeOfSelected(true, left * percent + width, step);
    } else {
      moveEdgeOfSelected(false, (left + picked.width) * percent - width, step);
    }
  }

  /** A short name for a key, for the places that show keys as text. */
  @NonNull
  private String labelOf(int code) {
    switch (code) {
      case CustomLayout.GAP:
        return getString(R.string.custom_layout_gap);
      case KeyCodes.SPACE:
        return getString(R.string.custom_layout_key_space);
      case KeyCodes.ENTER:
        return "⏎";
      case KeyCodes.DELETE:
        return "⌫";
      case KeyCodes.FORWARD_DELETE:
        return "⌦";
      case KeyCodes.SHIFT:
        return "⇧";
      case KeyCodes.KEYBOARD_MODE_CHANGE:
      case KeyCodes.MODE_SYMBOLS:
        return "123";
      case KeyCodes.MODE_ALPHABET:
        return "🌐";
      case KeyCodes.QUICK_TEXT:
        return "☺";
      case KeyCodes.VOICE_INPUT:
        return "🎤";
      case KeyCodes.ARROW_LEFT:
        return "←";
      case KeyCodes.ARROW_RIGHT:
        return "→";
      case KeyCodes.ARROW_UP:
        return "↑";
      case KeyCodes.ARROW_DOWN:
        return "↓";
      case KeyCodes.MOVE_HOME:
        return getString(R.string.custom_layout_key_home);
      case KeyCodes.MOVE_END:
        return getString(R.string.custom_layout_key_end);
      case KeyCodes.TAB:
        return getString(R.string.custom_layout_key_tab);
      case KeyCodes.CLIPBOARD_COPY:
        return getString(R.string.custom_layout_key_copy);
      case KeyCodes.CLIPBOARD_PASTE:
        return getString(R.string.custom_layout_key_paste);
      case KeyCodes.CLIPBOARD_SELECT:
        return getString(R.string.custom_layout_key_select);
      case KeyCodes.UNDO:
        return getString(R.string.custom_layout_key_undo);
      case KeyCodes.REDO:
        return getString(R.string.custom_layout_key_redo);
      case KeyCodes.CTRL:
        return "ctrl";
      case KeyCodes.DOMAIN:
        return ".com";
      case KeyCodes.SETTINGS:
        return getString(R.string.custom_layout_key_settings);
      case KeyCodes.CANCEL:
        return getString(R.string.custom_layout_key_close);
      default:
        return code > ' '
            ? new String(Character.toChars(code))
            : getString(R.string.custom_layout_function_key);
    }
  }

  /** {@code tag} is the key code the chip stands for, if it stands for one. */
  private TextView addChip(
      ViewGroup group, String label, @Nullable Object tag, View.OnClickListener onClick) {
    final TextView chip =
        new TextView(new ContextThemeWrapper(requireContext(), R.style.CustomLayout_Chip), null, 0);
    chip.setText(label);
    chip.setTag(tag);
    chip.setOnClickListener(onClick);
    group.addView(chip);
    return chip;
  }

  /**
   * Any character as a key: typed or pasted into the field, or picked from the lists under it,
   * which start with the everyday ones. The dialog stays open so several can be added.
   */
  private void pickCharacters() {
    final View content =
        LayoutInflater.from(requireContext()).inflate(R.layout.custom_layout_characters, null);
    final EditText typed = content.findViewById(R.id.custom_layout_characters_typed);
    final LinearLayout lists = content.findViewById(R.id.custom_layout_characters_lists);
    final AlertDialog dialog =
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.custom_layout_any_character)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                (d, which) -> typed.getText().toString().codePoints().forEach(this::placeKey))
            .create();
    final String[] names = getResources().getStringArray(R.array.custom_layout_character_groups);
    final String[] characters = getResources().getStringArray(R.array.custom_layout_characters);
    for (int group = 0; group < names.length; group++) {
      final TextView title =
          new TextView(
              new ContextThemeWrapper(requireContext(), R.style.CustomLayout_Text_Section),
              null,
              0);
      title.setText(names[group]);
      lists.addView(title);
      final ChipGroup chips = new ChipGroup(requireContext());
      final int padding = Math.round(16 * getResources().getDisplayMetrics().density);
      chips.setPadding(padding, padding / 2, padding, padding / 2);
      characters[group]
          .codePoints()
          .filter(code -> !Character.isWhitespace(code))
          .forEach(
              code ->
                  addChip(
                      chips,
                      labelOf(code),
                      code,
                      v -> {
                        placeKey(code);
                        dialog.dismiss();
                      }));
      lists.addView(chips);
    }
    dialog.show();
  }

  private String toJson() {
    try {
      return mLayout.toJson().toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Keeps what the layout was before it changed, so the change can be undone. Changes of the same
   * {@code kind} that follow each other (the steps of one drag) are undone as one.
   */
  private void noteChange(@Nullable String kind) {
    final String now = toJson();
    if (now.equals(mSnapshot)) return;
    if (kind == null || !kind.equals(mLastChangeKind)) mUndo.push(mSnapshot);
    mLastChangeKind = kind;
    mSnapshot = now;
    mAskBeforeLeaving.setEnabled(true);
    requireActivity().invalidateOptionsMenu();
  }

  private void undo() {
    if (mUndo.isEmpty()) return;
    mSnapshot = mUndo.pop();
    mLastChangeKind = null;
    try {
      final CustomLayout before = CustomLayout.fromJson(new JSONObject(mSnapshot));
      mLayout.rows.clear();
      mLayout.rows.addAll(before.rows);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    mKeyboardView.setSelection(CustomLayoutEditorView.NOTHING, CustomLayoutEditorView.NOTHING);
    mAskBeforeLeaving.setEnabled(!mUndo.isEmpty());
    requireActivity().invalidateOptionsMenu();
    showTab();
  }

  private int selectedRow() {
    final int row = mKeyboardView.getSelectedRow();
    return row < 0 || row >= mLayout.rows.size() ? CustomLayoutEditorView.NOTHING : row;
  }

  /** Equal widths for the picked keys when several are picked, otherwise for the whole row. */
  private void evenSelectedRow() {
    if (selectedRow() == CustomLayoutEditorView.NOTHING) return;
    final CustomLayout.Row row = mLayout.rows.get(selectedRow());
    final List<Integer> picked = mKeyboardView.getSelectedKeys();
    if (picked.size() > 1) row.evenWidths(picked);
    else row.evenWidths();
    showLayout();
  }

  @Nullable
  private CustomLayout.Key selectedKey() {
    final int row = mKeyboardView.getSelectedRow();
    final int key = mKeyboardView.getSelectedKey();
    if (row < 0 || row >= mLayout.rows.size()) return null;
    final List<CustomLayout.Key> keys = mLayout.rows.get(row).keys;
    return key < 0 || key >= keys.size() ? null : keys.get(key);
  }

  @Override
  public void onSelectionChanged() {
    if (mShownTab == TAB_KEYBOARD) {
      // a key was tapped: that is where the user wants to work
      mTabs.selectTab(mTabs.getTabAt(TAB_KEY));
    } else {
      showLayout();
    }
  }

  @Override
  public void onLayoutChanged() {
    noteChange("drag " + mKeyboardView.getGestureCount());
    showLayout();
  }

  // ---- Keys tab ----

  /**
   * Puts the key {@code code} where the picked key or gap is, in its place and with its width. With
   * nothing picked (or several keys) it is added: after the picked keys, or at the end of the
   * bottom row.
   */
  private void placeKey(int code) {
    final CustomLayout.Key picked = selectedKey();
    if (picked != null) {
      final CustomLayout.Row row = mLayout.rows.get(selectedRow());
      row.keys.set(row.keys.indexOf(picked), new CustomLayout.Key(code, picked.width));
      showLayout();
    } else {
      final List<Integer> several = mKeyboardView.getSelectedKeys();
      insertKey(code, several.isEmpty() ? Integer.MAX_VALUE : several.get(several.size() - 1) + 1);
    }
  }

  /**
   * Adds the key {@code code} to the picked row (the bottom row when none is picked) at {@code
   * index}, or at its end if the row is shorter, and picks it.
   */
  private void insertKey(int code, int index) {
    final int rowIndex =
        selectedRow() == CustomLayoutEditorView.NOTHING ? mLayout.rows.size() - 1 : selectedRow();
    final CustomLayout.Row row = mLayout.rows.get(rowIndex);
    index = Math.min(index, row.keys.size());
    row.add(index, new CustomLayout.Key(code, NEW_KEY_WIDTH * row.totalWidth() / 100f));
    mKeyboardView.setSelection(rowIndex, index);
    showLayout();
  }

  /**
   * A new gap beside the picked key (at that end of the row when no single key is picked). It is
   * picked, so the next key tapped in the lists takes its place.
   */
  private void insertGap(boolean after) {
    final int picked = mKeyboardView.getSelectedKey();
    if (picked == CustomLayoutEditorView.NOTHING) {
      insertKey(CustomLayout.GAP, after ? Integer.MAX_VALUE : 0);
    } else {
      insertKey(CustomLayout.GAP, after ? picked + 1 : picked);
    }
  }

  /** Removes the picked keys; the row too, when no key would be left. */
  private void removeSelectedKeys() {
    final List<Integer> picked = mKeyboardView.getSelectedKeys();
    if (picked.isEmpty()) return;
    final int rowIndex = selectedRow();
    final CustomLayout.Row row = mLayout.rows.get(rowIndex);
    if (picked.size() == row.keys.size()) {
      removeSelectedRow();
      return;
    }
    for (int i = picked.size() - 1; i >= 0; i--) row.remove(picked.get(i));
    mKeyboardView.setSelection(rowIndex, CustomLayoutEditorView.NOTHING);
    showLayout();
  }

  /**
   * A new row above or under the picked one (the bottom row when none is picked). It starts as one
   * gap, which gives way to the keys added.
   */
  private void addRow(boolean above) {
    int rowIndex = mKeyboardView.getSelectedRow();
    if (rowIndex < 0 || rowIndex >= mLayout.rows.size()) rowIndex = mLayout.rows.size() - 1;
    final CustomLayout.Row row = new CustomLayout.Row();
    row.height = mLayout.rows.get(rowIndex).height;
    row.keys.add(new CustomLayout.Key(CustomLayout.GAP, 100));
    final int newIndex = above ? rowIndex : rowIndex + 1;
    mLayout.rows.add(newIndex, row);
    mKeyboardView.setSelection(newIndex, 0);
    showLayout();
  }

  private void removeSelectedRow() {
    final int rowIndex = mKeyboardView.getSelectedRow();
    if (rowIndex < 0 || rowIndex >= mLayout.rows.size() || mLayout.rows.size() == 1) return;
    mLayout.rows.remove(rowIndex);
    mKeyboardView.setSelection(CustomLayoutEditorView.NOTHING, CustomLayoutEditorView.NOTHING);
    showLayout();
  }

  private void editLongPress() {
    final CustomLayout.Key selected = selectedKey();
    if (selected == null || selected.code == CustomLayout.GAP) return;
    CharSequence current = selected.popup;
    if (current == null) {
      // what the key has by itself
      for (Keyboard.Key key : mKeyboardView.getKeyboard().getKeys()) {
        if (key.getPrimaryCode() == selected.code) current = key.popupCharacters;
      }
    }
    askForText(
        R.string.custom_layout_long_press_hint,
        current == null ? "" : current,
        R.string.custom_layout_long_press_original,
        text -> {
          selected.popup = text;
          showLayout();
        });
  }

  private interface TextConsumer {
    void accept(@Nullable String text);
  }

  /**
   * Asks for a line of text. With {@code clearButton}, the dialog has a third button that answers
   * null.
   */
  private void askForText(int title, CharSequence initial, int clearButton, TextConsumer onText) {
    final EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    input.setText(initial);
    input.setHint(R.string.custom_layout_text_hint);
    final AlertDialog.Builder builder =
        new AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok, (dialog, which) -> onText.accept(input.getText().toString()));
    if (clearButton != 0) {
      builder.setNeutralButton(clearButton, (dialog, which) -> onText.accept(null));
    }
    builder.show();
  }

  // ---- Size tab ----

  private float getHeight(int sizeRow) {
    if (sizeRow != ALL_LETTER_ROWS) return mLayout.rows.get(sizeRow).height;
    final float first = mLayout.rows.get(0).height;
    for (int row = 1; row < mLayout.rows.size() - 1; row++) {
      if (mLayout.rows.get(row).height != first) return Float.NaN;
    }
    return first;
  }

  private void setHeight(int sizeRow, float height) {
    height = Math.max(CustomLayout.MIN_ROW_HEIGHT, Math.min(CustomLayout.MAX_ROW_HEIGHT, height));
    if (sizeRow != ALL_LETTER_ROWS) {
      mLayout.rows.get(sizeRow).height = height;
    } else {
      for (int row = 0; row < Math.max(1, mLayout.rows.size() - 1); row++) {
        mLayout.rows.get(row).height = height;
      }
    }
    noteChange("height " + sizeRow);
    showKeyboard();
  }

  private static int toProgress(float height) {
    return Math.round((height - CustomLayout.MIN_ROW_HEIGHT) * 100);
  }

  private static String format(float height) {
    return String.format(Locale.ROOT, "%.2f", height);
  }

  /**
   * The heights of rows, each with its typed value and, when it is the one being set, its slider.
   * On the Keyboard tab ({@code all}): every row, with the slider on "all rows above the bottom
   * row"; a tap on a row opens it on the Row tab. On the Row tab: the picked row only.
   */
  private void showHeights(LinearLayout container, boolean all) {
    container.removeAllViews();
    final LayoutInflater inflater = LayoutInflater.from(requireContext());
    final List<Runnable> refreshers = new ArrayList<>();
    final int rowCount = mLayout.rows.size();
    final int first = all ? (rowCount > 2 ? ALL_LETTER_ROWS : 0) : selectedRow();
    final int end = all ? rowCount : selectedRow() + 1;
    final int withSlider = all ? ALL_LETTER_ROWS : selectedRow();
    for (int sizeRow = first; sizeRow < end; sizeRow++) {
      if (sizeRow == ALL_LETTER_ROWS + 1) sizeRow = 0;
      final int thisRow = sizeRow;
      final View item = inflater.inflate(R.layout.custom_layout_size_row, container, false);
      container.addView(item);
      final TextView label = item.findViewById(R.id.size_row_label);
      if (thisRow == ALL_LETTER_ROWS) label.setText(R.string.custom_layout_all_letter_rows);
      else if (thisRow == rowCount - 1) label.setText(R.string.custom_layout_bottom_row);
      else label.setText(getString(R.string.custom_layout_row, thisRow + 1));
      if (thisRow >= 0 && mLayout.rows.get(thisRow).fields != CustomLayout.ALL_FIELDS) {
        label.setText(
            getString(
                R.string.custom_layout_row_for_fields,
                label.getText(),
                fieldNames(mLayout.rows.get(thisRow))));
      }

      final EditText value = item.findViewById(R.id.size_row_value);
      final TickSeekBar slider = item.findViewById(R.id.size_row_slider);
      final Runnable refresh =
          () -> {
            final float height = getHeight(thisRow);
            final String text =
                Float.isNaN(height)
                    ? getString(R.string.custom_layout_mixed_heights)
                    : format(height);
            if (!value.hasFocus() || !text.contentEquals(value.getText())) {
              value.setText(text);
            }
            if (!Float.isNaN(height)) slider.setProgress(toProgress(height));
          };
      refreshers.add(refresh);
      final Runnable refreshAll =
          () -> {
            for (Runnable refresher : refreshers) refresher.run();
            showTotalHeight();
          };

      if (all && thisRow != ALL_LETTER_ROWS) {
        item.findViewById(R.id.size_row_header).setOnClickListener(v -> openRow(thisRow));
      }
      value.setOnEditorActionListener(
          (v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            try {
              setHeight(thisRow, Float.parseFloat(value.getText().toString().replace(',', '.')));
            } catch (NumberFormatException e) {
              // not a number: the row keeps its height
            }
            value.clearFocus();
            refreshAll.run();
            return false;
          });

      if (thisRow == withSlider) {
        value.setBackgroundResource(R.drawable.custom_layout_value_box);
        slider.setVisibility(View.VISIBLE);
        slider.setMax(toProgress(CustomLayout.MAX_ROW_HEIGHT));
        // marks: the standard heights, and the heights the other rows have, so rows are easy to
        // match
        final TreeSet<Integer> ticks = new TreeSet<>();
        for (int i = 0; i < STANDARD_HEIGHTS.length; i++) {
          ticks.add(toProgress(STANDARD_HEIGHTS[i]));
          slider.addTick(
              toProgress(STANDARD_HEIGHTS[i]),
              getString(STANDARD_HEIGHT_NAMES[i]) + "\n" + format(STANDARD_HEIGHTS[i]),
              false);
        }
        for (int row = 0; row < rowCount; row++) {
          final float height = mLayout.rows.get(row).height;
          if (row != thisRow && ticks.add(toProgress(height))) {
            final String name =
                row == rowCount - 1
                    ? getString(R.string.custom_layout_bottom_row)
                    : getString(R.string.custom_layout_row, row + 1);
            slider.addTick(toProgress(height), name + "\n" + format(height), true);
          }
        }
        slider.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
              @Override
              public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                setHeight(thisRow, CustomLayout.MIN_ROW_HEIGHT + slider.snap(progress) / 100f);
                final float height = getHeight(thisRow);
                value.setText(format(height));
                for (Runnable refresher : refreshers) {
                  if (refresher != refresh) refresher.run();
                }
                showTotalHeight();
              }

              @Override
              public void onStartTrackingTouch(SeekBar seekBar) {}

              @Override
              public void onStopTrackingTouch(SeekBar seekBar) {
                refresh.run();
              }
            });
      }
      refresh.run();
    }
    showTotalHeight();
  }

  /** From the Keyboard tab: the Row tab with {@code row} picked. */
  private void openRow(int row) {
    mKeyboardView.setSelection(row, CustomLayoutEditorView.NOTHING);
    mTabs.selectTab(mTabs.getTabAt(TAB_ROW));
  }

  private void showTotalHeight() {
    final float total = mLayout.totalHeight(mPreviewField);
    final float rowHeightDp =
        mKeyboardView.getThemedKeyboardDimens().getNormalKeyHeight()
            / getResources().getDisplayMetrics().density;
    ((TextView) requireView().findViewById(R.id.custom_layout_total_height))
        .setText(getString(R.string.custom_layout_total_height, total));
    ((TextView) requireView().findViewById(R.id.custom_layout_total_height_hint))
        .setText(
            getString(R.string.custom_layout_total_height_hint, Math.round(total * rowHeightDp)));
  }

  // ---- menu ----

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.custom_layout_editor_menu, menu);
    menu.findItem(R.id.custom_layout_delete).setVisible(mStored);
    menu.findItem(R.id.custom_layout_undo).setVisible(!mUndo.isEmpty());
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    return perform(item.getItemId()) || super.onOptionsItemSelected(item);
  }

  /** Does what the menu item or link {@code itemId} stands for; false if it is not one of them. */
  private boolean perform(int itemId) {
    if (itemId == R.id.custom_layout_save) {
      save();
    } else if (itemId == R.id.custom_layout_undo) {
      undo();
    } else if (itemId == R.id.custom_layout_flip) {
      flipPreviewPlace();
    } else if (itemId == R.id.custom_layout_copy) {
      copyToClipboard();
    } else if (itemId == R.id.custom_layout_paste) {
      pasteFromClipboard();
    } else if (itemId == R.id.custom_layout_export) {
      mExportToFile.launch(mNameView.getText() + ".json");
    } else if (itemId == R.id.custom_layout_import) {
      mImportFromFile.launch(
          new String[] {"application/json", "text/*", "application/octet-stream"});
    } else if (itemId == R.id.custom_layout_reset) {
      final CustomLayout original = CustomLayout.capture(mLayout.id, mLayout.name, load(mBase));
      mLayout.rows.clear();
      mLayout.rows.addAll(original.rows);
      mKeyboardView.setSelection(CustomLayoutEditorView.NOTHING, CustomLayoutEditorView.NOTHING);
      showTab();
    } else if (itemId == R.id.custom_layout_delete) {
      mFactory.setAddOnEnabled(mLayout.id, false);
      mFactory.deleteCustomLayout(mLayout.id);
      Navigation.findNavController(requireView()).popBackStack();
    } else if (itemId == R.id.custom_layout_share_bottom_row) {
      shareBottomRow();
    } else {
      return false;
    }
    return true;
  }

  // ---- a layout as text: to and from the clipboard or a file ----

  private final ActivityResultLauncher<String> mExportToFile =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/json"),
          uri -> {
            if (uri == null) return;
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
              out.write(toJson().getBytes(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
              toast(R.string.custom_layout_export_failed);
            }
          });

  private final ActivityResultLauncher<String[]> mImportFromFile =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(),
          uri -> {
            if (uri == null) return;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
              final ByteArrayOutputStream text = new ByteArrayOutputStream();
              final byte[] buffer = new byte[4096];
              for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                text.write(buffer, 0, read);
              }
              importLayout(new String(text.toByteArray(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
              toast(R.string.custom_layout_import_failed);
            }
          });

  private void copyToClipboard() {
    final ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText(mLayout.name, toJson()));
    toast(R.string.custom_layout_copied);
  }

  private void pasteFromClipboard() {
    final ClipboardManager clipboard =
        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    final ClipData clip = clipboard.getPrimaryClip();
    final CharSequence text =
        clip == null || clip.getItemCount() == 0
            ? null
            : clip.getItemAt(0).coerceToText(requireContext());
    importLayout(text == null ? "" : text.toString());
  }

  /**
   * Takes the rows of a layout that was exported as text. The keyboard this layout is based on
   * stays; a key it does not have becomes a plain key for that character. Undo brings the rows
   * back.
   */
  private void importLayout(@NonNull String json) {
    try {
      final CustomLayout imported = CustomLayout.fromJson(new JSONObject(json));
      if (imported.rows.isEmpty()) throw new JSONException("no rows");
      mLayout.rows.clear();
      mLayout.rows.addAll(imported.rows);
    } catch (JSONException e) {
      toast(R.string.custom_layout_import_failed);
      return;
    }
    mKeyboardView.setSelection(CustomLayoutEditorView.NOTHING, CustomLayoutEditorView.NOTHING);
    showTab();
  }

  private void toast(int message) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
  }

  private void save() {
    if (!mLayout.contains(KeyCodes.SPACE)
        || !mLayout.contains(KeyCodes.ENTER)
        || !mLayout.contains(KeyCodes.DELETE)) {
      Toast.makeText(requireContext(), R.string.custom_layout_missing_keys, Toast.LENGTH_LONG)
          .show();
      return;
    }
    // the same for the keyboard of each kind of field, now that rows can be for some fields only
    for (int field : CustomLayout.FIELDS) {
      if (!mLayout.contains(KeyCodes.SPACE, field)
          || !mLayout.contains(KeyCodes.ENTER, field)
          || !mLayout.contains(KeyCodes.DELETE, field)) {
        Toast.makeText(
                requireContext(),
                getString(R.string.custom_layout_missing_keys_in_field, fieldName(field)),
                Toast.LENGTH_LONG)
            .show();
        return;
      }
    }
    final String name = mNameView.getText().toString().trim();
    if (!name.isEmpty()) mLayout.name = name;
    mFactory.saveCustomLayout(mLayout);
    if (!mStored) mFactory.setAddOnEnabled(mLayout.id, true);
    mAskBeforeLeaving.setEnabled(false);
    Navigation.findNavController(requireView()).popBackStack();
  }

  /** Offers the other keyboards, and gives the picked ones the bottom row of this layout. */
  private void shareBottomRow() {
    final List<KeyboardAddOnAndBuilder> others = new ArrayList<>();
    for (KeyboardAddOnAndBuilder addOn : mFactory.getAllAddOns()) {
      if (!addOn.getId().equals(mLayout.id)) others.add(addOn);
    }
    final CharSequence[] names = new CharSequence[others.size()];
    for (int i = 0; i < names.length; i++) names[i] = others.get(i).getName();
    final boolean[] picked = new boolean[names.length];
    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.custom_layout_share_bottom_row)
        .setMultiChoiceItems(names, picked, (dialog, which, isChecked) -> picked[which] = isChecked)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(
            android.R.string.ok,
            (dialog, which) -> {
              int count = 0;
              for (int i = 0; i < picked.length; i++) {
                if (!picked[i]) continue;
                count++;
                // a custom keyboard gets the row itself, any other gets it on a new copy
                final KeyboardAddOnAndBuilder other = others.get(i);
                final CustomLayout stored = mFactory.getCustomLayout(other.getId());
                final CustomLayout target = stored != null ? stored : captureAsNew(other);
                target.rows.set(target.rows.size() - 1, bottomRowFor(target));
                mFactory.saveCustomLayout(target);
                if (stored == null) mFactory.setAddOnEnabled(target.id, true);
              }
              Toast.makeText(
                      requireContext(),
                      getString(R.string.custom_layout_bottom_row_shared, count),
                      Toast.LENGTH_SHORT)
                  .show();
            })
        .show();
  }

  /**
   * A copy of this layout's bottom row for another keyboard: without the letters that keyboard does
   * not have, since they would mean nothing there.
   */
  @NonNull
  private CustomLayout.Row bottomRowFor(@NonNull CustomLayout target) {
    final CustomLayout.Row row = mLayout.copy("", "").rows.get(mLayout.rows.size() - 1);
    for (int index = row.keys.size() - 1; index >= 0 && row.keys.size() > 1; index--) {
      final int code = row.keys.get(index).code;
      if (Character.isLetter(code) && !target.contains(code)) row.remove(index);
    }
    return row;
  }
}
