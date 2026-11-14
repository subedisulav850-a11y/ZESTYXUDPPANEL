package com.ff.zesty.proxy.ui.settings;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ff.zesty.proxy.config.EspConfig;
import com.ff.zesty.proxy.config.Prefs;
import com.ff.zesty.proxy.R;

/**
 * Owns every {@code findViewById} for the settings screen plus the toggle and
 * seekbar wiring. Keeps the raw view references out of {@code MainActivity}.
 */
public final class UiBindings {

    private final Activity activity;
    private final AppearanceBroadcaster broadcaster;

    private final TextView cbFloatGhost, cbFloatFreeze, cbFloatTele;
    private final TextView tvFloatGhostStatus, tvFloatFreezeStatus, tvFloatTeleStatus;
    private final TextView cbDevMode, tvDevModeStatus;
    private TextView cbEsp, tvEspStatus, cbRadar, tvRadarStatus, cbEspLine, tvEspLineStatus, cbEspDebug, tvEspDebugStatus, cbEspTune, tvEspTuneStatus, cbAimSetup, tvAimSetupStatus;
    // FreezeController toggles (replaced aim/zoom button rows)
    private TextView cbAimLag, tvAimLagStatus, cbAutoFreeze, tvAutoFreezeStatus, cbScopeFreeze, tvScopeFreezeStatus;
    // Fire/Scope mode selector
    private TextView cbFreezeModeGroup;
    private TextView cbEspSkeleton, tvEspSkeletonStatus, cbEspDistText, tvEspDistTextStatus, cbEspNameText, tvEspNameTextStatus, cbEspHpText, tvEspHpTextStatus;
    private final TextView tvFloatSizeValue, tvFloatAlphaValue;
    private final SeekBar seekFloatSize, seekFloatAlpha;
    private final ViewGroup mainContent;
    private final View groupTeleOptions;

    // ---- ESP box colour picker ----
    private View btnColorBlack, btnColorWhite, btnColorRed;
    private View btnColorYellow, btnColorBlue, btnColorGreen, btnColorPurple;
    private TextView tvEspColorLabel;
    // ---- Per-feature colour pickers ----
    private View btnSkeletonColorBlack, btnSkeletonColorWhite, btnSkeletonColorRed, btnSkeletonColorYellow, btnSkeletonColorBlue, btnSkeletonColorGreen, btnSkeletonColorPurple;
    private TextView tvSkeletonColorLabel;
    private View btnDistColorBlack, btnDistColorWhite, btnDistColorRed, btnDistColorYellow, btnDistColorBlue, btnDistColorGreen, btnDistColorPurple;
    private TextView tvDistColorLabel;
    private View btnNameColorBlack, btnNameColorWhite, btnNameColorRed, btnNameColorYellow, btnNameColorBlue, btnNameColorGreen, btnNameColorPurple;
    private TextView tvNameColorLabel;
    private View btnHpColorBlack, btnHpColorWhite, btnHpColorRed, btnHpColorYellow, btnHpColorBlue, btnHpColorGreen, btnHpColorPurple;
    private TextView tvHpColorLabel;

    /** Packed ARGB values for each colour swatch (stroke alpha 235). */
    private static final int[] COLOR_VALUES = {
            Color.argb(235,   0,   0,   0),  // BLACK
            Color.argb(235, 255, 255, 255),  // WHITE
            Color.argb(235, 255,  64,  64),  // RED
            Color.argb(235, 255, 220,   0),  // YELLOW
            Color.argb(235,  60, 130, 255),  // BLUE
            Color.argb(235,  60, 220,  90),  // GREEN
            Color.argb(235, 180,  80, 255),  // PURPLE
    };
    private static final String[] COLOR_NAMES = {
            "Black", "White", "Red", "Yellow", "Blue", "Green", "Purple"
    };

