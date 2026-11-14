package com.ff.zesty.proxy.ui.button;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Owns the top banner view and the rainbow/pulse/banner animators that drive the
 * ring drawables and the banner text.
 */
public class OverlayAnimations {

    private final Context context;
    private final WindowManager wm;
    private final ArrayList<AnimatedCircleDrawable> ringDrawables = new ArrayList<AnimatedCircleDrawable>();

    private TextView topBanner;
    private boolean bannerAdded;
    private ValueAnimator shineAnimator;
    private ValueAnimator pulseAnimator;
    private ValueAnimator bannerAnimator;

    public OverlayAnimations(Context context, WindowManager wm) {
        this.context = context;
        this.wm = wm;
    }

    /**
     * The status title.
     *
     * <p>Replaces the decorative banner that used to sit here. That one was
     * branding with a status word appended, and it drove an infinite
     * ValueAnimator whose per-frame listener varied {@code setShadowLayer}'s
     * blur RADIUS -- which misses HWUI's blurred-text cache on every frame, at
     * 120 Hz, in a window composited over the game, forever. This one is a
     * static TextView whose text changes only when the state does.</p>
     *
     * <p>What it answers, in one glance, without opening the debug panel:
     * whether the engine can work yet, and how many players it can see. Those
     * are the two questions asked every time something looks wrong.</p>
     */
    public TextView makeTopBanner(int floatSizePercent) {
        TextView tv = new TextView(context);
        tv.setText("ENGINE OFF");
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setAlpha(0.97f);
        int padH = scalePx(14, floatSizePercent);
        int padV = scalePx(6, floatSizePercent);
        tv.setPadding(padH, padV, padH, padV);
        tv.setTextColor(COL_IDLE);
        // A 95% scrim, not a glow: this has to stay readable over a bright
        // gameplay frame, where the old translucent style fell to 3.46:1.
        tv.setBackground(pill(0xF20B0F14, 0xFF2C333D));

        int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT
        );

        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.x = 0;
        p.y = scalePx(10, floatSizePercent);

        try {
            wm.addView(tv, p);
            bannerAdded = true;
        } catch (Exception e) { }

