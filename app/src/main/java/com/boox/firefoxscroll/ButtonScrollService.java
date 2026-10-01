package com.boox.firefoxscroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ButtonScrollService extends AccessibilityService {

    private static final Set<String> FIREFOX_PACKAGES = new HashSet<>(Arrays.asList(
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "org.mozilla.klar"
    ));

    private String currentPackage = "";
    private long lastScrollAt = 0L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            currentPackage = event.getPackageName().toString();
        }
    }

    @Override
    public void onInterrupt() {
        // No spoken or continuous feedback to interrupt.
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();
        final boolean supported = code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_PAGE_UP
                || code == KeyEvent.KEYCODE_PAGE_DOWN;

        if (!supported || !isFirefoxForeground()) {
            return false;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }

        if (event.getRepeatCount() > 0) {
            return true;
        }

        getSharedPreferences("prefs", MODE_PRIVATE)
                .edit()
                .putString("last_key", KeyEvent.keyCodeToString(code))
                .apply();

        boolean reverse = getSharedPreferences("prefs", MODE_PRIVATE)
                .getBoolean("reverse", false);

        boolean normallyUp = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_PAGE_UP;
        boolean scrollDown = reverse ? normallyUp : !normallyUp;

        long now = SystemClock.uptimeMillis();
        if (now - lastScrollAt < 120) {
            return true;
        }
        lastScrollAt = now;

        scrollFirefox(scrollDown);
        return true;
    }

    private boolean isFirefoxForeground() {
        String pkg = currentPackage;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            CharSequence p = root.getPackageName();
            if (p != null) pkg = p.toString();
            root.recycle();
        }
        return FIREFOX_PACKAGES.contains(pkg);
    }

    private void scrollFirefox(boolean down) {
        int percent = getSharedPreferences("prefs", MODE_PRIVATE)
                .getInt("scroll_percent", 78);
        percent = Math.max(45, Math.min(90, percent));

        AccessibilityNodeInfo root = getRootInActiveWindow();
        Rect bounds = new Rect();
        if (root != null) {
            root.getBoundsInScreen(bounds);
            root.recycle();
        }

        int width = bounds.width() > 0 ? bounds.width() : getResources().getDisplayMetrics().widthPixels;
        int height = bounds.height() > 0 ? bounds.height() : getResources().getDisplayMetrics().heightPixels;
        int left = bounds.width() > 0 ? bounds.left : 0;
        int top = bounds.height() > 0 ? bounds.top : 0;

        float x = left + width * 0.50f;
        float halfTravel = height * (percent / 100f) / 2f;
        float centerY = top + height * 0.52f;
        float upper = Math.max(top + height * 0.08f, centerY - halfTravel);
        float lower = Math.min(top + height * 0.92f, centerY + halfTravel);

        float startY = down ? lower : upper;
        float endY = down ? upper : lower;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 90);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }
}
