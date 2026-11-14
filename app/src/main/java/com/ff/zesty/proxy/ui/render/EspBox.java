package com.ff.zesty.proxy.ui.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Draws the ESP box for one enemy entity.
 *
 * <p>Style matches the reference screenshot: a full white rectangle outline
 * with a green vertical bar ("|") on the left side, flush to the box left
 * edge. The bar is 3 px wide and spans the full box height.</p>
 *
 * <p>Self-contained: owns its own {@link Paint} instances, initialised once in
 * the constructor, so {@link EspRenderView} stays allocation-free on
 * {@code onDraw}. Call {@link #draw} once per visible enemy per frame.</p>
 */
public final class EspBox {

    // White rect outline for all roles
    private static final int COLOR_ENEMY   = Color.argb(235, 255, 255, 255);
    private static final int COLOR_TEAM    = Color.argb(235, 255, 255, 255);
    private static final int COLOR_UNKNOWN = Color.argb(235, 255, 255, 255);

    // Green left-side bar — matches the screenshot "|" indicator
    private static final int COLOR_BAR = Color.argb(235, 0, 230, 80);

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar    = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Cached last colour so setColor is only called when it changes. */
    private int lastStrokeColor = 0;

    /**
     * @param density {@code DisplayMetrics.density} for stroke width scaling
     */
    public EspBox(float density) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, 1.5f * density));
        stroke.setColor(COLOR_ENEMY);

        // Filled rect for the green bar
        bar.setStyle(Paint.Style.FILL);
        bar.setColor(COLOR_BAR);
    }

    /**
     * Draws the box for one enemy: full white outline + green left bar.
     *
     * @param canvas     target canvas
     * @param left       box left edge, px
     * @param top        box top edge (head), px
     * @param right      box right edge, px
     * @param bottom     box bottom edge (feet), px
     * @param role       {@code EspEngine.ROLE_ENEMY / ROLE_TEAM / ROLE_UNKNOWN}
     * @param alpha      255 = fully opaque; lower for stale data
     * @param boxColor   unused — box is always white per UX update
     */
    public void draw(Canvas canvas,
                     float left, float top, float right, float bottom,
                     int role, int alpha, int boxColor) {

        // Always white rectangle regardless of role
        int color = COLOR_ENEMY;

        if (color != lastStrokeColor) {
            lastStrokeColor = color;
            stroke.setColor(color);
        }

        stroke.setAlpha(alpha);
        bar.setAlpha(alpha);

        // ---- full white outline rectangle ----
        canvas.drawRect(left, top, right, bottom, stroke);

        // ---- green left-side bar ("|") — 3px wide, full height ----
        float barW = Math.max(2f, 3f);
        canvas.drawRect(left, top, left + barW, bottom, bar);
    }
}
