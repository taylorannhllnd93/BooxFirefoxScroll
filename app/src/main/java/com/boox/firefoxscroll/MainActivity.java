package com.boox.firefoxscroll;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView amountLabel;
    private TextView lastKeyLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        root.setGravity(Gravity.TOP);

        TextView title = new TextView(this);
        title.setText("BOOX Firefox Scroll");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView intro = text("Turns the BOOX physical page buttons into real vertical swipe gestures while Firefox is in the foreground. No root and no network access.");
        intro.setPadding(0, dp(10), 0, dp(18));
        root.addView(intro, matchWrap());

        Button accessibility = new Button(this);
        accessibility.setText("1. Enable accessibility service");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, matchWrap());

        TextView boox = text("2. In BOOX, long-press Firefox → Optimize → Others → Customize Buttons → Volume. Then open Firefox and test the two side buttons.");
        boox.setPadding(0, dp(16), 0, dp(10));
        root.addView(boox, matchWrap());

        Switch reverse = new Switch(this);
        reverse.setText("Reverse top/bottom button direction");
        reverse.setChecked(prefs.getBoolean("reverse", false));
        reverse.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean("reverse", isChecked).apply());
        root.addView(reverse, matchWrap());

        amountLabel = text("");
        amountLabel.setPadding(0, dp(18), 0, 0);
        root.addView(amountLabel, matchWrap());

        SeekBar amount = new SeekBar(this);
        amount.setMax(45);
        int saved = Math.max(45, Math.min(90, prefs.getInt("scroll_percent", 78)));
        amount.setProgress(saved - 45);
        updateAmount(saved);
        amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 45;
                updateAmount(value);
                if (fromUser) prefs.edit().putInt("scroll_percent", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(amount, matchWrap());

        lastKeyLabel = text("");
        lastKeyLabel.setPadding(0, dp(18), 0, dp(8));
        root.addView(lastKeyLabel, matchWrap());

        TextView supported = text("Recognized inputs: Volume Up/Down and Android Page Up/Down. Firefox Release, Beta, Nightly/Fenix, Focus, and Klar are included.");
        supported.setTextSize(14);
        root.addView(supported, matchWrap());

        TextView privacy = text("Privacy: the service needs Android Accessibility permission to receive hardware key events and inject a swipe. This app contains no INTERNET permission and does not save page text, URLs, or browsing history.");
        privacy.setTextSize(14);
        privacy.setPadding(0, dp(18), 0, 0);
        root.addView(privacy, matchWrap());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lastKeyLabel != null) {
            String last = prefs.getString("last_key", "None yet");
            lastKeyLabel.setText("Last recognized Firefox button: " + last);
        }
    }

    private void updateAmount(int value) {
        amountLabel.setText("Scroll distance per press: " + value + "%");
    }

    private TextView text(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(16);
        tv.setLineSpacing(0, 1.08f);
        return tv;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
