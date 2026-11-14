package com.ff.zesty.proxy.ui.render;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws a dist+hp badge above the ESP box.
 * Colors: white text on dark semi-transparent background. No purple.
 */
public final class EspMoco {

    private static final int BADGE_BG_COLOR   = Color.argb(180, 20, 20, 20);
    private static final int BADGE_TEXT_COLOR = Color.argb(255, 255, 255, 255);
    private static final int DIST_BG_COLOR    = Color.argb(160, 10, 10, 10);

    private final float padH;
    private final float padV;
    private final float cornerR;
    private final float gapBelowBadge;

    private final Paint badgePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distBgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distTxtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF        badgeRect = new RectF();
    private final RectF        distRect  = new RectF();
    private final StringBuilder buf      = new StringBuilder(32);

    public EspMoco(float density) {
        padH          = 6f * density;
        padV          = 3f * density;
        cornerR       = 4f * density;
        gapBelowBadge = 3f * density;

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(BADGE_BG_COLOR);

        textPaint.setColor(BADGE_TEXT_COLOR);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(9.5f * density);

        distBgPaint.setStyle(Paint.Style.FILL);
        distBgPaint.setColor(DIST_BG_COLOR);

        distTxtPaint.setColor(BADGE_TEXT_COLOR);
        distTxtPaint.setTypeface(Typeface.MONOSPACE);
        distTxtPaint.setTextAlign(Paint.Align.CENTER);
        distTxtPaint.setTextSize(8f * density);
    }

    public void draw(Canvas canvas,
                     float cx, float boxTop,
                     String name, float distM,
                     int alpha) {

        boolean hasName = (name != null && !name.isEmpty());
        if (!hasName && distM <= 0f) return;

        badgePaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
        distBgPaint.setAlpha((int)(alpha * 0.85f));
        distTxtPaint.setAlpha(alpha);

        float badgeBottom = boxTop - gapBelowBadge;

        if (hasName) {
            float textW  = textPaint.measureText(name);
            float badgeW = textW + padH * 2f;
            float badgeH = textPaint.getTextSize() + padV * 2f;
            float badgeTop = badgeBottom - badgeH;
            badgeRect.set(cx - badgeW / 2f, badgeTop, cx + badgeW / 2f, badgeBottom);
            canvas.drawRoundRect(badgeRect, cornerR, cornerR, badgePaint);
            float nameTextY = badgeRect.centerY()
                    - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(name, cx, nameTextY, textPaint);
        }

        if (distM > 0f) {
            buf.setLength(0);
            buf.append(Math.round(distM)).append('m');

            float dw  = distTxtPaint.measureText(buf, 0, buf.length()) + padH * 1.6f;
            float dh  = distTxtPaint.getTextSize() + padV * 1.5f;
            float dTop    = hasName ? badgeBottom - dh - 1f
                                    : boxTop - gapBelowBadge - dh;
            float dBottom = dTop + dh;
            float dc  = hasName
                    ? cx + (badgeRect.right - cx) * 0.5f + dw * 0.5f
                    : cx;
            distRect.set(dc - dw / 2f, dTop, dc + dw / 2f, dBottom);
            canvas.drawRoundRect(distRect, cornerR * 0.7f, cornerR * 0.7f, distBgPaint);
            float dtY = distRect.centerY()
                    - (distTxtPaint.ascent() + distTxtPaint.descent()) / 2f;
            canvas.drawText(buf, 0, buf.length(), dc, dtY, distTxtPaint);
        }
    }
}
