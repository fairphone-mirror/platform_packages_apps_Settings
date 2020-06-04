/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import android.widget.Toast;
import java.io.File;
import java.util.Locale;

/**
 * {@link Activity} that displays regulatory information for the "Regulatory information"
 * preference item, and when "*#07#" is dialed on the Phone keypad. To enable this feature,
 * set the "config_show_regulatory_info" boolean to true in a device overlay resource, and in the
 * same overlay, either add a drawable named "regulatory_info.png" containing a graphical version
 * of the required regulatory info (If ro.bootloader.hardware.sku property is set use
 * "regulatory_info_<sku>.png where sku is ro.bootloader.hardware.sku property value in lowercase"),
 * or add a string resource named "regulatory_info_text" with an HTML version of the required
 * information (text will be centered in the dialog).
 */
public class RegulatoryInfoDisplayActivity extends Activity  {
    private static final String TAG = "RegulatoryInfoDisplayActivity";
    private final String REGULATORY_INFO_RESOURCE = "regulatory_info";
    private static final String DEFAULT_REGULATORY_INFO_FILEPATH =
            "/data/misc/elabel/regulatory_info.png";
    private static final String REGULATORY_INFO_FILEPATH_TEMPLATE =
            "/data/misc/elabel/regulatory_info_%s.png";

    private static final String DEFAULT_ELABEL_PATH = "/system_ext/etc/eLabel.html.gz";
    public static final String EXTRA_MODULE = "extra.module";

    private static final String FILEPROVIDER_AUTHORITY = "com.android.settings.files";

    /**
     * Display the regulatory info graphic in a dialog window.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isFilePathValid(DEFAULT_ELABEL_PATH)) {
            showSelectedFile(DEFAULT_ELABEL_PATH);
        }
    }

    private void showSelectedFile(final String path) {
        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "The elabel file is empty");
            showErrorAndFinish();
            return;
        }

        final File file = new File(path);
        if (!isFileValid(file)) {
            Log.e(TAG, "elabel file " + path + " does not exist");
            showErrorAndFinish();
            return;
        }
        showHtmlFromUri(FileProvider.getUriForFile(getApplicationContext(), FILEPROVIDER_AUTHORITY, file));
    }

    private void showErrorAndFinish() {
        Toast.makeText(this, R.string.settings_elabel_activity_unavailable, Toast.LENGTH_LONG)
                .show();
        finish();
    }

    private void showHtmlFromUri(Uri uri) {
        // Kick off external viewer due to WebView security restrictions; we
        // carefully point it at HTMLViewer, since it offers to decompress
        // before viewing.
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/html");
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.regulatory_labels));
        intent.putExtra(EXTRA_MODULE, "Regulatory information");
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setPackage("com.android.htmlviewer");

        try {
            startActivity(intent);
            finish();
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to find viewer", e);
            showErrorAndFinish();
        }
    }

    private boolean isFilePathValid(final String path) {
        return !TextUtils.isEmpty(path) && isFileValid(new File(path));
    }

    @VisibleForTesting
    boolean isFileValid(final File file) {
        return file.exists() && file.length() != 0;
    }

    @VisibleForTesting
    public static String getSku() {
        return SystemProperties.get("ro.boot.hardware.sku", "");
    }
}
