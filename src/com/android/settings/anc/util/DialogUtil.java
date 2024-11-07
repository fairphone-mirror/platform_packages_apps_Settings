package com.android.settings.anc.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;

import com.android.settings.R;

public class DialogUtil {

	private Activity mActivity;

	public DialogUtil(Activity activity) {
		this.mActivity = activity;
	}

	private Dialog createDialog(int layout) {
		Dialog dialog = new Dialog(mActivity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		dialog.setContentView(layout);

		return dialog;
	}


	public void showDialog(int title ,int message, final DialogInterface.OnClickListener onOK) {

		AlertDialog dialog = new AlertDialog.Builder(mActivity,R.style.Theme_Dialog)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(R.string.okay, onOK /* onClickListener */)
				.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				} )
				.create();
		dialog.show();
	}

	public void onDestroy() {
		mActivity = null;
	}
}