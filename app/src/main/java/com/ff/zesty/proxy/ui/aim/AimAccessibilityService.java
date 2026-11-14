package com.ff.zesty.proxy.ui.aim;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility-based touch injector for the aim correction loop.
 *
 * <p>Replaces the Shizuku + nvmux path entirely. No root, no Shizuku —
 * just the standard {@link AccessibilityService#dispatchGesture} API
 * (available from API 24).</p>
 *
 * <p>The service must be enabled by the user in Settings → Accessibility.
 * {@link Privilege#available()} returns false and the aim loop bails with
 * "no accessibility" until the user does so.</p>
 *
 * <h3>How drag correction works without EVIOCGRAB</h3>
 *
 * <p>We cannot steal the real finger (that required the nvmux uinput clone).
 * Instead we inject a short synthetic swipe from the aim swipe-origin that
 * matches the correction delta. Because Free Fire's look region responds to
 * ANY new finger drag — not specifically the user's first contact — the
 * synthetic contact turns the camera even while the user has a finger down
 * elsewhere, as long as the inject point is in the look zone (not on a UI
 * button). The user's existing drag and our correction compound: He drags
 * roughly toward the enemy; we snap the last few degrees to the head.</p>
 *
 * <h3>Gesture duration</h3>
 *
 * <p>Too short (< 16 ms): the gesture finishes before the game samples it.
 * Too long (> 80 ms): the correction lags a moving target. 32 ms is two
 * game frames at 60 Hz and gives reliable registration.</p>
 */
public final class AimAccessibilityService extends AccessibilityService {

    private static final String TAG = "AimAccess";

    // Static instance: Daemon calls dispatchGesture through this.
    // Volatile: written on the main thread (onServiceConnected), read on
    // the aim worker thread (Daemon.steerBy / swipe).
    private static volatile AimAccessibilityService sInstance;

    /** True when the service is connected and ready to dispatch gestures. */
    public static boolean isAvailable() {
        return sInstance != null;
    }

    /** The service instance for status reporting. */
    public static AimAccessibilityService instance() {
        return sInstance;
    }

    @Override
    public void onServiceConnected() {
        sInstance = this;
        Log.i(TAG, "AimAccessibilityService connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        sInstance = null;
        Log.i(TAG, "AimAccessibilityService disconnected");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We do not consume accessibility events — this service is purely
        // for gesture dispatch. Returning without action is correct.
    }

    @Override
    public void onInterrupt() { }

    // ====================================================================
    // Gesture dispatch (called from Daemon on the aim worker thread)
    // ====================================================================

    /**
     * Inject a correction swipe: plant at (sx, sy), drag by (dx, dy).
     *
     * @param sx  swipe origin X, screen px
     * @param sy  swipe origin Y, screen px
     * @param dx  horizontal delta, px (right = positive)
     * @param dy  vertical delta, px (down = positive)
     * @param durationMs  gesture duration, ms (use 32 for aim corrections)
     * @return true if dispatchGesture accepted the gesture
     */
    public boolean injectSwipe(int sx, int sy, int dx, int dy, int durationMs) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        try {
            Path path = new Path();
            path.moveTo(sx, sy);
            path.lineTo(sx + dx, sy + dy);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            Log.w(TAG, "injectSwipe failed: " + t);
            return false;
        }
    }

    /**
     * Status line for diagnostics / HUD.
     */
    public static String statusLine() {
        return sInstance != null ? "Accessibility: READY" : "Accessibility: NOT ENABLED";
    }
}
