package com.boox.firefoxscroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class ButtonScrollService extends AccessibilityService {

    /** Largest single swipe, as a fraction of the window height. Bigger distances are split. */
    private static final float MAX_SWIPE_FRACTION = 0.70f;
    /** How long the virtual finger rests before lifting in precise mode (kills momentum). */
    private static final long HOLD_BEFORE_LIFT_MS = 150;
    private static final long DEBOUNCE_MS = 120;
    private static final long MIN_FIRST_REPEAT_DELAY_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentPackage = "";
    private long lastScrollAt = 0;

    /** Key currently held down that we consumed, or -1. */
    private int heldKeyCode = -1;
    private boolean heldScrollsDown = true;

    /** True while a swipe chain is running. */
    private boolean busy = false;
    /** Incremented for every new scroll so stale chains stop themselves. */
    private int generation = 0;

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (heldKeyCode == -1) return;
            if (!isTargetAppForeground()) {
                stopHold();
                return;
            }
            if (!busy) startScroll(heldScrollsDown);
            handler.postDelayed(this, Prefs.repeatMs(Prefs.get(ButtonScrollService.this)));
        }
    };

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
        stopHold();
    }

    @Override
    public void onDestroy() {
        stopHold();
        super.onDestroy();
    }

    private static boolean isScrollKey(int code) {
        return code == KeyEvent.KEYCODE_VOLUME_UP
                || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_PAGE_UP
                || code == KeyEvent.KEYCODE_PAGE_DOWN;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!isScrollKey(code)) return false;

        int action = event.getAction();

        if (action == KeyEvent.ACTION_UP) {
            // Only swallow the release of a press we swallowed.
            if (code == heldKeyCode) {
                stopHold();
                return true;
            }
            return false;
        }
        if (action != KeyEvent.ACTION_DOWN) return false;

        // System auto-repeat: our own timer handles holding, so just swallow these.
        if (event.getRepeatCount() > 0) return code == heldKeyCode;

        if (!isTargetAppForeground()) return false;

        SharedPreferences p = Prefs.get(this);
        p.edit().putString(Prefs.LAST_KEY,
                KeyEvent.keyCodeToString(code) + " in " + currentPackage).apply();

        boolean isUpKey = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_PAGE_UP;
        boolean scrollDown = p.getBoolean(Prefs.REVERSE, false) ? isUpKey : !isUpKey;

        handler.removeCallbacks(repeatRunnable);
        heldKeyCode = code;
        heldScrollsDown = scrollDown;

        long now = SystemClock.uptimeMillis();
        if (now - lastScrollAt >= DEBOUNCE_MS) {
            lastScrollAt = now;
            startScroll(scrollDown);
        }

        if (p.getBoolean(Prefs.HOLD, true)) {
            long firstDelay = Math.max(MIN_FIRST_REPEAT_DELAY_MS, Prefs.repeatMs(p));
            handler.postDelayed(repeatRunnable, firstDelay);
        }
        return true;
    }

    private void stopHold() {
        heldKeyCode = -1;
        handler.removeCallbacks(repeatRunnable);
    }

    private boolean isTargetAppForeground() {
        String pkg = currentPackage;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            CharSequence rootPkg = root.getPackageName();
            if (rootPkg != null) pkg = rootPkg.toString();
            root.recycle();
        }
        if (pkg == null || pkg.equals(getPackageName())) return false;
        return Prefs.apps(Prefs.get(this)).contains(pkg);
    }

    private Rect windowBounds() {
        Rect r = new Rect();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            root.getBoundsInScreen(r);
            root.recycle();
        }
        if (r.width() <= 0 || r.height() <= 0) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            r.set(0, 0, dm.widthPixels, dm.heightPixels);
        }
        return r;
    }

    /** Scrolls the configured distance, splitting it into several swipes if needed. */
    private void startScroll(boolean scrollDown) {
        SharedPreferences p = Prefs.get(this);
        Rect r = windowBounds();
        float total = r.height() * Prefs.scrollPercent(p) / 100f;
        float maxSwipe = r.height() * MAX_SWIPE_FRACTION;
        int swipes = Math.max(1, (int) Math.ceil(total / maxSwipe));
        float travel = total / swipes;
        boolean precise = p.getBoolean(Prefs.PRECISE, true);

        int gen = ++generation;
        busy = true;
        runSwipe(gen, r, scrollDown, travel, swipes, precise);
    }

    private void runSwipe(final int gen, final Rect r, final boolean scrollDown,
                          final float travel, final int remaining, final boolean precise) {
        if (gen != generation) return;
        if (remaining <= 0) {
            busy = false;
            return;
        }

        final float x = r.left + r.width() * 0.5f;
        float mid = r.top + r.height() * 0.5f;
        // To scroll content down, the finger moves up the screen.
        float startY = scrollDown ? mid + travel / 2f : mid - travel / 2f;
        final float endY = scrollDown ? mid - travel / 2f : mid + travel / 2f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        final Runnable next = new Runnable() {
            @Override
            public void run() {
                runSwipe(gen, r, scrollDown, travel, remaining - 1, precise);
            }
        };

        if (precise && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long moveMs = 150 + Math.round(250f * travel / r.height());
            final GestureDescription.StrokeDescription move =
                    new GestureDescription.StrokeDescription(path, 0, moveMs, true);
            dispatch(move, gen, new Runnable() {
                @Override
                public void run() {
                    // Rest the finger in place so the app sees zero velocity at lift.
                    Path rest = new Path();
                    rest.moveTo(x, endY);
                    rest.lineTo(x, endY + (scrollDown ? -1f : 1f));
                    GestureDescription.StrokeDescription stay =
                            move.continueStroke(rest, 0, HOLD_BEFORE_LIFT_MS, false);
                    dispatch(stay, gen, next);
                }
            });
        } else {
            // Original quick flick (has momentum).
            dispatch(new GestureDescription.StrokeDescription(path, 0, 90), gen, next);
        }
    }

    private void dispatch(GestureDescription.StrokeDescription stroke, final int gen,
                          final Runnable onDone) {
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription g) {
                onDone.run();
            }

            @Override
            public void onCancelled(GestureDescription g) {
                if (gen == generation) busy = false;
            }
        }, handler);
        if (!accepted && gen == generation) busy = false;
    }
}
