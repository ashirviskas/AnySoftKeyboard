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

package com.anysoftkeyboard.keyboards;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.anysoftkeyboard.addons.AddOn;
import com.anysoftkeyboard.addons.AddOnsFactory;
import com.anysoftkeyboard.base.utils.Logger;
import com.anysoftkeyboard.prefs.DirectBootAwareSharedPreferences;
import com.menny.android.anysoftkeyboard.BuildConfig;
import com.menny.android.anysoftkeyboard.R;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

public class KeyboardFactory extends AddOnsFactory.MultipleAddOnsFactory<KeyboardAddOnAndBuilder> {
  private static final String TAG = "ASKKeyboardFactory";

  private static final String XML_LAYOUT_RES_ID_ATTRIBUTE = "layoutResId";
  private static final String XML_LANDSCAPE_LAYOUT_RES_ID_ATTRIBUTE = "landscapeResId";
  private static final String XML_ICON_RES_ID_ATTRIBUTE = "iconResId";
  private static final String XML_DICTIONARY_NAME_ATTRIBUTE = "defaultDictionaryLocale";
  private static final String XML_ADDITIONAL_IS_LETTER_EXCEPTIONS_ATTRIBUTE =
      "additionalIsLetterExceptions";
  private static final String XML_SENTENCE_SEPARATOR_CHARACTERS_ATTRIBUTE = "sentenceSeparators";
  private static final String DEFAULT_SENTENCE_SEPARATORS = ".,!?)]:;";
  private static final String XML_PHYSICAL_TRANSLATION_RES_ID_ATTRIBUTE =
      "physicalKeyboardMappingResId";
  private static final String XML_HAS_RADICAL_INPUT_ATTRIBUTE = "hasRadicalInput";
  private static final String XML_RADICAL_KEY_CHARACTERS_ATTRIBUTE = "radicalKeyCharacters";
  private static final String XML_RADICAL_CANDIDATE_SELECTOR_KEYS_ATTRIBUTE =
      "radicalCandidateSelectorKeys";
  private static final String XML_PHONETIC_LAYOUT_ATTRIBUTE = "phoneticLayout";
  private static final String XML_VARIANT_OF_ATTRIBUTE = "variantOf";
  private static final String XML_ASCII_ONLY_POPUPS_ATTRIBUTE = "asciiOnlyPopups";
  private static final String XML_SUPPORTS_GESTURE_TYPING_ATTRIBUTE = "supportsGestureTyping";
  private static final String XML_DEFAULT_ATTRIBUTE = "defaultEnabled";
  public static final String PREF_ID_PREFIX = "keyboard_";

  // Starts with PREF_ID_PREFIX, so the keyboards are rebuilt when it changes.
  private static final String CUSTOM_LAYOUTS_PREF = PREF_ID_PREFIX + "custom_layouts";

  // The custom layouts are also written from outside this class (restoring a backup). A field,
  // since the preferences hold their listeners weakly.
  private final SharedPreferences.OnSharedPreferenceChangeListener mCustomLayoutsChangedListener =
      (preferences, key) -> {
        if (CUSTOM_LAYOUTS_PREF.equals(key)) clearAddOnList();
      };

  public KeyboardFactory(@NonNull Context context) {
    super(
        context,
        DirectBootAwareSharedPreferences.create(context),
        TAG,
        "com.menny.android.anysoftkeyboard.KEYBOARD",
        "com.menny.android.anysoftkeyboard.keyboards",
        "Keyboards",
        "Keyboard",
        PREF_ID_PREFIX,
        com.anysoftkeyboard.language.english.pack.R.xml.english_keyboards,
        R.string.settings_default_keyboard_id,
        true,
        BuildConfig.TESTING_BUILD);
    mSharedPreferences.registerOnSharedPreferenceChangeListener(mCustomLayoutsChangedListener);
  }