        topBanner = tv;
        return tv;
    }

    /** Rounded pill background, built in code so no drawable lookup is needed. */
    private static android.graphics.drawable.Drawable pill(int fill, int stroke) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(999f);
        d.setStroke(2, stroke);
        return d;
    }

    // Design-system colours, matching res/values/colors.xml.
    private static final int COL_IDLE    = 0xFF7D8899;   // fl_text_tertiary
    private static final int COL_LEARN   = 0xFFFFB020;   // fl_warning
    private static final int COL_READY   = 0xFF22D3EE;   // fl_accent
    private static final int COL_ACTIVE  = 0xFF3DDC84;   // fl_success

    public void startRainbowAnimation() {
        if (ringDrawables.isEmpty()) return;
        if (shineAnimator != null) shineAnimator.cancel();
        shineAnimator = ValueAnimator.ofFloat(0f, 360f);
        shineAnimator.setDuration(7200L);
        shineAnimator.setInterpolator(new LinearInterpolator());
        shineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                float rotation = ((Float) animator.getAnimatedValue()).floatValue();
                for (int i = 0; i < ringDrawables.size(); i++) {
                    ringDrawables.get(i).setRotationDegrees(rotation + (i * 22f));
                }
            }
        });
        shineAnimator.start();
    }

    public void startPulseAnimation() {
        if (ringDrawables.isEmpty()) return;
        if (pulseAnimator != null) pulseAnimator.cancel();
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(1800L);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                float pulse = ((Float) animator.getAnimatedValue()).floatValue();
                for (int i = 0; i < ringDrawables.size(); i++) {
                    ringDrawables.get(i).setPulse(Math.min(1f, pulse + (i * 0.08f)));
                }
            }
        });
        pulseAnimator.start();
    }

    // startBannerAnimation() DELETED.
    //
    // It drove an infinite ValueAnimator whose per-frame listener called
    // Color.parseColor twice and varied setShadowLayer's blur RADIUS. A
    // changing blur radius misses HWUI's blurred-text cache on every single
    // frame -- 120 times a second, in a window composited over the game, for
    // as long as the overlay existed. It was the most expensive thing this app
    // did, and it was decorating a word.
    //
    // The title now repaints only when its text actually changes.


    /** Cancels all running animators (used on service destroy). */
    public void cancelAll() {
        if (shineAnimator != null) shineAnimator.cancel();
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (bannerAnimator != null) bannerAnimator.cancel();
        if (rainbowBannerAnimator != null) rainbowBannerAnimator.cancel();
        rainbowBannerAnimator = null;
    }

    /** Removes the top banner view (used on service destroy). */
    public void removeBanner() {
        if (bannerAdded && topBanner != null) {
            try {
                wm.removeView(topBanner);
            } catch (Exception e) { }
        }
        topBanner = null;
        bannerAdded = false;
    }

    // ---- Live engine status on the banner ----

    /** Banner status codes (drive setBannerStatus). */
    public static final int STATUS_IDLE = 0;        // engine off / no match
    public static final int STATUS_LEARNING = 1;    // engine on, stream learning
    public static final int STATUS_READY = 2;       // match learned: effects armed
    public static final int STATUS_ACTIVE = 3;      // an effect is running


    /**
     * Updates the banner text + color to the engine's live state, so the
     * user can SEE whether the packet engine is armed before trusting a
     * toggle. The rainbow animation only runs when READY/ACTIVE; LEARNING
     * shows an amber pulse, IDLE a dim gray.
     */
    /**
     * Sets the title.
     *
     * <p>Counts are passed in rather than pulled, because this class has no
     * reference to the engine and should not grow one -- the service already
     * polls state on a timer and is the right place to own that.</p>
     *
     * @param status  one of the STATUS_ constants
     * @param enemies live enemies seen, or -1 when the ESP is off
     * @param mates   live teammates seen
     * @param unknown live entities whose side is not yet decided
     * @param mounted true while riding anything -- shown because the ESP runs
     *                on a different, weaker set of inputs in that state, so
     *                "it went odd in a car" should be visibly a mode and not
     *                a mystery
     */
    // Rainbow hue offset, advanced by the banner animator each tick.
    private float rainbowHueOffset = 0f;
    private ValueAnimator rainbowBannerAnimator;

    /** Starts the per-frame hue-shift animator for the banner rainbow. */
    private void startBannerRainbow() {
        if (rainbowBannerAnimator != null) return;
        rainbowBannerAnimator = ValueAnimator.ofFloat(0f, 360f);
        rainbowBannerAnimator.setDuration(2400L);
        rainbowBannerAnimator.setInterpolator(new LinearInterpolator());
        rainbowBannerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rainbowBannerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                rainbowHueOffset = (Float) animator.getAnimatedValue();
                applyRainbowShader();
            }
        });
        rainbowBannerAnimator.start();
    }

    /**
     * Applies a LinearGradient rainbow shader across the banner text.
     * Uses a Shader, NOT setShadowLayer blur radius, so it is HWUI-cache
     * friendly — zero per-frame allocations after the first call per layout.
     */
    private void applyRainbowShader() {
        if (topBanner == null) return;
        float w = topBanner.getWidth();
        if (w <= 0f) {
            // View not laid out yet — try again next animator tick.
            topBanner.post(new Runnable() {
                @Override public void run() { applyRainbowShader(); }
            });
            return;
        }
        float h = rainbowHueOffset;
        // 7 stops across the full width, cycling the hue wheel.
        int[] colors = {
            Color.HSVToColor(new float[]{h % 360f,        1f, 1f}),
            Color.HSVToColor(new float[]{(h + 51f) % 360f, 1f, 1f}),
            Color.HSVToColor(new float[]{(h + 103f) % 360f, 1f, 1f}),
            Color.HSVToColor(new float[]{(h + 154f) % 360f, 1f, 1f}),
            Color.HSVToColor(new float[]{(h + 206f) % 360f, 1f, 1f}),
            Color.HSVToColor(new float[]{(h + 257f) % 360f, 1f, 1f}),
            Color.HSVToColor(new float[]{(h + 309f) % 360f, 1f, 1f}),
        };
        LinearGradient shader = new LinearGradient(
                0f, 0f, w, 0f,
                colors, null,
                Shader.TileMode.CLAMP);
        topBanner.getPaint().setShader(shader);
        topBanner.invalidate();
    }

    public void setBannerStatus(int status, int enemies, int mates, int unknown,
                                boolean mounted) {
        if (topBanner == null) return;

        // Build the suffix: enemy count, teammate count, unknown count, mounted flag.
        StringBuilder sb = new StringBuilder(64);
        sb.append("Zesty Proxy");
        if (enemies >= 0) {
            sb.append("  •  ").append(enemies).append('E');
            if (mates > 0)   sb.append(" / ").append(mates).append('T');
            if (unknown > 0) sb.append(" / ").append(unknown).append('?');
            // "RIDING" label removed — mode is internal, not shown in banner.
        }
        // Always show engine state as a terse suffix so the operator still
        // knows what is happening without losing the branding.
        switch (status) {
            case STATUS_LEARNING: sb.append("  •  SYNC"); break;
            case STATUS_IDLE:     sb.append("  •  OFF");  break;
            // READY / ACTIVE: counts above are enough.
        }
        String text = sb.toString();
        if (text.equals(lastBannerText)) return;   // no needless relayout
        lastBannerText = text;
        topBanner.setText(text);
        // Clear any solid colour so the shader shows through.
        topBanner.setTextColor(Color.WHITE);
        // Kick off the rainbow animator (idempotent after first call).
        startBannerRainbow();
        // Apply immediately so there is no single white frame before first tick.
        topBanner.post(new Runnable() {
            @Override public void run() { applyRainbowShader(); }
        });
    }

    private String lastBannerText = "";

    private int scalePx(int basePx, int floatSizePercent) {
        return Math.max(1, Math.round(basePx * (floatSizePercent / 100f)));
    }
}