    public UiBindings(Activity activity, AppearanceBroadcaster broadcaster) {
        this.activity = activity;
        this.broadcaster = broadcaster;

        cbFloatGhost      = (TextView) activity.findViewById(R.id.cb_float_ghost);
        cbFloatFreeze     = (TextView) activity.findViewById(R.id.cb_float_freeze);
        cbFloatTele       = (TextView) activity.findViewById(R.id.cb_float_tele);
        tvFloatGhostStatus   = (TextView) activity.findViewById(R.id.tv_float_ghost_status);
        tvFloatFreezeStatus  = (TextView) activity.findViewById(R.id.tv_float_freeze_status);
        tvFloatTeleStatus    = (TextView) activity.findViewById(R.id.tv_float_tele_status);
        cbDevMode            = (TextView) activity.findViewById(R.id.cb_dev_mode);
        tvDevModeStatus      = (TextView) activity.findViewById(R.id.tv_dev_mode_status);

        tvFloatSizeValue = (TextView) activity.findViewById(R.id.tv_float_size_value);
        tvFloatAlphaValue = (TextView) activity.findViewById(R.id.tv_float_alpha_value);
        seekFloatSize = (SeekBar) activity.findViewById(R.id.seek_float_size);
        seekFloatAlpha = (SeekBar) activity.findViewById(R.id.seek_float_alpha);

        cbEsp = (TextView) activity.findViewById(R.id.cb_esp);
        tvEspStatus = (TextView) activity.findViewById(R.id.tv_esp_status);
        cbRadar = (TextView) activity.findViewById(R.id.cb_radar);
        tvRadarStatus = (TextView) activity.findViewById(R.id.tv_radar_status);
        cbEspLine = (TextView) activity.findViewById(R.id.cb_esp_line);
        tvEspLineStatus = (TextView) activity.findViewById(R.id.tv_esp_line_status);
        cbEspSkeleton = (TextView) activity.findViewById(R.id.cb_esp_skeleton);
        tvEspSkeletonStatus = (TextView) activity.findViewById(R.id.tv_esp_skeleton_status);
        cbEspDistText = (TextView) activity.findViewById(R.id.cb_esp_dist_text);
        tvEspDistTextStatus = (TextView) activity.findViewById(R.id.tv_esp_dist_text_status);
        cbEspNameText = (TextView) activity.findViewById(R.id.cb_esp_name_text);
        tvEspNameTextStatus = (TextView) activity.findViewById(R.id.tv_esp_name_text_status);
        cbEspHpText = (TextView) activity.findViewById(R.id.cb_esp_hp_text);
        tvEspHpTextStatus = (TextView) activity.findViewById(R.id.tv_esp_hp_text_status);
        cbEspDebug = (TextView) activity.findViewById(R.id.cb_espdebug);
        tvEspDebugStatus = (TextView) activity.findViewById(R.id.tv_espdebug_status);
        cbEspTune = (TextView) activity.findViewById(R.id.cb_esptune);
        tvEspTuneStatus = (TextView) activity.findViewById(R.id.tv_esptune_status);
        cbAimSetup = (TextView) activity.findViewById(R.id.cb_aimsetup);
        tvAimSetupStatus = (TextView) activity.findViewById(R.id.tv_aimsetup_status);
        // FreezeController rows
        cbAimLag       = (TextView) activity.findViewById(R.id.cb_aim_lag);
        tvAimLagStatus = (TextView) activity.findViewById(R.id.tv_aim_lag_status);
        cbAutoFreeze       = (TextView) activity.findViewById(R.id.cb_auto_freeze);
        tvAutoFreezeStatus = (TextView) activity.findViewById(R.id.tv_auto_freeze_status);
        cbScopeFreeze       = (TextView) activity.findViewById(R.id.cb_scope_freeze);
        tvScopeFreezeStatus = (TextView) activity.findViewById(R.id.tv_scope_freeze_status);

        mainContent = (ViewGroup) activity.findViewById(R.id.main_content);
        groupTeleOptions = activity.findViewById(R.id.group_tele_options);

        // Colour picker — box/line
        btnColorBlack  = activity.findViewById(R.id.btn_color_black);
        btnColorWhite  = activity.findViewById(R.id.btn_color_white);
        btnColorRed    = activity.findViewById(R.id.btn_color_red);
        btnColorYellow = activity.findViewById(R.id.btn_color_yellow);
        btnColorBlue   = activity.findViewById(R.id.btn_color_blue);
        btnColorGreen  = activity.findViewById(R.id.btn_color_green);
        btnColorPurple = activity.findViewById(R.id.btn_color_purple);
        tvEspColorLabel = (TextView) activity.findViewById(R.id.tv_esp_color_label);
        // Colour picker — skeleton
        btnSkeletonColorBlack  = activity.findViewById(R.id.btn_skeleton_color_black);
        btnSkeletonColorWhite  = activity.findViewById(R.id.btn_skeleton_color_white);
        btnSkeletonColorRed    = activity.findViewById(R.id.btn_skeleton_color_red);
        btnSkeletonColorYellow = activity.findViewById(R.id.btn_skeleton_color_yellow);
        btnSkeletonColorBlue   = activity.findViewById(R.id.btn_skeleton_color_blue);
        btnSkeletonColorGreen  = activity.findViewById(R.id.btn_skeleton_color_green);
        btnSkeletonColorPurple = activity.findViewById(R.id.btn_skeleton_color_purple);
        tvSkeletonColorLabel   = (TextView) activity.findViewById(R.id.tv_skeleton_color_label);
        // Colour picker — distance
        btnDistColorBlack  = activity.findViewById(R.id.btn_dist_color_black);
        btnDistColorWhite  = activity.findViewById(R.id.btn_dist_color_white);
        btnDistColorRed    = activity.findViewById(R.id.btn_dist_color_red);
        btnDistColorYellow = activity.findViewById(R.id.btn_dist_color_yellow);
        btnDistColorBlue   = activity.findViewById(R.id.btn_dist_color_blue);
        btnDistColorGreen  = activity.findViewById(R.id.btn_dist_color_green);
        btnDistColorPurple = activity.findViewById(R.id.btn_dist_color_purple);
        tvDistColorLabel   = (TextView) activity.findViewById(R.id.tv_dist_color_label);
        // Colour picker — name
        btnNameColorBlack  = activity.findViewById(R.id.btn_name_color_black);
        btnNameColorWhite  = activity.findViewById(R.id.btn_name_color_white);
        btnNameColorRed    = activity.findViewById(R.id.btn_name_color_red);
        btnNameColorYellow = activity.findViewById(R.id.btn_name_color_yellow);
        btnNameColorBlue   = activity.findViewById(R.id.btn_name_color_blue);
        btnNameColorGreen  = activity.findViewById(R.id.btn_name_color_green);
        btnNameColorPurple = activity.findViewById(R.id.btn_name_color_purple);
        tvNameColorLabel   = (TextView) activity.findViewById(R.id.tv_name_color_label);
        // Colour picker — HP
        btnHpColorBlack  = activity.findViewById(R.id.btn_hp_color_black);
        btnHpColorWhite  = activity.findViewById(R.id.btn_hp_color_white);
        btnHpColorRed    = activity.findViewById(R.id.btn_hp_color_red);
        btnHpColorYellow = activity.findViewById(R.id.btn_hp_color_yellow);
        btnHpColorBlue   = activity.findViewById(R.id.btn_hp_color_blue);
        btnHpColorGreen  = activity.findViewById(R.id.btn_hp_color_green);
        btnHpColorPurple = activity.findViewById(R.id.btn_hp_color_purple);
        tvHpColorLabel   = (TextView) activity.findViewById(R.id.tv_hp_color_label);
    }

