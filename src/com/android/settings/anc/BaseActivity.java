package com.android.settings.anc;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import android.view.WindowManager;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;

import com.android.settings.R;

public class BaseActivity extends Activity {
    FrameLayout mBody;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        mBody = findViewById(R.id.content_frame);
    }

    protected void setLayoutId(@LayoutRes int id) {
        View v = View.inflate(this, id, null);
        mBody.addView(v);
    }

}
