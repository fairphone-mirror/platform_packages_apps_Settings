package com.android.settings.anc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.settings.R;

public class TempActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.faceunlock_settings_option);
        finish();
    }
}
