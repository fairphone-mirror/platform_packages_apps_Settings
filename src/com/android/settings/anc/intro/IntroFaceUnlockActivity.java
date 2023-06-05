package com.android.settings.anc.intro;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.view.Menu;

import com.android.settings.R;
import com.android.settings.anc.enroll.EnrollActivity;
import com.android.settings.anc.BaseActivity;

public class IntroFaceUnlockActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro_face_unlock);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
        }
        TextView mTvDisclaimer = findViewById(R.id.tv_disclaimer);
        Button mBtStart = findViewById(R.id.bt_start);
        mTvDisclaimer.setOnClickListener(v->{
            Intent disclaimerIntent = new Intent(IntroFaceUnlockActivity.this, DisclaimerActivity.class);
            startActivity(disclaimerIntent);
        });
        mBtStart.setOnClickListener(v->{
            Intent enrollIntent = new Intent(IntroFaceUnlockActivity.this, EnrollActivity.class);
            startActivity(enrollIntent);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() ==  android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }
}