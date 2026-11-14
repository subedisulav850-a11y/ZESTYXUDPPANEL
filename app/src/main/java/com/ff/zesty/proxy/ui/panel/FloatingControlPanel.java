package com.ff.zesty.proxy.ui.panel;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ff.zesty.proxy.R;
import com.ff.zesty.proxy.config.Prefs;
import com.ff.zesty.proxy.freeze.FreezeController;
import com.ff.zesty.proxy.ui.settings.AppearanceBroadcaster;

/**
 * Compact in-game floating panel — Black & White theme.
 * Controls: Aim Lag (+ Fire/Scope/Both selector) · Aimbot · ESP Line · ESP Box ·
 *           ESP Skeleton · ESP Distance.
 * Each ESP row uses a real Switch widget tinted white.
 */
public final class FloatingControlPanel {

    // ── Palette (Black & White) ──────────────────────────────────────────────
    private static final int BG        = Color.rgb(10, 10, 10);
    private static final int SURFACE   = Color.rgb(20, 20, 20);
    private static final int SURFACE_2 = Color.rgb(30, 30, 30);
    private static final int WHITE     = Color.WHITE;
    private static final int GRAY_HI   = Color.rgb(220, 220, 220);
    private static final int GRAY_MID  = Color.rgb(140, 140, 140);
    private static final int GRAY_LO   = Color.rgb(70,  70,  70);
    private static final int BLACK     = Color.BLACK;
    private static final int BORDER    = Color.rgb(55, 55, 55);
    private static final int BORDER_HI = Color.rgb(100, 100, 100);

    // ── State ────────────────────────────────────────────────────────────────
    private final Context               context;
    private final WindowManager         wm;
    private final AppearanceBroadcaster broadcaster;
    private final SharedPreferences     prefs;
    private final android.os.Handler    handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private LinearLayout header;
    private ImageButton  logoView;
    private TextView     titleView;
    private TextView     minimizeView;
    private TextView     closeView;
    private LinearLayout content;
    private ScrollView   scroll;
    private boolean added;
    private boolean minimized;
    private int     panelWidth;
    private int     maxPanelHeight;
    private int     downX, downY;
    private float   rawDownX, rawDownY;
    private boolean dragMoved;

    public FloatingControlPanel(Context context, WindowManager wm) {
        this.context     = context;
        this.wm          = wm;
        this.broadcaster = new AppearanceBroadcaster(context);
        this.prefs       = Prefs.get(context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public void show() {
        if (added) return;
        final int[] size = displaySize();
        panelWidth     = clamp(Math.round(size[0] * 0.48f), dp(160), dp(220));
        maxPanelHeight = clamp(Math.round(size[1] * 0.50f), dp(240), dp(420));

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.setBackground(panelBg());

        // Minimised bubble logo
        logoView = new ImageButton(context);
        logoView.setImageResource(R.drawable.nepdon_logo_floating);
        logoView.setContentDescription("Open Zesty controls");
        logoView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        logoView.setBackground(round(Color.rgb(16, 16, 16), dp(26), WHITE));
        logoView.setPadding(0, 0, 0, 0);
        logoView.setVisibility(View.GONE);
        root.addView(logoView, new LinearLayout.LayoutParams(dp(52), dp(52)));

        // ── Header ───────────────────────────────────────────────────────────
        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(2), dp(2), dp(2));
        header.setBackground(round(SURFACE_2, dp(10), BORDER_HI));

        titleView = new TextView(context);
        titleView.setText("Zesty Proxy");
        titleView.setTextColor(WHITE);
        titleView.setTextSize(13f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(0, dp(36), 1f);
        titleLp.leftMargin = dp(4);
        header.addView(titleView, titleLp);

        minimizeView = headerBtn("−", "Minimize", GRAY_MID);
        closeView    = headerBtn("×", "Close",    GRAY_HI);
        header.addView(minimizeView, new LinearLayout.LayoutParams(dp(34), dp(36)));
        header.addView(closeView,    new LinearLayout.LayoutParams(dp(34), dp(36)));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        // Thin divider under header
        View divider = new View(context);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin    = dp(4);
        divLp.bottomMargin = dp(4);
        root.addView(divider, divLp);

        // ── Scrollable content ───────────────────────────────────────────────
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), dp(2), dp(2), dp(6));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxPanelHeight - dp(60));
        root.addView(scroll, scrollLp);

