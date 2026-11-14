package com.ff.zesty.proxy.ui.panel;

import com.ff.zesty.proxy.config.EspConfig;

import android.content.Context;
import android.view.WindowManager;

import com.ff.zesty.proxy.config.EspTuning;
import com.ff.zesty.proxy.ui.aim.AimConfig;
import com.ff.zesty.proxy.ui.aim.AimController;
import com.ff.zesty.proxy.ui.aim.Daemon;
import com.ff.zesty.proxy.ui.aim.AimTarget;
import com.ff.zesty.proxy.ui.edit.Layout;
import com.ff.zesty.proxy.ui.render.EspRenderView;

/**
 * Aim setup — acquisition rules, swipe placement, calibration and live state.
 *
 * <h3>What the aim is, precisely</h3>
 *
 * <p>It moves the player's own crosshair by planting a synthetic finger through
 * the {@code nvmux} uinput clone and dragging it. <b>It touches no packet.</b>
 * It does not forge hits, does not claim damage, and the deleted Cmd-106
 * {@code AimFilter} stays deleted. Everything below is geometry and touch.</p>
 *
 * <p>Additionally, {@link com.ff.zesty.proxy.filter.AimRotationFilter} nudges the
 * reported camera direction on the wire toward the acquired target — this is the
 * wire-level assist, separate from the touch-injection path above.</p>
 *
 * <h3>The five-tap</h3>
 *
 * <p>Tapping the header five times opens the full-screen layout editor, which is
 * where the swipe paths and the fire button are actually placed. The same editor
 * is reachable from the EDIT LAYOUT row.</p>
 */
public final class AimSetupPanel extends SetupPanel {

    /** Opens the layout editor. Injected so this panel owns no window. */
    public interface EditorHook {
        void openEditor();
    }

    private final EditorHook editor;

    public AimSetupPanel(Context context, WindowManager wm, EditorHook editor) {
        super(context, wm);
        this.editor = editor;
    }

    @Override protected String title()      { return "◎ AIM LEGIT"; }
    @Override protected String shortTitle() { return "◎ AIM"; }
    @Override protected String openSectionKey() { return "aim_setup_open_section"; }
    @Override protected int startXDp() { return 12; }
    @Override protected int startYDp() { return 96; }
    @Override protected int bodyHeightDp() { return 480; }

    @Override
    protected void onSecretTap() {
        if (editor != null) editor.openEditor();
    }

    @Override
    protected void buildRows() {

        // ================================================================
        // MASTER SWITCHES — read first, change here if aim does nothing
        // ================================================================
        section("MASTER");
        note("Both switches must be ON for any aim correction to act. "
                + "AIM ENABLED gates the touch-injection path. "
                + "WIRE ROTATION gates the packet-level camera nudge.");

        bool("AIM ENABLED",
                "Master on/off for the entire touch-injection aim. OFF = no "
                + "synthetic drag is ever sent, nothing steers the finger. "
                + "Toggle this first when diagnosing 'aim does nothing'.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.AIM_ENABLED; }
                    public void set(boolean b) {
                        AimConfig.AIM_ENABLED = b;
                        refreshAll();
                    }
                });

