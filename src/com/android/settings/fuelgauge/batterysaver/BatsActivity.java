package com.android.settings.fuelgauge.batterysaver;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.android.settings.core.SettingsBaseActivity;
import com.android.settings.fuelgauge.batterysaver.BatteryChargingState;
import android.view.View;
import android.widget.Toolbar;

import com.android.settings.R;

/**
 * author : suntianhai
 * e-mail : tianhai.sun@t2mobile.com
 * time   : 2023/08/04
 * desc   : add for FP5-2351 to set Battery Charging mode
 * version: 1.0
 *
 */
public class BatsActivity extends SettingsBaseActivity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content_frame, new BatteryChargingState())
            .commit();
    }
}