  @Override
  protected KeyboardAddOnAndBuilder createConcreteAddOn(
      Context askContext,
      Context context,
      int apiVersion,
      CharSequence prefId,
      CharSequence name,
      CharSequence description,
      boolean isHidden,
      int sortIndex,
      AttributeSet attrs) {
    final int layoutResId =
        attrs.getAttributeResourceValue(null, XML_LAYOUT_RES_ID_ATTRIBUTE, AddOn.INVALID_RES_ID);
    final int landscapeLayoutResId =
        attrs.getAttributeResourceValue(
            null, XML_LANDSCAPE_LAYOUT_RES_ID_ATTRIBUTE, AddOn.INVALID_RES_ID);
    final int iconResId =
        attrs.getAttributeResourceValue(
            null, XML_ICON_RES_ID_ATTRIBUTE, R.drawable.sym_keyboard_notification_icon);
    final String defaultDictionary = attrs.getAttributeValue(null, XML_DICTIONARY_NAME_ATTRIBUTE);
    final String additionalIsLetterExceptions =
        attrs.getAttributeValue(null, XML_ADDITIONAL_IS_LETTER_EXCEPTIONS_ATTRIBUTE);
    String sentenceSeparators =
        attrs.getAttributeValue(null, XML_SENTENCE_SEPARATOR_CHARACTERS_ATTRIBUTE);
    if (TextUtils.isEmpty(sentenceSeparators)) sentenceSeparators = DEFAULT_SENTENCE_SEPARATORS;
    final int physicalTranslationResId =
        attrs.getAttributeResourceValue(
            null, XML_PHYSICAL_TRANSLATION_RES_ID_ATTRIBUTE, AddOn.INVALID_RES_ID);
    final boolean hasRadicalInput =
        attrs.getAttributeBooleanValue(null, XML_HAS_RADICAL_INPUT_ATTRIBUTE, false);
    final String radicalKeyCharacters =
        attrs.getAttributeValue(null, XML_RADICAL_KEY_CHARACTERS_ATTRIBUTE);
    final String radicalCandidateSelectorKeys =
        attrs.getAttributeValue(null, XML_RADICAL_CANDIDATE_SELECTOR_KEYS_ATTRIBUTE);
    final String phoneticLayout = attrs.getAttributeValue(null, XML_PHONETIC_LAYOUT_ATTRIBUTE);
    final CharSequence variantOf =
        getTextFromResourceOrText(context, attrs, XML_VARIANT_OF_ATTRIBUTE);
    final boolean asciiOnlyPopups =
        attrs.getAttributeBooleanValue(null, XML_ASCII_ONLY_POPUPS_ATTRIBUTE, false);
    final boolean supportsGestureTyping =
        attrs.getAttributeBooleanValue(
            null, XML_SUPPORTS_GESTURE_TYPING_ATTRIBUTE, !hasRadicalInput);
    // A keyboard is enabled by default if it is the first one (index==1)
    final boolean keyboardDefault =
        attrs.getAttributeBooleanValue(null, XML_DEFAULT_ATTRIBUTE, sortIndex == 1);

    // asserting
    if (layoutResId == AddOn.INVALID_RES_ID) {
      Logger.e(
          TAG,
          "External Keyboard does not include all mandatory details! Will not create"
              + " keyboard.");
      return null;
    } else {
      if (BuildConfig.DEBUG) {
        Logger.d(
            TAG,
            "External keyboard details: prefId:"
                + prefId
                + " nameId:"
                + name
                + " resId:"
                + layoutResId
                + " landscapeResId:"
                + landscapeLayoutResId
                + " iconResId:"
                + iconResId
                + " defaultDictionary:"
                + defaultDictionary);
      }
      return new KeyboardAddOnAndBuilder(
          askContext,
          context,
          apiVersion,
          prefId,
          name,
          layoutResId,
          landscapeLayoutResId,
          defaultDictionary,
          iconResId,
          physicalTranslationResId,
          additionalIsLetterExceptions,
          sentenceSeparators,
          description,
          isHidden,
          sortIndex,
          hasRadicalInput,
          radicalKeyCharacters,
          radicalCandidateSelectorKeys,
          phoneticLayout,
          variantOf == null ? null : variantOf.toString(),
          asciiOnlyPopups,
          supportsGestureTyping,
          keyboardDefault);
    }
  }

