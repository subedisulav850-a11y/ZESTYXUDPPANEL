package com.ff.zesty.proxy.filter;

import com.ff.zesty.proxy.decode.body.PositionSync;
import com.ff.zesty.proxy.dev.DevLogger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speed boost: two-lever approach.
 *
 * <p><b>Lever 1 — velocity inflation.</b> Inflates V_VEL_X/Z on every outbound
 * Cmd 2001 by {@link #SPEED_MULT}. Tells the server the player is moving faster;
 * affects how other clients interpolate you between ticks. Position untouched so
 * no distance-gate trip.</p>
 *
 * <p><b>Lever 2 — teleport step budget.</b> When armed, bumps the TeleportFilter's
 * per-tick step to {@link #BOOST_STEP_M} (~2x sprint, well under the 2.77m server
 * ceiling). Restores {@link TeleportFilter#DEFAULT_STEP_M} on disarm.</p>
 */
public final class SpeedFilter implements PacketFilter {

    private static final int    CMD_PLAYER_STATE_SYNC = 2001;
    private static final float  SPEED_MULT   = 1.10f;
    /** ~4x sprint speed, just under the 2.77m server gate. */
    public static final double  BOOST_STEP_M = 2.60;

    private final DevLogger      logger;
    private final AtomicBoolean  active   = new AtomicBoolean(false);
    private final PositionSync   sync     = new PositionSync();
    private TeleportFilter       teleport;

    public SpeedFilter(DevLogger logger) {
        this.logger = logger;
    }

    /** Late-bound: call once after TeleportFilter is constructed. */
    public void setTeleportFilter(TeleportFilter tf) {
        this.teleport = tf;
    }

    @Override public String  name()     { return "speed"; }
    @Override public boolean isActive() { return active.get(); }

    @Override
    public void start() {
        active.set(true);
        if (teleport != null) teleport.setStepMetres(BOOST_STEP_M);
    }

    @Override
    public void stop() {
        active.set(false);
        if (teleport != null) teleport.setStepMetres(TeleportFilter.DEFAULT_STEP_M);
    }

    @Override
    public void shutdown() {
        active.set(false);
        if (teleport != null) teleport.setStepMetres(TeleportFilter.DEFAULT_STEP_M);
    }

    @Override
    public FilterDecision onOutbound(PacketContext ctx) {
        if (!active.get())                           return FilterDecision.PASS;
        if (!ctx.hasCmd())                           return FilterDecision.PASS;
        if (ctx.cmd != CMD_PLAYER_STATE_SYNC)        return FilterDecision.PASS;
        if (!sync.parse(ctx.buf, ctx.off, ctx.plen)) return FilterDecision.PASS;

        long velX = sync.signedAt(PositionSync.V_VEL_X);
        long velZ = sync.signedAt(PositionSync.V_VEL_Z);

        // Skip if standing still — don't inflate gravity/idle noise
        if (velX == 0 && velZ == 0) return FilterDecision.PASS;

        sync.setSigned(PositionSync.V_VEL_X, Math.round(velX * SPEED_MULT));
        sync.setSigned(PositionSync.V_VEL_Z, Math.round(velZ * SPEED_MULT));
        sync.seal();

        return FilterDecision.PASS;
    }

    @Override
    public FilterDecision onInbound(PacketContext ctx) {
        return FilterDecision.PASS;
    }
}