        bool("WIRE ROTATION",
                "Wire-level camera nudge: intercepts each outbound Cmd 2001 "
                + "PositionSync and rotates the reported view direction toward "
                + "the target by up to MAX STEP degrees per tick. Complementary "
                + "to touch injection — both can run simultaneously. Turn OFF "
                + "to isolate touch-only behaviour.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.AIM_ROTATION_ENABLED; }
                    public void set(boolean b) { EspTuning.setAimRotationEnabled(b); }
                });

        num("WIRE MAX STEP",
                "Maximum camera correction per Cmd 2001 tick, degrees. "
                + "4° is the default: above ~7° you overshoot a sprinter at 5 m "
                + "and the server may reject the delta as implausible. Gain "
                + "margin breaks above ~24°/tick. Lower to enter the game's "
                + "own aim-assist cone gradually rather than snapping.",
                AimConfig.AIM_ROTATION_STEP_MIN,
                (AimConfig.AIM_ROTATION_STEP_MAX - AimConfig.AIM_ROTATION_STEP_MIN) / 20f,
                "%.1f°",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_ROTATION_MAX_STEP_DEG; }
                    public void set(float v) { EspTuning.setAimRotationMaxStep(v); }
                });

        // ================================================================
        // BODY PART SELECTOR
        // ================================================================
        section("TARGET PART");
        note("Which body zone the aim pulls the crosshair toward. "
                + "Selection sets anchor + box fracs automatically. "
                + "HEAD = skull lock, NECK = safer at range, CHEST = wide zone.");

        bool("HEAD",
                "Skull crown (anchor 0.95 / box 0.96). Highest damage, smallest "
                + "zone. Best inside 80 m on stationary targets.",
                new BoolKnob() {
                    public boolean get() {
                        return AimConfig.AIM_TARGET_PART == AimConfig.PART_HEAD;
                    }
                    public void set(boolean b) {
                        if (b) { EspTuning.setAimPart(AimConfig.PART_HEAD); refreshAll(); }
                    }
                });

        bool("NECK",
                "Neck base (anchor 0.82 / box 0.88). Good for 50-120 m or "
                + "moving targets — wider zone, still upper-body damage.",
                new BoolKnob() {
                    public boolean get() {
                        return AimConfig.AIM_TARGET_PART == AimConfig.PART_NECK;
                    }
                    public void set(boolean b) {
                        if (b) { EspTuning.setAimPart(AimConfig.PART_NECK); refreshAll(); }
                    }
                });

        bool("CHEST",
                "Upper chest (anchor 0.55 / box 0.55). Largest zone — use at "
                + ">120 m or against sprinters where precision is lost.",
                new BoolKnob() {
                    public boolean get() {
                        return AimConfig.AIM_TARGET_PART == AimConfig.PART_CHEST;
                    }
                    public void set(boolean b) {
                        if (b) { EspTuning.setAimPart(AimConfig.PART_CHEST); refreshAll(); }
                    }
                });

        info("ZONE NOW", new TextProvider() {
            public String text() {
                return String.format("Part: %s   anchor=%.2f   box=%.2f",
                        AimConfig.partLabel(AimConfig.AIM_TARGET_PART),
                        AimConfig.AIM_ANCHOR_FRAC,
                        EspConfig.AIM_BOX_FRAC);
            }
        });

        // ================================================================
        // STATE — live diagnostics; read before changing anything
        // ================================================================
        section("STATE");
        note("Read this before changing anything. Most 'the aim is broken' is "
                + "a gate refusing to acquire, not a bad number.");

        info("TARGET", new TextProvider() {
            public String text() { return AimTarget.dumpLine(); }
        });
        info("INJECT", new TextProvider() {
            public String text() { return AimController.aimLine(); }
        });
        info("DAEMON", new TextProvider() {
            public String text() { return Daemon.statusLine(); }
        });
        info("LAYOUT", new TextProvider() {
            public String text() { return Layout.isPlaced() ? "placed" : "defaults (not placed)"; }
        });
        info("SCOPE", new TextProvider() {
            public String text() { return com.ff.zesty.proxy.protocol.ScopeState.debugLine(); }
        });

        // ================================================================
        // LAYOUT EDITOR
        // ================================================================
        section("LAYOUT");
        note("Where the synthetic finger plants and drags, and where your fire "
                + "button is. Placed on screen, not typed in.");
        action("EDIT LAYOUT", "OPEN", new Runnable() {
            public void run() { if (editor != null) editor.openEditor(); }
        });
        note("Shortcut: tap this panel's header five times.");
        bool("SHOW ELEMENTS",
                "Draw the swipe paths and fire button during play. Off by default.",
                new BoolKnob() {
                    public boolean get() { return Layout.visible; }
                    public void set(boolean b) { Layout.visible = b; }
                });
        action("RESET LAYOUT", "RESET", new Runnable() {
            public void run() { Layout.reset(context); refreshAll(); }
        });

        // ================================================================
        // ACQUISITION — who the aim is allowed to pick
        // ================================================================
        section("ACQUISITION");
        note("Only a server-published team byte marks an enemy. An unclassified "
                + "entity is NEVER a target.");

        bool("SCOPE ONLY",
                "Acquire only while scoped. ON is the intended mode: scoped, the "
                + "reticle is a point and the correction is the whole shot. Turn OFF "
                + "only to test without a scope — the SCOPE row above shows whether "
                + "the latch is tracking.",
                new BoolKnob() {
                    public boolean get() { return EspConfig.AIM_SCOPE_ONLY; }
                    public void set(boolean v) { EspTuning.setAimScopeOnly(v); }
                });

        num("CONE",
                "Half-angle around the crosshair inside which a target is eligible. "
                + "Wider acquires sooner and swipes further; narrower only helps shots "
                + "you were nearly taking.",
                0.5f, 5f, "%.1f°",
                new FloatKnob() {
                    public float get() { return EspConfig.AIM_CONE_DEG; }
                    public void set(float v) { EspTuning.setAimCone(v); }
                });

        num("RANGE",
                "Beyond this distance, ignore the target. Distant targets need a "
                + "tiny swipe that the game's own aim friction eats.",
                5f, 25f, "%.0fm",
                new FloatKnob() {
                    public float get() { return EspConfig.AIM_MAX_RANGE_M; }
                    public void set(float v) { EspTuning.setAimRange(v); }
                });

        num("STILL FOR",
                "The thesis in one number. Dead reckoning's RMS error is "
                + "0.57/0.62/0.68 m over one/two/three ticks and a swipe takes "
                + "100-200 ms, so a moving target outruns its correction. "
                + "Raising this makes the aim pickier and MORE accurate, not weaker.",
                50f, 200f, "%.0fms",
                new FloatKnob() {
                    public float get() { return EspConfig.AIM_STILL_MS; }
                    public void set(float v) { EspTuning.setAimStillMs(v); }
                });

        bool("TEST TRACK",
                "Acquire ANY enemy in the cone. Skips team-known, still-time, "
                + "record age and posture. ON while the aim is being brought up — "
                + "you cannot debug a steering problem through an acquisition problem. "
                + "Turn OFF once the swipe is confirmed to land.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.AIM_TEST_TRACK; }
                    public void set(boolean b) { EspTuning.setAimTestTrack(b); }
                });

        bool("POSTURE GATE",
                "Refuse targets by posture (knocked / falling / swimming / creeping). "
                + "OFF — the posture enum is UNVERIFIED and a live capture contradicted "
                + "it: value 11 was 15% of all samples, players are not airborne 15% "
                + "of the time. Turn ON only after confirming the mapping.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.AIM_POSTURE_GATE; }
                    public void set(boolean b) { EspTuning.setAimPostureGate(b); }
                });

        // ================================================================
        // TARGET VOLUME — the modelled capsule
        // ================================================================
        section("TARGET VOLUME");
        note("There is NO per-bone data on the wire — an exhaustive 4.96M sweep "
                + "found body offsets 24× BELOW chance. The hittable body is MODELLED "
                + "from the entity root plus the posture byte, so these three numbers "
                + "ARE the target.");

        num("BODY HEIGHT",
                "Standing capsule height. 1.75, not 1.8 — two independent residuals "
                + "from the scoped-zoom work agree 1.8 is too big. The real value is "
                + "serialized Unity data scaled per match by the server.",
                0.01f, 0.05f, "%.2fm",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_H_STANDING_M; }
                    public void set(float v) { EspTuning.setAimBodyH(v); }
                });

        num("CROUCH RATIO",
                "Crouched height as a fraction of standing. 0.60 is an ASSUMPTION "
                + "taken from the ADS pivot table (0.77/1.28) — no collider table "
                + "exists in the binary. The least-supported number in the aim path. "
                + "Calibrate against a crouched target.",
                0.01f, 0.05f, "%.2f",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_CROUCH_RATIO; }
                    public void set(float v) { EspTuning.setAimCrouchRatio(v); }
                });

        num("ANCHOR",
                "Where in the capsule to aim, as a fraction of height. "
                + "0.95 = head top, 0.82 = neck base, 0.55 = centre of mass. "
                + "Overridden by the TARGET PART selector above.",
                0.01f, 0.05f, "%.2f",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_ANCHOR_FRAC; }
                    public void set(float v) { EspTuning.setAimAnchorFrac(v); }
                });

        num("AIM POINT",
                "Where on the ESP box the aim points. 0 = feet, 1 = box top, "
                + "0.55 = upper chest. The aim projects the SAME box the outline "
                + "uses, so if they still disagree the box is miscalibrated.",
                0.05f, 0.1f, "%.2f",
                new FloatKnob() {
                    public float get() { return EspConfig.AIM_BOX_FRAC; }
                    public void set(float v) { EspTuning.setAimBoxFrac(v); }
                });

        // ================================================================
        // LEAD / TIMING
        // ================================================================
        section("LEAD");
        note("Bullets are HITSCAN and the server rewinds to our own tick "
                + "(Cmd 106 hit claim carries our sim timer and our ping), "
                + "so every transport term cancels — the lead is client-side only.");

        num("PIPELINE",
                "How far ahead the aim point is carried: swipe (~160) + pipe (~8) "
                + "+ game applying rotation (~17) ms. Too high: shots land in front "
                + "and error grows with speed. Too low: shots land behind.",
                5f, 25f, "%.0fms",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_PIPELINE_MS; }
                    public void set(float v) { EspTuning.setAimPipelineMs(v); }
                });

        num("MAX RECORD AGE",
                "Refuse a target whose freshest packet is older than this. "
                + "~37% of wall-clock time an entity has no fresh record at all "
                + "and the p99 dropout is 1782 ms — a player travels a median "
                + "7.2 m in that window. Predicting through it is invention.",
                10f, 50f, "%.0fms",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_MAX_SRC_AGE_MS; }
                    public void set(float v) { EspTuning.setAimMaxSrcAge(v); }
                });

        num("BASIS STALE",
                "How long our own Cmd 2001 keepalive may be silent before the aim "
                + "refuses everything. The camera basis comes from our own keepalive; "
                + "a frozen basis makes every bearing wrong. 400 ms (the vehicle test) "
                + "is only two missed ticks and refused 1.1×/sec on foot.",
                50f, 200f, "%.0fms",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_BASIS_STALE_MS; }
                    public void set(float v) { EspTuning.setAimBasisStale(v); }
                });

        // ================================================================
        // CALIBRATION — the two unknowns
        // ================================================================
        section("CALIBRATION");
        note("The one measurement still missing. 10.0 is the binary's value at "
                + "GAME SENS 75, but Free Fire moved to a 0-200 scale with six "
                + "per-scope sliders, so the compiled constant is a fallback.");

        num("PX / DEG",
                "Pixels of swipe per degree of view rotation. "
                + "Watch one swipe land. OVERSHOOTS → lower. UNDERSHOOTS → raise. "
                + "That is the whole calibration procedure.",
                0.1f, 1f, "%.1f",
                new FloatKnob() {
                    public float get() { return AimConfig.PX_PER_DEG; }
                    public void set(float v) { EspTuning.setAimPxPerDeg(v); }
                });

        num("MAX TRAVEL",
                "Longest single drag before the finger lifts and re-plants. "
                + "The game's look ACCELERATES with drag distance (~3.4× at 300 px), "
                + "so one long drag overshoots. Lower before touching PX / DEG.",
                5f, 25f, "%.0fpx",
                new FloatKnob() {
                    public float get() { return AimConfig.MAX_TRAVEL_PX; }
                    public void set(float v) { EspTuning.setAimMaxTravel(v); }
                });

        num("SMOOTHNESS",
                "Chase time constant, ms — how hard the steer pulls toward the aim "
                + "point. The contact is always moving; this sets only how fast. "
                + "Low = snappy and eventually robotic. High = smooth but lags. "
                + "Keep it under ~120 ms (the aim's re-solve period).",
                5f, 20f, "%.0fms",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_STEER_TAU_MS; }
                    public void set(float v) { EspTuning.setAimSteerTau(v); }
                });

        num("SCOPED GAIN",
                "Extra px-per-degree multiplier while scoped. PX / DEG is the "
                + "hip-fire figure; scoped, the game damps the look ~0.2×, so the "
                + "same drag turns a fifth as far. Too low: the aim crawls. "
                + "Too high: it overshoots and oscillates.",
                0.25f, 1f, "%.2fx",
                new FloatKnob() {
                    public float get() { return AimConfig.AIM_SCOPED_GAIN; }
                    public void set(float v) { EspTuning.setAimScopedGain(v); }
                });

        // ================================================================
        // SWIPE ORIGIN
        // ================================================================
        section("SWIPE ORIGIN");
        bool("PLANT ON FIRE",
                "Plant the synthetic finger on the placed fire button. Scoped, that "
                + "is the ONLY place a drag turns the view — a swipe starting anywhere "
                + "else runs perfectly and moves nothing, which looks exactly like a "
                + "bad PX / DEG.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.PLANT_ON_FIRE; }
                    public void set(boolean v) { EspTuning.setAimPlantOnFire(v); }
                });
        note("The two placed lines set the DIRECTION of each axis, not the travel "
                + "limit. A swipe may run from the plant point to the screen edge.");

        section("SWIPE ORIGIN (numeric fallback)");
        note("Used only while the layout above is unplaced. Fractions of screen "
                + "width and height. The editor replaces both with two placed paths.");
        num("SWIPE X", 0.01f, 0.05f, "%.2f", new FloatKnob() {
            public float get() { return AimConfig.SWIPE_X_FRAC; }
            public void set(float v) { EspTuning.setAimSwipeX(v); }
        });
        num("SWIPE Y", 0.01f, 0.05f, "%.2f", new FloatKnob() {
            public float get() { return AimConfig.SWIPE_Y_FRAC; }
            public void set(float v) { EspTuning.setAimSwipeY(v); }
        });

        // ================================================================
        // TEST MODE — prove the swipe lands at all
        // ================================================================
        section("TEST MODE");
        note("Hold your real finger on the fire button. The daemon TAKES OVER "
                + "that finger and moves it — your own contact is what travels, "
                + "so the game steers exactly as if you had dragged.");
        note("A second synthetic finger beside yours does NOT work scoped: the "
                + "scoped look is driven by the contact already down, so an extra "
                + "one is ignored.");
        info("TEST", new TextProvider() {
            public String text() { return AimController.steerLine(); }
        });
        info("GEOM", new TextProvider() {
            public String text() { return AimController.geomLine(); }
        });
        info("LAST PRESS", new TextProvider() {
            public String text() { return Daemon.lastDown(); }
        });
        info("TRACE", new TextProvider() {
            public String text() { return Daemon.lastLine(); }
        });
        action("SPIN NOW (3s)", "GO", new Runnable() {
            public void run() { AimController.steerNow(3000); refreshAll(); }
        });
        note("Hold the fire button then press GO. Refuses if no finger is on the "
                + "glass — there is nothing to steer.");
        num("SPIN FOR",
                "How long the automatic scope-in steer runs. Stops early if you "
                + "unscope or lift the finger.",
                250f, 1000f, "%.0fms",
                new FloatKnob() {
                    public float get() { return AimConfig.TEST_HOLD_MS; }
                    public void set(float v) { EspTuning.setAimSpinMs(v); }
                });
        bool("HOLD-FIRE STEER",
                "Arms the daemon to watch the placed fire circle. ON by default. "
                + "Scoping in now steers the finger nearest your fire button "
                + "automatically — no button to press. Turn OFF to play normally.",
                new BoolKnob() {
                    public boolean get() { return AimConfig.TEST_MODE; }
                    public void set(boolean v) { EspTuning.setAimTestMode(v); refreshAll(); }
                });

        // ================================================================
        // DEBUG OVERLAY
        // ================================================================
        section("DEBUG OVERLAY");
        bool("AIM OVERLAY",
                "While SCOPED, draws the acquisition cone and a dot at the scope's "
                + "centre. The cone uses the SAME tangents the projection does, so "
                + "what you see is what acquires. If the dot misses the scope's "
                + "own centre, the projection is off.",
                new BoolKnob() {
                    public boolean get() { return EspConfig.SHOW_AIM_EDITOR; }
                    public void set(boolean v) { EspTuning.setAimEditor(v); }
                });
    }
}