  /** The layouts the user made. Each of them is also a keyboard add-on of this factory. */
  @NonNull
  public List<CustomLayout> getCustomLayouts() {
    final List<CustomLayout> layouts = new ArrayList<>();
    try {
      final JSONArray json = new JSONArray(mSharedPreferences.getString(CUSTOM_LAYOUTS_PREF, "[]"));
      for (int i = 0; i < json.length(); i++) {
        layouts.add(CustomLayout.fromJson(json.getJSONObject(i)));
      }
    } catch (JSONException e) {
      Logger.w(TAG, e, "Failed to read the custom layouts.");
    }
    return layouts;
  }

  @Nullable
  public CustomLayout getCustomLayout(@NonNull String id) {
    for (CustomLayout layout : getCustomLayouts()) {
      if (layout.id.equals(id)) return layout;
    }
    return null;
  }

  /** Stores {@code layout}, in place of the stored one with the same id if there is one. */
  public void saveCustomLayout(@NonNull CustomLayout layout) {
    final List<CustomLayout> layouts = getCustomLayouts();
    int index = 0;
    while (index < layouts.size() && !layouts.get(index).id.equals(layout.id)) index++;
    if (index < layouts.size()) layouts.set(index, layout);
    else layouts.add(layout);
    storeCustomLayouts(layouts);
  }

  public void deleteCustomLayout(@NonNull String id) {
    final List<CustomLayout> layouts = getCustomLayouts();
    for (int index = layouts.size() - 1; index >= 0; index--) {
      if (layouts.get(index).id.equals(id)) layouts.remove(index);
    }
    storeCustomLayouts(layouts);
  }

  private void storeCustomLayouts(List<CustomLayout> layouts) {
    final JSONArray json = new JSONArray();
    try {
      for (CustomLayout layout : layouts) json.put(layout.toJson());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    mSharedPreferences.edit().putString(CUSTOM_LAYOUTS_PREF, json.toString()).apply();
    // not left to the listener, which may be told later
    clearAddOnList();
  }

  @NonNull
  @Override
  protected List<KeyboardAddOnAndBuilder> createDerivedAddOns() {
    final List<KeyboardAddOnAndBuilder> addOns = new ArrayList<>();
    for (CustomLayout layout : getCustomLayouts()) {
      // a layout whose keyboard is not installed (anymore) stays stored and is not offered
      final KeyboardAddOnAndBuilder base = getAddOnById(layout.baseId);
      final KeyboardAddOnAndBuilder addOn =
          base == null ? null : base.withLayout(layout, base.getSortIndex());
      if (addOn != null) addOns.add(addOn);
    }
    return addOns;
  }

  /**
   * The keyboards that are layouts of the keyboard {@code groupId} (see {@link
   * KeyboardAddOnAndBuilder#getLayoutGroupId}): itself, its variants and the user's layouts of
   * them.
   */
  @NonNull
  public List<KeyboardAddOnAndBuilder> getLayoutsOf(@NonNull String groupId) {
    final List<KeyboardAddOnAndBuilder> layouts = new ArrayList<>();
    for (KeyboardAddOnAndBuilder addOn : getAllAddOns()) {
      if (addOn.getLayoutGroupId().equals(groupId)) layouts.add(addOn);
    }
    return layouts;
  }

  public boolean hasMultipleAlphabets() {
    return getEnabledIds().size() > 1;
  }

  @Override
  protected boolean isAddOnEnabledByDefault(@NonNull String addOnId) {
    final KeyboardAddOnAndBuilder addOnById = getAddOnById(addOnId);
    return super.isAddOnEnabledByDefault(addOnId)
        || (addOnById != null && addOnById.getKeyboardDefaultEnabled());
  }
}
