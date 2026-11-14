package com.ff.zesty.proxy.filter;

import com.ff.zesty.proxy.decode.frame.HeaderField;
import com.ff.zesty.proxy.decode.frame.HeaderLayout;
import com.ff.zesty.proxy.decode.frame.MsgKey;
import com.ff.zesty.proxy.decode.frame.PacketCodec;
import com.ff.zesty.proxy.dev.DevLogger;
import com.ff.zesty.proxy.util.Leb128;
import com.ff.zesty.proxy.util.ZigZag;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;

/**
 * Speed boost via server-side buff injection.
 *
 * <p>When armed, injects a {@code BuffBehavior} packet (Cmd 170) on every
 * outbound pulse — once immediately on {@link #start()}, then every
 * {@link #INJECT_INTERVAL_MS} milliseconds via a background thread.</p>
 *
 * <p>The buff payload matches the Python {@code build_buffbehavior()} snippet:
 * <ul>
 *   <li>UID = 4</li>
 *   <li>BuffBehaviorID = 333</li>
 *   <li>StatusType = 0</li>
 *   <li>Life = 3000.0f</li>
 *   <li>BuffData = [988]</li>
 * </ul>
 * PlayerID (my_player_uid) is learned from the first solved outbound packet.</p>
 *
 * <p>The packet header is cloned from the first valid outbound game packet so
 * the session mask/permutation is always correct without coupling to
 * {@link com.ff.zesty.proxy.protocol.PacketPipeline}.</p>
 */
public final class BuffSpeedFilter implements PacketFilter {

    private static final int   CMD_BUFF_BEHAVIOR    = 170;
    private static final int   BUFF_UID             = 4;
    private static final int   BUFF_BEHAVIOR_ID     = 333;
    private static final int   BUFF_STATUS_TYPE     = 0;
    private static final float BUFF_LIFE            = 3000.0f;
    private static final int   BUFF_DATA_ENTRY      = 988;
    private static final int   INJECT_INTERVAL_MS   = 2000;  // re-inject every 2 s

    // Cmd 2001 carries SEND_OPTION=0 (non-reliable), so its MsgKey is 100-109.
    // We want non-reliable for the buff inject too.
    private static final int   NON_RELIABLE_MSGKEY  = 101;   // arbitrary, within 100-109

    private final DevLogger      logger;
    private final PacketSender   sender;
    private final AtomicBoolean  active = new AtomicBoolean(false);

    /** Captured server endpoint from the first seen outbound packet. */
    private volatile int  serverDip;
    private volatile int  serverDp;
    private volatile int  serverSp;

    /** Captured header template (non-reliable, cmd=170 written in). */
    private volatile byte[] headerTemplate;
    /** Layout that produced the template, for reserialize. */
    private volatile HeaderLayout templateLayout;

    /** Player UID snooped from the first outbound CMD_PLAYER_STATE_SYNC. */
    private volatile long playerUid = -1;

    private volatile Thread injectThread;

    private final Random rng = new Random();

    public BuffSpeedFilter(PacketSender sender, DevLogger logger) {
        this.sender = sender;
        this.logger = logger;
    }

    @Override public String  name()     { return "buff_speed"; }
    @Override public boolean isActive() { return active.get(); }

    @Override
    public void start() {
        if (active.getAndSet(true)) return;   // already running
        injectOnce();
        Thread t = new Thread(this::injectLoop, "buff-speed-inject");
        t.setDaemon(true);
        injectThread = t;
        t.start();
    }

    @Override
    public void stop() {
        active.set(false);
        Thread t = injectThread;
        injectThread = null;
        if (t != null) t.interrupt();
    }

    @Override
    public void shutdown() {
        stop();
    }

    // ── packet filter hooks ──────────────────────────────────────────────────

    @Override
    public FilterDecision onOutbound(PacketContext ctx) {
        // Snoop the server endpoint and header layout from every outbound packet.
        // The first valid non-reliable match packet is enough for a template.
        if (headerTemplate == null) {
            tryLearnTemplate(ctx);
        }
        // Snoop player UID from Cmd 2001 if not yet known.
        if (playerUid < 0 && ctx.hasCmd() && ctx.cmd == 2001) {
            tryLearnPlayerUid(ctx);
        }
        return FilterDecision.PASS;
    }

    @Override
    public FilterDecision onInbound(PacketContext ctx) {
        return FilterDecision.PASS;
    }

    // ── header template capture ──────────────────────────────────────────────

