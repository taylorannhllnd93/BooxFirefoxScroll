package com.boox.firefoxscroll;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Setting keys, defaults and limits shared by the settings screen and the service. */
final class Prefs {
    static final String FILE = "prefs";

    static final String SCROLL_PERCENT = "scroll_percent";
    static final int SCROLL_MIN = 10;
    static final int SCROLL_MAX = 200;
    static final int SCROLL_STEP = 5;
    static final int SCROLL_DEFAULT = 80;

    static final String REVERSE = "reverse";
    static final String PRECISE = "precise";

    static final String HOLD = "hold_repeat";
    static final String REPEAT_MS = "repeat_ms";
    static final int REPEAT_MIN = 200;
    static final int REPEAT_MAX = 2000;
    static final int REPEAT_STEP = 100;
    static final int REPEAT_DEFAULT = 700;

    static final String APPS = "enabled_apps";
    static final String LAST_KEY = "last_key";

    static final Set<String> DEFAULT_APPS = new HashSet<>(Arrays.asList(
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "org.mozilla.klar"));

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static int scrollPercent(SharedPreferences p) {
        return clamp(p.getInt(SCROLL_PERCENT, SCROLL_DEFAULT), SCROLL_MIN, SCROLL_MAX);
    }

    static int repeatMs(SharedPreferences p) {
        return clamp(p.getInt(REPEAT_MS, REPEAT_DEFAULT), REPEAT_MIN, REPEAT_MAX);
    }

    /** Returns a modifiable copy of the enabled app set. */
    static Set<String> apps(SharedPreferences p) {
        return new HashSet<>(p.getStringSet(APPS, DEFAULT_APPS));
    }
}
