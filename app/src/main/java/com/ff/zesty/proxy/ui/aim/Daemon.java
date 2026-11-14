package com.ff.zesty.proxy.ui.aim;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ff.zesty.proxy.diag.Diag;
import com.ff.zesty.proxy.diag.Slot;

/**
 * Touch injector — accessibility-based, no Shizuku, no native daemon.
 *
 * <p>Replaces the nvmux/Shizuku path. All gesture injection goes through
 * {@link AimAccessibilityService#injectSwipe}, which calls Android's own
 * {@code AccessibilityService.dispatchGesture}. No root, no extra install.</p>
 *
 * <p>Protocol difference from the nvmux version: we have no EVIOCGRAB, so
 * we cannot steer the user's real contact. Instead every correction is a
 * fresh short swipe injected on top of the user's drag. Free Fire's look
 * zone accepts any new finger drag, so the correction turns the camera even
 * while the user's own thumb is down — as long as the inject point lands in
 * the look area and not on a UI button.</p>
 *
 * <p>The public surface is intentionally identical to the old Daemon so
 * {@link AimController} needs zero changes.</p>
 */
public final class Daemon {

    private static final String TAG = "FakelagAim";

    private Daemon() { }

    // Keep-alive is a no-op without the LEASE, but we keep the cadence so
    // AimController's heartbeat logic compiles unchanged.
    static final long LEASE_MS = 1500L;
    private static final long KEEPALIVE_MS = 400L;

    private static Handler worker;

    private static volatile boolean keepAliveOn = false;
    private static Runnable keepAlive;

    // diagnostics
    private static volatile long sentCount = 0;
    private static volatile String lastError = null;
    private static volatile String lastBail = "-";
    private static volatile String lastLine = "-";
    private static volatile String lastDown = "-";

    // Screen dims — stored locally since we have no native process to tell.
    private static volatile int screenW = 1080, screenH = 2340;
    private static volatile int maxTravelPx = 160;

    // Swipe duration for aim corrections, ms.
    private static final int SWIPE_MS = 32;

    interface Listener {
        void onLine(String line);
    }

    private static volatile Listener listener;

    static void setListener(Listener l) {
        listener = l;
    }

    // ====================================================================
    // Thread
    // ====================================================================

    public static synchronized void post(Runnable r) {
        if (worker == null) {
            HandlerThread t = new HandlerThread("aim-inject");
            t.start();
            worker = new Handler(t.getLooper());
        }
        worker.post(r);
    }

    public static void postDelayed(Runnable r, long ms) {
        handler().postDelayed(r, ms);
    }

    public static void cancel(Runnable r) {
        if (worker != null && r != null) worker.removeCallbacks(r);
    }

    private static synchronized Handler handler() {
        if (worker == null) post(NO_OP);
        return worker;
    }

    private static final Runnable NO_OP = new Runnable() {
        @Override public void run() { }
    };

    public static void setLibDir(String dir) {
        // no-op: no native binary
    }

    // ====================================================================
    // "Process" lifecycle — now just a readiness check
    // ====================================================================

    public static boolean isUp() {
        return AimAccessibilityService.isAvailable();
    }

    static synchronized boolean ensure() {
        if (!AimAccessibilityService.isAvailable()) {
            lastBail = "accessibility not enabled";
            lastError = "AimAccessibilityService not connected";
            Log.w(TAG, "aim bailed: accessibility service not enabled");
            Diag.hit(Slot.AIM_NO_SHIZUKU); // reuse slot for "no injector"
            return false;
        }
        return true;
    }

    public static synchronized void close() {
        stopKeepAlive();
        // No process to kill.
    }

    // ====================================================================
    // Protocol — translated to dispatchGesture calls
    // ====================================================================

    /** Store screen dims for use in steerBy / steerNow. */
    static boolean dims(int w, int h, int maxTravel) {
        if (w > 0) screenW = w;
        if (h > 0) screenH = h;
        if (maxTravel > 0) maxTravelPx = maxTravel;
        return true;
    }