        // ── Controls ─────────────────────────────────────────────────────────
        // Aimbot: touch-injection head-lock. Uses KEY_AIMBOT which FloatingOverlayService
        // already handles via EXTRA_AIMBOT → AimConfig.AIM_ENABLED + AIM_BOX_FRAC=HEAD.
        // Wire-rotation (AIM_ROTATION_ENABLED) is intentionally NOT touched here —
        // that causes screen-rotation artefacts and is controlled separately in AimSetupPanel.
        switchRow("Aimbot",       Prefs.KEY_AIMBOT,         false);
        rowDivider();
        switchRow("Aim Lag",      Prefs.KEY_AIM_LAG,       false);
        aimLagModeSelector();
        rowDivider();
        switchRow("ESP Line",     Prefs.KEY_ESP_LINE,       true);  // on by default
        switchRow("ESP Box",      Prefs.KEY_ESP_BOXES,      true);

        // ── Touch wiring ─────────────────────────────────────────────────────
        View.OnTouchListener drag = new HeaderDragListener();
        header.setOnTouchListener(drag);
        titleView.setOnTouchListener(drag);
        logoView.setOnTouchListener(drag);
        minimizeView.setOnClickListener(v -> setMinimized(true));
        closeView.setOnClickListener(v -> exitEverything());

        // ── Window params ────────────────────────────────────────────────────
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        params = new WindowManager.LayoutParams(panelWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = clamp(dp(12), 0, Math.max(0, size[0] - panelWidth));
        params.y = clamp(dp(72), 0, Math.max(0, size[1] - maxPanelHeight));
        try {
            wm.addView(root, params);
            added = true;
        } catch (Throwable ignored) {
            added  = false;
            root   = null;
            params = null;
        }
    }

    public void hide() {
        if (root != null && added) {
            try { wm.removeView(root); } catch (Throwable ignored) { }
        }
        added   = false;
        root    = null;
        params  = null;
        content = null;
        scroll  = null;
    }

    public boolean isShown() { return added; }

    // ─────────────────────────────────────────────────────────────────────────
    // Controls
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One row: label on the left, custom pill toggle on the right.
     * Uses bg_switch_custom_on / bg_switch_custom_off drawables — same as main app.
     */
    private void switchRow(String label, String key, boolean defaultOn) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Generous vertical padding so rows don't stack against each other
        row.setPadding(dp(12), dp(8), dp(10), dp(8));
        row.setBackground(round(SURFACE, dp(8), Color.TRANSPARENT));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin    = dp(4);
        rowLp.bottomMargin = dp(2);
        content.addView(row, rowLp);

        // Label
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Custom pill toggle — TextView with on/off drawable background
        // 46×24dp, same contract as cb_* toggles in the main app
        final boolean[] state = { prefs.getBoolean(key, defaultOn) };
        TextView toggle = new TextView(context);
        toggle.setBackground(context.getResources().getDrawable(
                state[0] ? R.drawable.bg_switch_custom_on : R.drawable.bg_switch_custom_off));

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(dp(46), dp(26));
        tLp.leftMargin = dp(8);
        row.addView(toggle, tLp);

