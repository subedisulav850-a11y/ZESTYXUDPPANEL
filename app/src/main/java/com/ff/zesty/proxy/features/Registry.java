package com.ff.zesty.proxy.features;

import com.ff.zesty.proxy.config.EspConfig;

import android.content.Context;

import com.ff.zesty.proxy.config.Prefs;
import com.ff.zesty.proxy.config.plugin.Feature;
import com.ff.zesty.proxy.config.plugin.Features;
import com.ff.zesty.proxy.config.plugin.Refs;
import com.ff.zesty.proxy.ui.render.EspRenderView;

/**
 * Every feature the app has, in one array.
 *
 * <p><b>This file is the whole plugin system's index.</b> You can read what the
 * app does in one screen, the compiler checks every entry, R8 cannot rename it
 * out from under you, and it costs nothing at startup — none of which is true
 * of a classpath scan, a service manifest or an annotation processor.</p>
 *
 * <p>This is also the only class that knows about both {@code config/} and the
 * things features drive ({@code ui/}, {@code esp/}, {@code filter/}). That
 * asymmetry is deliberate: the registry core in
 * {@code config/plugin} takes the array as an argument rather than naming
 * these types, so {@code config/} still depends on nothing and the
 * {@code esp/ -> ui/} edge the 2026-09-15 reorg removed stays removed.</p>
 *
 * <p><b>Order is declaration order and nothing else.</b> Features do not
 * declare dependencies on one another; if two ever need sequencing, that is a
 * design problem in those two, not a missing graph here. Soft dependency
 * resolution is where plugin systems reliably go wrong.</p>
 */
public final class Registry {

    private Registry() {}

    /** Builds the feature list and installs it. Call once, early. */
    public static void install(Context ctx) {
        Features.install(ctx, build());
    }

    private static Feature[] build() {
        // The default declared here must match every OTHER site that reads the
        // same key, because a Feature's default is not the only one: the same
        // keys are read by FloatingOverlayService.applyPrefs, by that class's
        // own field initialisers (which back the intent-extra fallbacks), and
        // by AppearanceBroadcaster.isFloatEnabled. Declaring a default here
        // that disagrees with those makes the settings switch show one thing
        // and the floating button do another.
        //
        // Ghost and Freeze are TRUE. Teleport is FALSE as of 2026-09-17 (owner's
        // call): it rewrites the position you report and the server enforces a
        // distance gate, so it is the one effect that should never be one stray
        // tap away on a fresh install. Changed in four places together —
        // here, showTele's initialiser, the KEY_TELE read in applyPrefs, and
        // isFloatEnabled's exclusion list.
        return new Feature[] {

                new EspFeature(),
                new RadarFeature(),
                new MountedCameraFeature(),
                // Input-side only: moves our own crosshair through the uinput
                // clone. Touches no packet, forges nothing.
                new AimFeature(),

                // The three packet effects. Same shape, different copy - see
                // FilterFeature for why they are not three classes.
                new FilterFeature("ghost", "Ghost",
                        "Withhold world state so you stop being streamed",
                        "Drops only world state. It used to withhold 88% of inbound "
                      + "including your own damage feed, which blinded you while it ran.",
                        Prefs.KEY_GHOST, true,
                        new Refs.BoolRef() {
                            public boolean get() { return sGhost; }
                            public void set(boolean v) { sGhost = v; }
                        }),

                new FilterFeature("freeze", "Freeze",
                        "Hold the world still",
                        null,
                        Prefs.KEY_FREEZE, true,
                        new Refs.BoolRef() {
                            public boolean get() { return sFreeze; }
                            public void set(boolean v) { sFreeze = v; }
                        }),

                new FilterFeature("teleport", "Teleport",
                        "Rewrite the position you report",
                        "The server enforces a 3-5 m distance gate and snaps you back "
                      + "past it. Never withholds the report - that drops the session.",
                        Prefs.KEY_TELE, false,
                        new Refs.BoolRef() {
                            public boolean get() { return sTele; }
                            public void set(boolean v) { sTele = v; }
                        }),

                new CaptureFeature(),
        };
    }

    // ---- availability flags -------------------------------------------------
    //
    // These say "the user wants this button available", NOT "the effect is
    // running". Arming happens on the floating button during a match: these
    // effects change what the server sees, and arming one from a settings
    // screen you are not looking at is how people get disconnected mid-fight.
    //
    // They live here rather than on the filters because a filter's own
    // isActive() already means something different and load-bearing.

    private static volatile boolean sGhost;
    private static volatile boolean sFreeze;
    private static volatile boolean sTele;

    public static boolean ghostAvailable() { return sGhost; }
    public static boolean freezeAvailable() { return sFreeze; }
    public static boolean teleAvailable() { return sTele; }

    /** Kept so a reader can see the renderer statics are still the real home. */
    public static boolean espOn() { return EspConfig.SHOW_BOXES; }
}