    /** Watch a fire circle — no-op without native daemon. */
    static boolean watchFire(int cx, int cy, int r, int holdMs) {
        return true;
    }

    /**
     * Steer: inject a swipe from (cx, cy) in the direction of the aim error.
     * Used for scope-in one-shot correction (AimController.steerNow).
     * Without the user's real contact to redirect, we inject a fresh synthetic
     * swipe from the look-zone origin.
     */
    static boolean steerNow(int cx, int cy, int ms) {
        // steerNow is for scope-in bulk correction; with accessibility we just
        // report ready — the hip loop's steerBy handles the actual work.
        sentCount++;
        lastLine = "steerNow @" + cx + "," + cy;
        return true;
    }

    /**
     * Core aim correction: inject a swipe of (dx, dy) px starting at (cx, cy).
     *
     * <p>This is called every 8 ms from the hip-fire loop. Each call is a
     * complete gesture: plant at (cx, cy), drag to (cx+dx, cy+dy), lift.
     * Free Fire's look zone treats this as a camera drag. Because we do not
     * hold EVIOCGRAB, the user's own finger is a separate contact; the game
     * blends both and the camera moves by our delta on top of the user's drag,
     * which is exactly the "thoda drag karo → head pe snap" behaviour.</p>
     */
    static boolean steerBy(int dx, int dy, int cx, int cy) {
        AimAccessibilityService svc = AimAccessibilityService.instance();
        if (svc == null) {
            lastBail = "no accessibility";
            return false;
        }
        // Clamp travel
        float mag = (float) Math.sqrt((double)(dx * dx + dy * dy));
        if (mag > maxTravelPx && mag > 0f) {
            float scale = maxTravelPx / mag;
            dx = Math.round(dx * scale);
            dy = Math.round(dy * scale);
        }
        if (dx == 0 && dy == 0) return true; // nothing to do

        boolean ok = svc.injectSwipe(cx, cy, dx, dy, SWIPE_MS);
        if (ok) {
            sentCount++;
            lastBail = null;
            lastLine = "G " + dx + "," + dy + " @" + cx + "," + cy;
            Diag.hit(Slot.AIM_INJECTED);
        } else {
            lastBail = "dispatchGesture refused";
            lastError = "gesture rejected";
            Diag.hit(Slot.AIM_INJECT_THREW);
        }
        return ok;
    }

    /** One-shot swipe — same impl as steerBy but explicit origin. */
    static boolean swipe(int sx, int sy, int dx, int dy) {
        return steerBy(dx, dy, sx, sy);
    }

    /** Release — no-op without grab. */
    static boolean release() {
        return true;
    }

    // ====================================================================
    // Lease / keep-alive — kept for API compatibility; heartbeat is a no-op
    // ====================================================================

    static synchronized void startKeepAlive() {
        if (keepAliveOn) return;
        keepAliveOn = true;
        keepAlive = new Runnable() {
            @Override
            public void run() {
                if (!keepAliveOn) return;
                // No pipe to ping — just touch accessibility liveness.
                if (!AimAccessibilityService.isAvailable()) {
                    lastBail = "accessibility lost";
                }
                handler().postDelayed(this, KEEPALIVE_MS);
            }
        };
        handler().post(keepAlive);
    }

    static synchronized void stopKeepAlive() {
        keepAliveOn = false;
        if (worker != null && keepAlive != null) worker.removeCallbacks(keepAlive);
        keepAlive = null;
    }

    // ====================================================================
    // Diagnostics
    // ====================================================================

    public static String statusLine() {
        StringBuilder sb = new StringBuilder(64);
        sb.append(AimAccessibilityService.statusLine());
        sb.append(keepAliveOn ? " held" : " free");
        sb.append(" tx=").append(sentCount);
        if (lastError != null) sb.append(" err=").append(lastError);
        else if (!"-".equals(lastBail) && lastBail != null) sb.append(" bail=").append(lastBail);
        return sb.toString();
    }

    public static String lastLine() { return lastLine; }
    public static String lastDown() { return lastDown; }
}