    private void tryLearnTemplate(PacketContext ctx) {
        // Need a solved non-reliable game packet to clone its header.
        if (ctx.plen < MsgKey.HEADER_NON_RELIABLE) return;
        int msgKey = ctx.buf[ctx.off] & 0xFF;
        if (!MsgKey.isValid(msgKey) || MsgKey.isReliable(msgKey)) return;

        // We need the current layout from PacketPipeline — read it lazily.
        com.ff.zesty.proxy.protocol.PacketPipeline pipeline =
                com.ff.zesty.proxy.protocol.PacketPipeline.current();
        if (pipeline == null) return;
        HeaderLayout layout = pipeline.layout();
        if (layout == null) return;

        // Clone the 8-byte non-reliable header and stamp cmd=170 into it.
        byte[] tmpl = new byte[MsgKey.HEADER_NON_RELIABLE];
        System.arraycopy(ctx.buf, ctx.off, tmpl, 0, MsgKey.HEADER_NON_RELIABLE);
        // Stamp cmd and dataLen; sendOption=0 (non-reliable) and flags=0 are
        // already in the template from the clone.
        PacketCodec.reserialize(tmpl, 0, tmpl.length, layout,
                /*sendOption*/ 0, CMD_BUFF_BEHAVIOR, /*orderId*/ -1,
                /*flags*/ 0, /*dataLen set after body built*/ -1);

        templateLayout = layout;
        headerTemplate = tmpl;
        serverDip = ctx.dip;
        serverDp  = ctx.dp;
        serverSp  = ctx.sp;
    }

    private void tryLearnPlayerUid(PacketContext ctx) {
        // Field 2 of Cmd 2001 body is PlayerID (unsigned varint at position after
        // UID). The body starts at ctx.off, layout header precedes it; the actual
        // body offset inside ctx.buf is ctx.off.
        // Body layout: var(UID) var(PlayerID) ...
        // We read the first two varints.
        int end = ctx.off + ctx.plen;
        int[] pos = { ctx.off + MsgKey.HEADER_NON_RELIABLE };
        if (pos[0] >= end) return;
        // UID (field 1) — skip
        long uid = Leb128.read(ctx.buf, pos, end);
        if (uid == Leb128.BAD) return;
        // PlayerID (field 2)
        long pid = Leb128.read(ctx.buf, pos, end);
        if (pid == Leb128.BAD) return;
        playerUid = pid;
    }

    // ── injection ────────────────────────────────────────────────────────────

    private void injectLoop() {
        while (active.get()) {
            try {
                Thread.sleep(INJECT_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
            if (active.get()) injectOnce();
        }
    }

    private void injectOnce() {
        byte[] tmpl = headerTemplate;
        HeaderLayout layout = templateLayout;
        if (tmpl == null || layout == null) return;

        long uid = playerUid;
        if (uid < 0) return;

        byte[] body = buildBuffBody(uid);
        byte[] pkt  = new byte[MsgKey.HEADER_NON_RELIABLE + body.length];

        // Copy header template and randomise MsgKey (byte 0) within non-reliable range.
        System.arraycopy(tmpl, 0, pkt, 0, tmpl.length);
        pkt[0] = (byte) (100 + rng.nextInt(10));  // 100..109

        // Write body
        System.arraycopy(body, 0, pkt, MsgKey.HEADER_NON_RELIABLE, body.length);

        // Stamp dataLen and refresh CRC.
        PacketCodec.reserialize(pkt, 0, pkt.length, layout,
                /*sendOption*/ 0, CMD_BUFF_BEHAVIOR, /*orderId*/ -1,
                /*flags*/ 0, body.length);

        sender.send(new TelePacket(pkt, serverDip, serverDp, serverSp, TelePacket.TYPE_MOVE));
    }

    // ── body serialization (mirrors Python build_buffbehavior) ───────────────

    /**
     * Builds the buff-behavior body bytes.
     *
     * <pre>
     *   var(UID=4)
     *   var(PlayerID=playerUid)
     *   var(InventoryID=0)
     *   var(BuffBehaviorID=333)
     *   byte(StatusType=0)
     *   var(LifeBits = Float.floatToRawIntBits(3000.0f))
     *   zz(len=1)          // BuffData count via zigzag
     *   var(988)           // BuffData[0]
     * </pre>
     */
    private static byte[] buildBuffBody(long playerUid) {
        byte[] tmp = new byte[64];
        int p = 0;
        p = Leb128.write(tmp, p, BUFF_UID);
        p = Leb128.write(tmp, p, playerUid);
        p = Leb128.write(tmp, p, 0L);                           // InventoryID
        p = Leb128.write(tmp, p, BUFF_BEHAVIOR_ID);
        tmp[p++] = (byte) BUFF_STATUS_TYPE;
        int lifeBits = Float.floatToRawIntBits(BUFF_LIFE);
        p = Leb128.write(tmp, p, lifeBits & 0xFFFFFFFFL);       // LifeBits as unsigned
        // zz(len=1) — zigzag encode 1
        p = Leb128.write(tmp, p, ZigZag.encode(1L));
        p = Leb128.write(tmp, p, BUFF_DATA_ENTRY);

        byte[] out = new byte[p];
        System.arraycopy(tmp, 0, out, 0, p);
        return out;
    }
}