    /** Wires every toggle and seekbar exactly as the original onCreate did. */
    public void setupAll() {
        setupFloatingToggle(cbFloatFreeze,  tvFloatFreezeStatus,  Prefs.KEY_FREEZE);
        setupFloatingToggle(cbFloatGhost,   tvFloatGhostStatus,   Prefs.KEY_GHOST);
        setupFloatingToggle(cbFloatTele,    tvFloatTeleStatus,    Prefs.KEY_TELE);
        setupFloatSizeSeek();
        setupFloatAlphaSeek();
        setupFloatingToggle(cbDevMode, tvDevModeStatus, Prefs.KEY_DEV_MODE);
        setupFloatingToggle(cbEsp,          tvEspStatus,          Prefs.KEY_ESP_BOXES);
        setupFloatingToggle(cbRadar,        tvRadarStatus,        Prefs.KEY_ESP_RADAR);
        setupFloatingToggle(cbEspLine,      tvEspLineStatus,      Prefs.KEY_ESP_LINE);
        setupFloatingToggle(cbEspSkeleton,  tvEspSkeletonStatus,  Prefs.KEY_ESP_SKELETON);
        setupFloatingToggle(cbEspDistText,  tvEspDistTextStatus,  Prefs.KEY_ESP_DIST_TEXT);
        setupFloatingToggle(cbEspNameText,  tvEspNameTextStatus,  Prefs.KEY_ESP_NAME_TEXT);
        setupFloatingToggle(cbEspHpText,    tvEspHpTextStatus,    Prefs.KEY_ESP_HP_TEXT);
        setupFloatingToggle(cbEspDebug,     tvEspDebugStatus,     Prefs.KEY_ESP_DEBUG);
        setupFloatingToggle(cbEspTune,  tvEspTuneStatus,  Prefs.KEY_ESP_TUNE);
        // FreezeController toggles
        setupFloatingToggle(cbAimLag,      tvAimLagStatus,      Prefs.KEY_AIM_LAG);
        setupFloatingToggle(cbAutoFreeze,  tvAutoFreezeStatus,  Prefs.KEY_AUTO_FREEZE);
        setupFloatingToggle(cbScopeFreeze, tvScopeFreezeStatus, Prefs.KEY_SCOPE_FREEZE);
        setupFreezeModeSelector();
        setupColorPicker();
        setupSkeletonColorPicker();
        setupDistColorPicker();
        setupNameColorPicker();
        setupHpColorPicker();
        updateConditionalGroups(false);
        animateMainEntrance();
    }

