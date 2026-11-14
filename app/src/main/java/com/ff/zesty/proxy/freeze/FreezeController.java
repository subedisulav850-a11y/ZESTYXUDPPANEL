package com.ff.zesty.proxy.freeze;

import android.os.SystemClock;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Aim Lag / Auto Freeze / Scope Freeze controller.
 *
 * Ported from UDP_CODES leak (samitoteam / @jossmodz).
 *
 * Logic:
 *   aimLagEnabled   — master gate; nothing acts without it
 *   autoFreezeEnabled — freeze position packets while FIRE is active (Cmd 104/105)
 *   scopeFreezeEnabled — freeze position packets while SCOPE is active (Cmd 137/138)
 *   manualFreezeEnabled — always-freeze (existing FREEZE button, kept separate)
 *
 * isFreezeActive() is what the packet filter checks.
 */
public final class FreezeController {

    private static final long FIRE_HOLD_MS  = 700L;
    private static final long SCOPE_HOLD_MS = 1800L;

    private static final int CMD_STOP_ALL    = 103;
    private static final int CMD_FIRE_START  = 104;
    private static final int CMD_FIRE_STOP   = 105;
    private static final int CMD_SCOPE_START = 137;
    private static final int CMD_SCOPE_STOP  = 138;

    private static volatile boolean aimLagEnabled       = false;
    private static volatile boolean autoFreezeEnabled   = false;
    private static volatile boolean scopeFreezeEnabled  = false;
    private static volatile boolean manualFreezeEnabled = false;

    private static final ConcurrentHashMap<Integer, Long> candidateLastLogAt = new ConcurrentHashMap<>();

    private static volatile long    firePulseEndsAt  = 0L;
    private static volatile boolean firing           = false;
    private static volatile long    scopePulseEndsAt = 0L;
    private static volatile boolean scoping          = false;

    private static volatile long lastCandidateLogAt = 0L;
    private static volatile long lastFeedLogAt      = 0L;
    private static volatile long lastFreezeLogAt    = 0L;
    private static volatile long lastPoseLogAt      = 0L;
    private static volatile long rewrittenPoseCount = 0L;

    private FreezeController() { }

    // ---- setters called from FloatingOverlayService via broadcast ----

    public static void setAimLag(boolean enabled) {
        aimLagEnabled = enabled;
        log("Aim Lag=" + enabled);
    }

    public static void setAutoFreeze(boolean enabled) {
        autoFreezeEnabled = enabled;
        log("Auto Freeze(fire)=" + enabled);
    }

    public static void setScopeFreeze(boolean enabled) {
        scopeFreezeEnabled = enabled;
        log("Scope Freeze=" + enabled);
    }

    public static void setManualFreeze(boolean enabled) {
        manualFreezeEnabled = enabled;
        log("Manual Freeze=" + enabled);
    }

    public static boolean isAimLag()      { return aimLagEnabled; }
    public static boolean isAutoFreeze()  { return autoFreezeEnabled; }
    public static boolean isScopeFreeze() { return scopeFreezeEnabled; }
    public static boolean isManualFreeze(){ return manualFreezeEnabled; }

    // ---- runtime reset (call on engine start/stop) ----

    public static void resetRuntime() {
        firing           = false;
        scoping          = false;
        firePulseEndsAt  = 0L;
        scopePulseEndsAt = 0L;
        lastFreezeLogAt  = 0L;
        lastPoseLogAt    = 0L;
        lastCandidateLogAt = 0L;
        lastFeedLogAt    = 0L;
        rewrittenPoseCount = 0L;
        candidateLastLogAt.clear();
    }

    // ---- command observer (call from packet filter on every outbound command) ----

    public static void observeCommand(int commandId, byte[] body, boolean serverToClient) {
        if (commandId <= 0 || commandId > 65535) return;

        long now = SystemClock.elapsedRealtime();

        if (!serverToClient) {
            switch (commandId) {
                case CMD_STOP_ALL:
                    firing  = false;
                    scoping = false;
                    firePulseEndsAt  = 0L;
                    scopePulseEndsAt = 0L;
                    break;
                case CMD_FIRE_START:
                    firing = true;
                    firePulseEndsAt = now + FIRE_HOLD_MS;
                    break;
                case CMD_FIRE_STOP:
                    firing = false;
                    firePulseEndsAt = 0L;
                    break;
                case CMD_SCOPE_START:
                    scoping = true;
                    scopePulseEndsAt = now + SCOPE_HOLD_MS;
                    break;
                case CMD_SCOPE_STOP:
                    scoping = false;
                    scopePulseEndsAt = 0L;
                    break;
                default:
                    break;
            }
        }

        if (aimLagEnabled) {
            logCandidate(commandId, body == null ? 0 : body.length, serverToClient, now);
        }
    }

    // ---- main gate used by the packet filter ----

    /**
     * Returns true when position/pose packets should be held (frozen).
     * Call this from your existing freeze check in the packet filter — OR use
     * it alongside the existing manualFreezeEnabled flag by replacing that flag
     * with this method.
     */
    public static boolean isFreezeActive() {
        long now = SystemClock.elapsedRealtime();
        return manualFreezeEnabled || (
                aimLagEnabled && (
                        (autoFreezeEnabled  && isFireActive(now)) ||
                        (scopeFreezeEnabled && isScopeActive(now))
                )
        );
    }

    // ---- diagnostics ----

    public static void recordDownlinkRewrite(String info, int commandId, int entityCount) {
        if (entityCount <= 0) return;
        long now  = SystemClock.elapsedRealtime();
        long total = ++rewrittenPoseCount;
        if (now - lastFreezeLogAt >= 1000) {
            lastFreezeLogAt = now;
            log("Freeze rewrite S2C cmd=" + commandId + " entities=" + entityCount + " total=" + total + " " + info);
        }
    }

    public static void recordDownlinkFeed(String info, int commandId, int entityCount,
            int liveCount, int heldCount, boolean active) {
        if (entityCount <= 0) return;
        if (!manualFreezeEnabled && !aimLagEnabled) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastFeedLogAt < 1000) return;
        lastFeedLogAt = now;
        log("Freeze feed S2C cmd=" + commandId + " entities=" + entityCount
                + " live=" + liveCount + " held=" + heldCount + " active=" + active
                + " fire=" + isFireActive(now) + " scope=" + isScopeActive(now) + " " + info);
    }

    // ---- internals ----

    private static boolean isFireActive(long now)  { return firing  || now < firePulseEndsAt;  }
    private static boolean isScopeActive(long now) { return scoping || now < scopePulseEndsAt; }

    private static void logCandidate(int commandId, int bodyLen, boolean s2c, long now) {
        if (commandId == 2001 || commandId == 2012) return;
        if ((commandId >= 101 && commandId <= 105)
                || commandId == CMD_SCOPE_START || commandId == CMD_SCOPE_STOP) return;

        Long lastAt = candidateLastLogAt.get(commandId);
        boolean canCmd    = lastAt == null || now - lastAt >= 2500;
        boolean canGlobal = now - lastCandidateLogAt >= 200;
        if (canCmd && canGlobal) {
            candidateLastLogAt.put(commandId, now);
            lastCandidateLogAt = now;
            log("Aim candidate " + (s2c ? "S2C" : "C2S") + " cmd=" + commandId + " body=" + bodyLen);
        }
    }

    private static void log(String msg) {
        android.util.Log.d("FreezeCtrl", msg);
    }
}
