package com.boox.firefoxscroll;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private interface Labeler { String label(int value); }
    private interface ValueListener { void onValue(int value); }

    private SharedPreferences prefs;
    private TextView lastKeyLabel;
    private LinearLayout appList;
    private View repeatRow;
    private TextView repeatLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("BOOX Firefox Scroll");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView intro = text("Turns the BOOX physical page buttons into real vertical swipes "
                + "in the apps you check below. No root and no network access.");
        intro.setPadding(0, dp(10), 0, dp(18));
        root.addView(intro, matchWrap());

        Button enable = new Button(this);
        enable.setText("1. Enable accessibility service");
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            }
        });
        root.addView(enable, matchWrap());

        TextView step2 = text("2. For each app checked below, long-press it on the BOOX "
                + "\u2192 Optimize \u2192 Others \u2192 Customize Buttons \u2192 Volume.");
        step2.setPadding(0, dp(16), 0, dp(4));
        root.addView(step2, matchWrap());

        // ---- Scrolling ----
        root.addView(header("Scrolling"), matchWrap());

        root.addView(toggle("Reverse top/bottom button direction", Prefs.REVERSE, false, null),
                matchWrap());

        addSlider(root, Prefs.SCROLL_MIN, Prefs.SCROLL_MAX, Prefs.SCROLL_STEP,
                Prefs.scrollPercent(prefs),
                new Labeler() {
                    @Override
                    public String label(int v) {
                        String s = "Scroll distance per press: " + v + "% of the screen";
                        if (v > 70) s += "\n(split into several swipes)";
                        return s;
                    }
                },
                new ValueListener() {
                    @Override
                    public void onValue(int v) {
                        prefs.edit().putInt(Prefs.SCROLL_PERCENT, v).apply();
                    }
                });

        Switch precise = toggle("Precise scrolling (stops exactly, no momentum)",
                Prefs.PRECISE, true, null);
        precise.setPadding(0, dp(12), 0, 0);
        root.addView(precise, matchWrap());

        // ---- Hold ----
        root.addView(header("Hold to keep scrolling"), matchWrap());

        root.addView(toggle("Keep scrolling while a button is held", Prefs.HOLD, true,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        setRepeatEnabled(checked);
                    }
                }), matchWrap());

        repeatRow = addSlider(root, Prefs.REPEAT_MIN, Prefs.REPEAT_MAX, Prefs.REPEAT_STEP,
                Prefs.repeatMs(prefs),
                new Labeler() {
                    @Override
                    public String label(int v) {
                        return String.format(Locale.US,
                                "Time between scrolls while held: %.1f s", v / 1000f);
                    }
                },
                new ValueListener() {
                    @Override
                    public void onValue(int v) {
                        prefs.edit().putInt(Prefs.REPEAT_MS, v).apply();
                    }
                });
        setRepeatEnabled(prefs.getBoolean(Prefs.HOLD, true));

        // ---- Apps ----
        root.addView(header("Apps"), matchWrap());
        TextView appsNote = text("The buttons only scroll in checked apps. Everywhere else "
                + "they keep their normal job (for example, volume).");
        appsNote.setTextSize(15f);
        root.addView(appsNote, matchWrap());

        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        appList.setPadding(0, dp(8), 0, 0);
        root.addView(appList, matchWrap());
        buildAppList();

        // ---- Footer ----
        lastKeyLabel = text("");
        lastKeyLabel.setPadding(0, dp(18), 0, dp(8));
        root.addView(lastKeyLabel, matchWrap());

        TextView inputs = text("Recognized inputs: Volume Up/Down and Android Page Up/Down.");
        inputs.setTextSize(15f);
        root.addView(inputs, matchWrap());

        TextView privacy = text("Privacy: the service needs Android Accessibility permission to "
                + "receive hardware key events and inject a swipe. This app contains no INTERNET "
                + "permission and does not save page text, URLs, or browsing history.");
        privacy.setTextSize(15f);
        privacy.setPadding(0, dp(18), 0, 0);
        root.addView(privacy, matchWrap());

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lastKeyLabel != null) {
            lastKeyLabel.setText("Last recognized button: "
                    + prefs.getString(Prefs.LAST_KEY, "None yet"));
        }
    }

    // ---------- App list ----------

    private void buildAppList() {
        appList.removeAllViews();
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);

        final Map<String, String> labels = new HashMap<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || labels.containsKey(pkg)) continue;
            labels.put(pkg, String.valueOf(ri.loadLabel(pm)));
        }

        final Set<String> selected = Prefs.apps(prefs);
        List<String> pkgs = new ArrayList<>(labels.keySet());
        Collections.sort(pkgs, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean sa = selected.contains(a), sb = selected.contains(b);
                if (sa != sb) return sa ? -1 : 1;
                return labels.get(a).compareToIgnoreCase(labels.get(b));
            }
        });

        for (final String pkg : pkgs) {
            CheckBox cb = new CheckBox(this);
            cb.setText(labels.get(pkg));
            cb.setTextSize(17f);
            cb.setPadding(dp(4), dp(6), 0, dp(6));
            cb.setChecked(selected.contains(pkg));
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    Set<String> apps = Prefs.apps(prefs);
                    if (checked) apps.add(pkg); else apps.remove(pkg);
                    prefs.edit().putStringSet(Prefs.APPS, apps).apply();
                }
            });
            appList.addView(cb, matchWrap());
        }

        if (pkgs.isEmpty()) {
            appList.addView(text("No apps found."), matchWrap());
        }
    }

    // ---------- UI helpers ----------

    private void setRepeatEnabled(boolean enabled) {
        if (repeatRow == null) return;
        setEnabledDeep(repeatRow, enabled);
        if (repeatLabel != null) repeatLabel.setAlpha(enabled ? 1f : 0.4f);
    }

    private static void setEnabledDeep(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) v;
            for (int i = 0; i < l.getChildCount(); i++) setEnabledDeep(l.getChildAt(i), enabled);
        }
    }

    /** Adds a label plus a [\u2212] slider [+] row. Returns the row. */
    private View addSlider(LinearLayout root, final int min, int max, final int step, int value,
                           final Labeler labeler, final ValueListener listener) {
        final TextView label = text(labeler.label(value));
        label.setPadding(0, dp(14), 0, dp(2));
        root.addView(label, matchWrap());
        if (min == Prefs.REPEAT_MIN) repeatLabel = label;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        final SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress(Math.round((value - min) / (float) step));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress * step;
                label.setText(labeler.label(v));
                if (fromUser) listener.onValue(v);
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        row.addView(stepButton("\u2212", bar, -1, min, step, listener),
                new LinearLayout.LayoutParams(dp(56), dp(48)));
        row.addView(bar, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(stepButton("+", bar, +1, min, step, listener),
                new LinearLayout.LayoutParams(dp(56), dp(48)));

        root.addView(row, matchWrap());
        return row;
    }

    private Button stepButton(String text, final SeekBar bar, final int delta, final int min,
                              final int step, final ValueListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20f);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int p = Math.max(0, Math.min(bar.getMax(), bar.getProgress() + delta));
                bar.setProgress(p);
                listener.onValue(min + p * step);
            }
        });
        return b;
    }

    private Switch toggle(String label, final String key, boolean def,
                          final CompoundButton.OnCheckedChangeListener extra) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(17f);
        s.setChecked(prefs.getBoolean(key, def));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                if (extra != null) extra.onCheckedChanged(b, checked);
            }
        });
        return s;
    }

    private TextView header(String s) {
        TextView t = text(s);
        t.setTextSize(19f);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(26), 0, dp(6));
        return t;
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17f);
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
