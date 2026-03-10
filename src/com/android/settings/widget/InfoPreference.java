package com.android.settings.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * add for FPSW-919
 * Fix the color of the Preference title
 * Fix the layout of the Preference item
 */
public class InfoPreference extends Preference {

    private static final int SINGLE_LINE_THRESHOLD = 30;
    private static final int MAX_MULTI_LINES = 2;

    public InfoPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary == null) {
            return;
        }
        CharSequence text = getSummary();
        if (TextUtils.isEmpty(text)) {
            summary.setMaxLines(1);
            summary.setSingleLine(true);
            return;
        }

        // avoid RecyclerView reuse pollution
        summary.setEllipsize(TextUtils.TruncateAt.END);

        // manually determine whether the content should be displayed in multiple lines
        if (text.toString().contains("\n")) {
            summary.setSingleLine(false);
            summary.setMaxLines(MAX_MULTI_LINES);
            return;
        }
        if (text.length() <= SINGLE_LINE_THRESHOLD) {
            summary.setSingleLine(true);
            summary.setMaxLines(1);
        } else {
            summary.setSingleLine(false);
            summary.setMaxLines(MAX_MULTI_LINES);
        }
    }
}