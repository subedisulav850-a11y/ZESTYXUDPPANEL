package com.ff.zesty.proxy.ui.aim;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Privilege check — now accessibility-based, no Shizuku.
 *
 * <p>{@link #available()} returns true when {@link AimAccessibilityService}
 * is connected. The user enables it once in Settings → Accessibility → Zesty.</p>
 */
public final class Privilege {

    private static final String TAG = "FakelagAim";

    private Privilege() { }

    public static final int STATE_NOT_ENABLED = 0;
    public static final int STATE_READY       = 1;

    // No-op: was Shizuku listener registration. Nothing to do now.
    public static synchronized void init() { }

    /** True when the AccessibilityService is live. */
    public static boolean available() {
        return AimAccessibilityService.isAvailable();
    }

    public static int state(Context ctx) {
        return AimAccessibilityService.isAvailable() ? STATE_READY : STATE_NOT_ENABLED;
    }

    public static String stateLabel(Context ctx) {
        return AimAccessibilityService.isAvailable()
                ? "AIM: Accessibility READY ✓"
                : "AIM: enable Accessibility service";
    }

    public static String statusLine() {
        return AimAccessibilityService.statusLine();
    }

    public static boolean isInstalled(Context ctx) {
        return true; // built-in service, always installed
    }

    /** No-op — was Shizuku permission request. */
    public static void requestPermission() { }

    /** No-op. */
    public static void refresh() { }
}