    // ---- Colour pickers ----

    /** Generic helper: wires 7 swatches to a pref key + EspConfig field, restores saved value. */
    private interface ColorApplier { void apply(int argb); }
    private interface LabelUpdater { void update(int argb); }

    private void wireColorPicker(View[] swatches, TextView label,
                                 String prefKey, int defaultArgb,
                                 ColorApplier applier) {
        int saved = Prefs.get(activity).getInt(prefKey, defaultArgb);
        applier.apply(saved);
        setColorLabel(label, saved);
        for (int i = 0; i < swatches.length; i++) {
            final View sw = swatches[i];
            if (sw == null) continue;
            final int cv = COLOR_VALUES[i];
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Prefs.get(activity).edit().putInt(prefKey, cv).commit();
                    applier.apply(cv);
                    setColorLabel(label, cv);
                    broadcaster.send();
                    v.animate().cancel();
                    v.setScaleX(0.85f); v.setScaleY(0.85f);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start();
                }
            });
        }
    }

    private void setColorLabel(TextView tv, int argb) {
        if (tv == null) return;
        String name = "Custom";
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == argb) { name = COLOR_NAMES[i]; break; }
        }
        tv.setText("Selected: " + name);
    }

    private void setupColorPicker() {
        wireColorPicker(
            new View[]{btnColorBlack, btnColorWhite, btnColorRed, btnColorYellow, btnColorBlue, btnColorGreen, btnColorPurple},
            tvEspColorLabel, Prefs.KEY_ESP_BOX_COLOR, Color.argb(235, 255, 64, 64),
            new ColorApplier() { @Override public void apply(int a) { EspConfig.BOX_COLOR_ARGB = a; } });
    }

    private void setupSkeletonColorPicker() {
        wireColorPicker(
            new View[]{btnSkeletonColorBlack, btnSkeletonColorWhite, btnSkeletonColorRed, btnSkeletonColorYellow, btnSkeletonColorBlue, btnSkeletonColorGreen, btnSkeletonColorPurple},
            tvSkeletonColorLabel, Prefs.KEY_ESP_SKELETON_COLOR, Color.argb(235, 255, 255, 255),
            new ColorApplier() { @Override public void apply(int a) { EspConfig.SKELETON_COLOR_ARGB = a; } });
    }

    private void setupDistColorPicker() {
        wireColorPicker(
            new View[]{btnDistColorBlack, btnDistColorWhite, btnDistColorRed, btnDistColorYellow, btnDistColorBlue, btnDistColorGreen, btnDistColorPurple},
            tvDistColorLabel, Prefs.KEY_ESP_DIST_COLOR, Color.argb(235, 255, 255, 255),
            new ColorApplier() { @Override public void apply(int a) { EspConfig.DIST_COLOR_ARGB = a; } });
    }

    private void setupNameColorPicker() {
        wireColorPicker(
            new View[]{btnNameColorBlack, btnNameColorWhite, btnNameColorRed, btnNameColorYellow, btnNameColorBlue, btnNameColorGreen, btnNameColorPurple},
            tvNameColorLabel, Prefs.KEY_ESP_NAME_COLOR, Color.argb(235, 255, 255, 255),
            new ColorApplier() { @Override public void apply(int a) { EspConfig.NAME_COLOR_ARGB = a; } });
    }

    private void setupHpColorPicker() {
        wireColorPicker(
            new View[]{btnHpColorBlack, btnHpColorWhite, btnHpColorRed, btnHpColorYellow, btnHpColorBlue, btnHpColorGreen, btnHpColorPurple},
            tvHpColorLabel, Prefs.KEY_ESP_HP_COLOR, Color.argb(235, 255, 255, 255),
            new ColorApplier() { @Override public void apply(int a) { EspConfig.HP_COLOR_ARGB = a; } });
    }

    // ---- Seekbars ----

    private void setupFloatSizeSeek() {
        bindSeek(seekFloatSize, tvFloatSizeValue,
                AppearanceBroadcaster.FLOAT_SIZE_MIN, AppearanceBroadcaster.FLOAT_SIZE_MAX,
                Prefs.KEY_FLOAT_SIZE, "%", broadcaster.getFloatSizePercent(), null);
    }

    private void setupFloatAlphaSeek() {
        bindSeek(seekFloatAlpha, tvFloatAlphaValue,
                AppearanceBroadcaster.FLOAT_ALPHA_MIN, AppearanceBroadcaster.FLOAT_ALPHA_MAX,
                Prefs.KEY_FLOAT_ALPHA, "%", broadcaster.getFloatAlphaPercent(), null);
    }

    private void bindSeek(final SeekBar seek, final TextView value, final int min, final int max,
                          final String key, final String suffix, final int saved, final Runnable onChanged) {
        if (seek == null || value == null) return;
        seek.setMax(max - min);
        seek.setProgress(saved - min);
        value.setText(saved + suffix);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                Prefs.get(activity).edit().putInt(key, v).commit();
                value.setText(v + suffix);
                if (onChanged != null) onChanged.run();
                broadcaster.send();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    // ---- Toggles ----

    private void setupFloatingToggle(final TextView toggle, final TextView status, final String key) {
        if (toggle == null) return;
        // Default-off keys. These must match AppearanceBroadcaster and the
        // service, or the switch shows ON for something that is actually off
        // on a fresh install (which is what KEY_DEV_MODE did).
        boolean defaultOn = !Prefs.KEY_DEV_MODE.equals(key)
                && !Prefs.KEY_ESP_TUNE.equals(key)
                && !Prefs.KEY_AIM_SETUP.equals(key)
                && !Prefs.KEY_ESP_DEBUG.equals(key)
                && !Prefs.KEY_TELE.equals(key)
                && !Prefs.KEY_ESP_LINE.equals(key)
                // FreezeController rows all default off
                && !Prefs.KEY_AIM_LAG.equals(key)
                && !Prefs.KEY_AUTO_FREEZE.equals(key)
                && !Prefs.KEY_SCOPE_FREEZE.equals(key);
        boolean isOn = Prefs.get(activity).getBoolean(key, defaultOn);
        applyToggleState(toggle, status, isOn);
        View.OnClickListener flip = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean current = toggle.getTag() != null && (Boolean) toggle.getTag();
                boolean next = !current;
                applyToggleState(toggle, status, next);
                animateToggle(toggle);
                Prefs.get(activity).edit().putBoolean(key, next).commit();
                broadcaster.send();
                updateConditionalGroups(true);
            }
        };
        toggle.setOnClickListener(flip);
        // THE WHOLE ROW IS THE TARGET, not just the switch.
        //
        // Measured on device: the switch graphic is 52x28dp -- 28dp is 58% of
        // the 48dp floor this design set itself -- and the row was not
        // clickable at all, so ~314dp of row width was dead and the only live
        // area sat at the far right, the hardest place to reach with a left
        // thumb. The row is the parent LinearLayout; the layout gives it
        // minHeight 56dp and a ripple, and the switch carries
        // duplicateParentState so it lights with the row.
        View row = (toggle.getParent() instanceof View) ? (View) toggle.getParent() : null;
        if (row != null) row.setOnClickListener(flip);
    }

    private void applyToggleState(TextView toggle, TextView status, boolean isOn) {
        if (toggle == null) return;
        toggle.setTag(isOn);
        toggle.setText("");
        toggle.setBackgroundResource(isOn ? R.drawable.bg_switch_custom_on : R.drawable.bg_switch_custom_off);
        // THE SUBLABEL IS NOT A STATE ECHO.
        //
        // It used to be overwritten with "Showing"/"Hidden" on every row,
        // which (a) restated what the switch already says, costing ~15dp of
        // scroll per row for nothing, and (b) was simply wrong for anything
        // non-visual: "Aim assist - Hidden", "Teleport - Hidden",
        // "Developer mode - Hidden". Aim assist is not hidden; it is off.
        //
        // The layout now sets a permanent description of what each setting
        // DOES, and nothing here touches it.
    }

    /**
     * Fire / Scope / Both selector for auto-freeze.
     *
     * Wires three RadioButton-like rows identified by id:
     *   rb_freeze_fire, rb_freeze_scope, rb_freeze_both
     *
     * Each press saves KEY_FREEZE_MODE and then calls broadcaster.send()
     * so FloatingOverlayService picks it up in the same broadcast it already
     * handles for all other appearance changes.
     */
    private void setupFreezeModeSelector() {
        final android.widget.RadioGroup rg =
                (android.widget.RadioGroup) activity.findViewById(R.id.rg_freeze_mode);
        if (rg == null) return;

        String saved = Prefs.get(activity).getString(Prefs.KEY_FREEZE_MODE, "fire");
        int checkId;
        switch (saved) {
            case "scope": checkId = R.id.rb_freeze_scope; break;
            case "both":  checkId = R.id.rb_freeze_both;  break;
            default:      checkId = R.id.rb_freeze_fire;  break;
        }
        rg.check(checkId);

        rg.setOnCheckedChangeListener(new android.widget.RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.RadioGroup group, int checkedId) {
                String mode;
                if (checkedId == R.id.rb_freeze_scope) {
                    mode = "scope";
                } else if (checkedId == R.id.rb_freeze_both) {
                    mode = "both";
                } else {
                    mode = "fire";
                }
                Prefs.get(activity).edit().putString(Prefs.KEY_FREEZE_MODE, mode).commit();
                broadcaster.send();
            }
        });
    }

    private void animateToggle(final TextView toggle) {
        if (toggle == null) return;
        toggle.animate().cancel();
        toggle.setScaleX(0.96f);
        toggle.setScaleY(0.96f);
        toggle.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120L)
                .start();
    }

    // ---- Conditional groups + entrance ----

    private void updateConditionalGroups(boolean animate) {
        // The mask-bar group was the only conditional group and it is gone
        // (the renderer it fed, overlay/MaskBar, was deleted long ago and the
        // two sliders kept rendering and persisting values nothing read).
        // groupTeleOptions is still bound but has never been shown or hidden.
    }

    private void setOptionalGroupVisible(final View group, boolean visible, boolean animate) {
        if (group == null) return;
        if (!animate) {
            group.setVisibility(visible ? View.VISIBLE : View.GONE);
            group.setAlpha(visible ? 1f : 0f);
            group.setTranslationY(0f);
            return;
        }
        group.animate().cancel();
        if (visible) {
            group.setVisibility(View.VISIBLE);
            group.setAlpha(0f);
            group.setTranslationY(18f);
            group.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180L)
                    .start();
        } else {
            group.animate()
                    .alpha(0f)
                    .translationY(-10f)
                    .setDuration(130L)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            group.setVisibility(View.GONE);
                            group.setTranslationY(0f);
                        }
                    })
                    .start();
        }
    }


    private void animateMainEntrance() {
        if (mainContent == null) return;
        for (int i = 0; i < mainContent.getChildCount(); i++) {
            View child = mainContent.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(28f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(45L * i)
                    .setDuration(260L)
                    .start();
        }
    }
}
