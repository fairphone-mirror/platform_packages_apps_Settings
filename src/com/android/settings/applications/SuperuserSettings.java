package com.android.settings.applications;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;

import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.Callbacks;

public class SuperuserSettings extends AppInfoWithHeader
        implements OnClickListener, Callbacks {
    private static final String TAG = SuperuserSettings.class.getSimpleName();

    private static final String KEY_SUPERUSER = "superuser";
    private static final String KEY_SUPERUSER_PREF = "superuser_preference";

    private Preference mSuperuserPreference;

    private RadioButton mSuAskRadioButton;
    private RadioButton mSuAllowedRadioButton;
    private RadioButton mSuDeniedRadioButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.superuser_settings);
        setupViews();
    }

    private void setupViews() {
      mSuperuserPreference = findPreference(KEY_SUPERUSER);

      mSuAskRadioButton = (RadioButton) ((LayoutPreference) findPreference(KEY_SUPERUSER_PREF))
              .findViewById(R.id.askRadioButton);

      mSuAllowedRadioButton = (RadioButton) ((LayoutPreference) findPreference(KEY_SUPERUSER_PREF))
              .findViewById(R.id.alwaysRadioButton);

      mSuDeniedRadioButton = (RadioButton) ((LayoutPreference) findPreference(KEY_SUPERUSER_PREF))
              .findViewById(R.id.neverRadioButton);
    }

    @Override
    public void onClick(View v) {
        if (v == mSuAskRadioButton) {
            mSuAskRadioButton.setChecked(true);
            Context c = mSuAllowedRadioButton.getContext();
            AppOpsManager appOpsManager = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            appOpsManager.setMode(AppOpsManager.OP_SU, mAppEntry.info.uid, mAppEntry.info.packageName,
                AppOpsManager.MODE_ASK);
        } else if (v == mSuDeniedRadioButton) {
            mSuDeniedRadioButton.setChecked(true);
            Context c = mSuAllowedRadioButton.getContext();
            AppOpsManager appOpsManager = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            appOpsManager.setMode(AppOpsManager.OP_SU, mAppEntry.info.uid, mAppEntry.info.packageName,
                AppOpsManager.MODE_ERRORED);
        } else if (v == mSuAllowedRadioButton) {
            mSuAllowedRadioButton.setChecked(true);
            Context c = mSuAllowedRadioButton.getContext();
            AppOpsManager appOpsManager = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            appOpsManager.setMode(AppOpsManager.OP_SU, mAppEntry.info.uid, mAppEntry.info.packageName,
                AppOpsManager.MODE_ALLOWED);
        }
    }

    @Override
    protected boolean refreshUi() {
        retrieveAppEntry();
        if (mAppEntry == null) {
            return false;
        }
        refreshButtons();

        return true;
    }

    private void refreshButtons() {
        initSuperuserButtons();
    }

    private void initSuperuserButtons() {
        Context c = mSuAllowedRadioButton.getContext();
        AppOpsManager appOpsManager = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OP_SU, mAppEntry.info.uid, mAppEntry.info.packageName);
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
                mSuAllowedRadioButton.setChecked(true);
                break;
            case AppOpsManager.MODE_ERRORED:
            case AppOpsManager.MODE_IGNORED:
                mSuDeniedRadioButton.setChecked(true);
                break;
            case AppOpsManager.MODE_ASK:
            default:
                mSuAskRadioButton.setChecked(true);
        }
        mSuAskRadioButton.setOnClickListener(this);
        mSuAllowedRadioButton.setOnClickListener(this);
        mSuDeniedRadioButton.setOnClickListener(this);
    }

    @Override
    protected AlertDialog createDialog(int id, int errorCode) {
        // No dialogs for superuser settings.
        return null;
    }

    public static CharSequence getSummary(AppEntry appEntry, Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OP_SU, appEntry.info.uid, appEntry.info.packageName);
        switch (mode) {
          case AppOpsManager.MODE_ALLOWED:
              return context.getString(R.string.superuser_always);
          case AppOpsManager.MODE_ERRORED:
          case AppOpsManager.MODE_IGNORED:
              return context.getString(R.string.superuser_never);
          case AppOpsManager.MODE_ASK:
              return context.getString(R.string.superuser_ask);
          default:
              return context.getString(R.string.superuser_ask);
        }
    }

    @Override
    protected int getMetricsCategory() {
        return MetricsEvent.FP_SUPERUSER;
    }
}
