package com.android.settings.anc.util;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.IdRes;

import com.android.settings.R;

public class BottomDialog extends Dialog {

    private View layoutView;

    public BottomDialog(Context context, int layoutId) {
        super(context, R.style.BottomDialog);
        layoutView = LayoutInflater.from(context).inflate(layoutId,null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutView);

        Window window = this.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setGravity(Gravity.BOTTOM);
        window.setAttributes(params);
        window.setWindowAnimations(R.style.BottomDialog_Animation);
    }

    public BottomDialog setOnClickListener(@IdRes int viewId, View.OnClickListener listener){
        layoutView.findViewById(viewId).setOnClickListener(listener);
        return this;
    }

    public <T extends View> T getView(@IdRes int viewId){
        return layoutView.findViewById(viewId);
    }
}