        // Click anywhere on the row to flip
        View.OnClickListener flip = v -> {
            state[0] = !state[0];
            prefs.edit().putBoolean(key, state[0]).apply();
            toggle.setBackground(context.getResources().getDrawable(
                    state[0] ? R.drawable.bg_switch_custom_on : R.drawable.bg_switch_custom_off));
            broadcaster.send();
        };
        row.setOnClickListener(flip);
        toggle.setOnClickListener(flip);
    }

    /**
     * Fire / Scope / Both pill selector — same layout as screenshot.
     */
    private void aimLagModeSelector() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(6), dp(10), dp(10));
        row.setBackground(round(SURFACE, dp(0), Color.TRANSPARENT));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        content.addView(row, rowLp);

        final String[] modes  = {"fire",  "scope",  "both"};
        final String[] labels = {"Fire",  "Scope",  "Both"};
        final TextView[] btns = new TextView[3];

        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            TextView b = new TextView(context);
            b.setText(labels[i]);
            b.setTextSize(11f);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            if (i > 0) bp.leftMargin = dp(6);
            btns[i] = b;
            row.addView(b, bp);
            b.setOnClickListener(v -> {
                prefs.edit().putString(Prefs.KEY_FREEZE_MODE, mode).apply();
                paintModeSelector(btns, modes);
                broadcaster.send();
            });
        }
        paintModeSelector(btns, modes);
    }

    private void paintModeSelector(TextView[] btns, String[] modes) {
        String cur = prefs.getString(Prefs.KEY_FREEZE_MODE, "fire");
        for (int i = 0; i < btns.length; i++) {
            boolean sel = modes[i].equals(cur);
            btns[i].setTextColor(sel ? BLACK : GRAY_MID);
            btns[i].setBackground(round(
                    sel ? WHITE            : SURFACE_2,  dp(8),
                    sel ? WHITE            : BORDER_HI));
        }
    }

    private void rowDivider() {
        View div = new View(context);
        div.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin  = dp(12);
        lp.rightMargin = dp(10);
        lp.topMargin   = dp(6);
        lp.bottomMargin = dp(6);
        content.addView(div, lp);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle / window management
    // ─────────────────────────────────────────────────────────────────────────

    private void exitEverything() {
        hide();
        try {
            context.stopService(new android.content.Intent(context,
                    com.ff.zesty.proxy.FloatingOverlayService.class));
            android.content.Intent stop = new android.content.Intent(context,
                    com.ff.zesty.proxy.PacketVpnService.class);
            stop.setAction(com.ff.zesty.proxy.config.VpnActions.ACTION_STOP);
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(stop);
                else                             context.startService(stop);
            } catch (Throwable ignored) { }
            android.content.Intent close = new android.content.Intent(context,
                    com.ff.zesty.proxy.MainActivity.class);
            close.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            close.putExtra("nepdon_exit_app", true);
            context.startActivity(close);
        } catch (Throwable ignored) { }
    }

    private void setMinimized(boolean value) {
        minimized = value;
        if (scroll       != null) scroll.setVisibility(value ? View.GONE : View.VISIBLE);
        if (header       != null) header.setVisibility(value ? View.GONE : View.VISIBLE);
        if (logoView     != null) logoView.setVisibility(value ? View.VISIBLE : View.GONE);
        if (titleView    != null) titleView.setVisibility(value ? View.GONE : View.VISIBLE);
        if (minimizeView != null) minimizeView.setVisibility(value ? View.GONE : View.VISIBLE);
        if (closeView    != null) closeView.setVisibility(value ? View.GONE : View.VISIBLE);
        if (header != null) {
            header.setPadding(
                    value ? 0 : dp(6), value ? 0 : dp(2),
                    value ? 0 : dp(2), value ? 0 : dp(2));
            header.setBackground(value
                    ? round(Color.TRANSPARENT, dp(26), Color.TRANSPARENT)
                    : round(SURFACE_2, dp(10), BORDER_HI));
        }
        if (logoView != null) logoView.requestLayout();
        if (root != null) {
            root.setPadding(
                    value ? 0 : dp(6), value ? 0 : dp(6),
                    value ? 0 : dp(6), value ? 0 : dp(6));
            root.setBackground(value
                    ? round(Color.TRANSPARENT, dp(26), Color.TRANSPARENT)
                    : panelBg());
            WindowManager.LayoutParams p = params;
            if (p != null) {
                p.width  = value ? dp(52) : panelWidth;
                p.height = WindowManager.LayoutParams.WRAP_CONTENT;
                try { wm.updateViewLayout(root, p); } catch (Throwable ignored) { }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private TextView headerBtn(String text, String desc, int color) {
        TextView b = new TextView(context);
        b.setText(text);
        b.setContentDescription(desc);
        b.setTextColor(color);
        b.setTextSize(22f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(Color.TRANSPARENT, dp(8), Color.TRANSPARENT));
        return b;
    }

    private GradientDrawable panelBg() {
        return round(BG, dp(14), BORDER_HI);
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT)
            d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag listener
    // ─────────────────────────────────────────────────────────────────────────

    private final class HeaderDragListener implements View.OnTouchListener {
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (params == null || root == null) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = params.x; downY = params.y;
                    rawDownX = e.getRawX(); rawDownY = e.getRawY();
                    dragMoved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int[] size = displaySize();
                    if (Math.abs(e.getRawX() - rawDownX) > dp(5)
                            || Math.abs(e.getRawY() - rawDownY) > dp(5))
                        dragMoved = true;
                    params.x = clamp(downX + Math.round(e.getRawX() - rawDownX),
                            0, Math.max(0, size[0] - panelWidth));
                    params.y = clamp(downY + Math.round(e.getRawY() - rawDownY),
                            0, Math.max(0, size[1] - dp(60)));
                    try { wm.updateViewLayout(root, params); }
                    catch (Throwable ignored) { }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (e.getActionMasked() == MotionEvent.ACTION_UP
                            && v == logoView && !dragMoved)
                        setMinimized(!minimized);
                    return true;
                default: return true;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private int[] displaySize() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        try { wm.getDefaultDisplay().getRealMetrics(dm); }
        catch (Throwable ignored) {
            context.getResources().getDisplayMetrics().setTo(dm);
        }
        return new int[]{ Math.max(1, dm.widthPixels), Math.max(1, dm.heightPixels) };
    }

    private int dp(int v) {
        return Math.max(1, Math.round(
                v * context.getResources().getDisplayMetrics().density));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
