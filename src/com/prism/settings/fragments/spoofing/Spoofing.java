/*
 * Copyright (C) 2025 euclidOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.prism.settings.fragments.spoofing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.os.SystemProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.euclid.SystemRestartUtils;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settingslib.search.SearchIndexable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.euclid.support.preferences.SystemPropertySwitchPreference;
import com.prism.settings.utils.Utils;

import com.prism.settings.preferences.KeyboxDataPreference;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;

 import org.json.JSONArray;
 import org.json.JSONException;
 import org.json.JSONObject;

@SearchIndexable
public class Spoofing extends SettingsPreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "Spoofing";

    private static final String KEY_SYSTEM_WIDE_CATEGORY = "spoofing_system_wide_category";
    private static final String KEY_PIF_JSON_FILE_PREFERENCE = "pif_json_file_preference";
    private static final String KEY_GAME_PROPS_JSON_FILE_PREFERENCE = "game_props_json_file_preference";
    private static final String KEY_UPDATE_JSON_BUTTON = "update_pif_json";

    // System properties
    private static final String SYS_GMS_SPOOF = "persist.sys.pixelprops.gms";
    private static final String SYS_GOOGLE_SPOOF = "persist.sys.pphooks.enable";
    private static final String SYS_GAMEPROP_SPOOF = "persist.sys.gameprops.enabled";
    private static final String SYS_GPHOTOS_SPOOF = "persist.sys.gphooks.enable";
    private static final String SYS_SNAP_SPOOF = "persist.sys.snap.enable";
    private static final String SYS_VENDING_SPOOF = "persist.sys.vending.enable";
    private static final String SYS_ENABLE_TENSOR_FEATURES = "persist.sys.features.tensor";
    private static final String KEYBOX_DATA_KEY = "keybox_data_setting";

    private Preference mGamePropsJsonFilePreference;
    private Preference mPifJsonFilePreference;
    private Preference mUpdateJsonButton;
    private PreferenceCategory mSystemWideCategory;

    private SystemPropertySwitchPreference mGmsSpoof;
    private SystemPropertySwitchPreference mGoogleSpoof;
    private SystemPropertySwitchPreference mGamePropsSpoof;
    private SystemPropertySwitchPreference mGphotosSpoof;
    private SystemPropertySwitchPreference mSnapSpoof;
    private SystemPropertySwitchPreference mVendingSpoof;
    private SystemPropertySwitchPreference mTensorFeaturesToggle;
    private ActivityResultLauncher<Intent> mKeyboxFilePickerLauncher;
    private KeyboxDataPreference mKeyboxDataPreference;

    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        addPreferencesFromResource(R.xml.spoofing_settings);

        final Context context = getContext();
        final PreferenceScreen prefScreen = getPreferenceScreen();
        final Resources res = context.getResources();

        getActivity().setTitle(R.string.prism_spoofing_dashboard_title);

        mSystemWideCategory = findPreference(KEY_SYSTEM_WIDE_CATEGORY);
        mGmsSpoof = findPreference(SYS_GMS_SPOOF);
        mGoogleSpoof = findPreference(SYS_GOOGLE_SPOOF);
        mGamePropsSpoof = findPreference(SYS_GAMEPROP_SPOOF);
        mGphotosSpoof = findPreference(SYS_GPHOTOS_SPOOF);
        mSnapSpoof = findPreference(SYS_SNAP_SPOOF);
        mVendingSpoof = findPreference(SYS_VENDING_SPOOF);
        mTensorFeaturesToggle = findPreference(SYS_ENABLE_TENSOR_FEATURES);

        mPifJsonFilePreference = findPreference(KEY_PIF_JSON_FILE_PREFERENCE);
        mGamePropsJsonFilePreference = findPreference(KEY_GAME_PROPS_JSON_FILE_PREFERENCE);
        mUpdateJsonButton = findPreference(KEY_UPDATE_JSON_BUTTON);

        String model = SystemProperties.get("ro.product.model");
        boolean isTensorDevice = model.matches("Pixel [6-9][a-zA-Z ]*");

        if (Utils.isCurrentlySupportedPixel()) {
            mGoogleSpoof.setDefaultValue(false);
            if (isMainlineTensorModel(model)) {
                mSystemWideCategory.removePreference(mGoogleSpoof);
            }
        }

        if (isTensorDevice) {
            mSystemWideCategory.removePreference(mTensorFeaturesToggle);
        }

        mGmsSpoof.setOnPreferenceChangeListener(this);
        mGoogleSpoof.setOnPreferenceChangeListener(this);
        mGphotosSpoof.setOnPreferenceChangeListener(this);
        mGamePropsSpoof.setOnPreferenceChangeListener(this);
        mSnapSpoof.setOnPreferenceChangeListener(this);
        mVendingSpoof.setOnPreferenceChangeListener(this);
        mTensorFeaturesToggle.setOnPreferenceChangeListener(this);

        mPifJsonFilePreference.setOnPreferenceClickListener(preference -> {
            openFileSelector(10001);
            return true;
        });

        mGamePropsJsonFilePreference.setOnPreferenceClickListener(preference -> {
            openFileSelector(10002);
            return true;
        });

        mUpdateJsonButton.setOnPreferenceClickListener(preference -> {
            updatePropertiesFromUrl("https://raw.githubusercontent.com/euclidTeam/vendor_euclid/refs/heads/16/PlayIntegrity/pif.json");
            return true;
        });

        mKeyboxFilePickerLauncher = registerForActivityResult(
             new ActivityResultContracts.StartActivityForResult(),
             result -> {
                 if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                     Uri uri = result.getData().getData();
                     Preference pref = findPreference(KEYBOX_DATA_KEY);
                     if (pref instanceof KeyboxDataPreference) {
                         ((KeyboxDataPreference) pref).handleFileSelected(uri);
                     }
                 }
             }
         );

        Preference showPropertiesPref = findPreference("show_pif_properties");
        if (showPropertiesPref != null) {
            showPropertiesPref.setOnPreferenceClickListener(preference -> {
                showPropertiesDialog();
                return true;
            });
        }
    }

    private boolean isMainlineTensorModel(String model) {
        return model.matches("Pixel [8-9][a-zA-Z ]*");
    }

    private void openFileSelector(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                if (requestCode == 10001) {
                    loadPifJson(uri);
                } else if (requestCode == 10002) {
                    loadGameSpoofingJson(uri);
                }
            }
        }
    }

    @Override
     public void onViewCreated(View view, Bundle savedInstanceState) {
         super.onViewCreated(view, savedInstanceState);
         mKeyboxDataPreference = findPreference(KEYBOX_DATA_KEY);
         if (mKeyboxDataPreference != null) {
             mKeyboxDataPreference.setFilePickerLauncher(mKeyboxFilePickerLauncher);
         }
     }

    private void showPropertiesDialog() {
        StringBuilder properties = new StringBuilder();
        try {
            JSONObject jsonObject = new JSONObject();
            String[] keys = {
                    "persist.sys.pihooks_ID",
                    "persist.sys.pihooks_BRAND",
                    "persist.sys.pihooks_DEVICE",
                    "persist.sys.pihooks_FINGERPRINT",
                    "persist.sys.pihooks_MANUFACTURER",
                    "persist.sys.pihooks_MODEL",
                    "persist.sys.pihooks_PRODUCT",
                    "persist.sys.pihooks_SECURITY_PATCH",
                    "persist.sys.pihooks_DEVICE_INITIAL_SDK_INT"
            };
            for (String key : keys) {
                String value = SystemProperties.get(key, null);
                if (value != null) {
                    String buildKey = key.replace("persist.sys.pihooks_", "");
                    jsonObject.put(buildKey, value);
                }
            }
            properties.append(jsonObject.toString(4));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON from properties", e);
            properties.append(getString(R.string.error_loading_properties));
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.show_pif_properties_title)
                .setMessage(properties.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updatePropertiesFromUrl(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try (InputStream inputStream = connection.getInputStream()) {
                    String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    JSONObject jsonObject = new JSONObject(json);
                    String spoofedModel = jsonObject.optString("MODEL", "Unknown model");
                    for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                        String key = it.next();
                        String value = jsonObject.getString(key);
                        SystemProperties.set("persist.sys.pihooks_" + key, value);
                    }
                    mHandler.post(() -> Toast.makeText(getContext(),
                            getString(R.string.toast_spoofing_success, spoofedModel),
                            Toast.LENGTH_LONG).show());
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading JSON or setting properties", e);
                mHandler.post(() -> Toast.makeText(getContext(),
                        R.string.toast_spoofing_failure, Toast.LENGTH_LONG).show());
            }
            mHandler.postDelayed(() -> SystemRestartUtils.showSystemRestartDialog(getContext()), 1250);
        }).start();
    }

    private void loadPifJson(Uri uri) {
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = jsonObject.getString(key);
                    SystemProperties.set("persist.sys.pihooks_" + key, value);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading PIF JSON", e);
        }
        mHandler.postDelayed(() -> SystemRestartUtils.showSystemRestartDialog(getContext()), 1250);
    }

    private void loadGameSpoofingJson(Uri uri) {
        try (InputStream inputStream = getActivity().getContentResolver().openInputStream(uri)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);
                for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                    String key = it.next();
                    if (key.startsWith("PACKAGES_") && !key.endsWith("_DEVICE")) {
                        String deviceKey = key + "_DEVICE";
                        if (jsonObject.has(deviceKey)) {
                            JSONObject deviceProps = jsonObject.getJSONObject(deviceKey);
                            JSONArray packages = jsonObject.getJSONArray(key);
                            for (int i = 0; i < packages.length(); i++) {
                                setGameProps(packages.getString(i), deviceProps);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading Game Props JSON", e);
        }
        mHandler.postDelayed(() -> SystemRestartUtils.showSystemRestartDialog(getContext()), 1250);
    }

    private void setGameProps(String packageName, JSONObject deviceProps) {
        try {
            for (Iterator<String> it = deviceProps.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = deviceProps.getString(key);
                SystemProperties.set("persist.sys.gameprops." + packageName + "." + key, value);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing device properties", e);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGmsSpoof
                || preference == mGoogleSpoof
                || preference == mGphotosSpoof
                || preference == mGamePropsSpoof
                || preference == mSnapSpoof
                || preference == mVendingSpoof) {
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        if (preference == mTensorFeaturesToggle) {
            boolean enabled = (Boolean) newValue;
            SystemProperties.set(SYS_ENABLE_TENSOR_FEATURES, enabled ? "true" : "false");
            SystemRestartUtils.showSystemRestartDialog(getContext());
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.PRISM;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.spoofing_settings);
}
