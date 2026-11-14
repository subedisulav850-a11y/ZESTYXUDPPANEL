package com.ff.zesty.proxy.config;

/**
 * Single source of truth for every Intent action and extra key used to talk to
 * {@link PacketVpnService} and {@link FloatingOverlayService}. Keeping these in
 * one place (instead of duplicated string literals across three services) is
 * the contract that lets the transport core, the filter engine, and the UI evolve
 * independently.
 */
public final class VpnActions {

    private VpnActions() { }

    // ---- Service control actions (sent to PacketVpnService) ----
    public static final String ACTION_ACTIVATE   = "com.ff.zesty.proxy.ACTIVATE";
    public static final String ACTION_DEACTIVATE = "com.ff.zesty.proxy.DEACTIVATE";
    public static final String ACTION_FREEZE     = "com.ff.zesty.proxy.FREEZE";
    public static final String ACTION_GHOST      = "com.ff.zesty.proxy.GHOST";
    public static final String ACTION_JITTER     = "com.ff.zesty.proxy.JITTER";
    /** Toggles the aim / hit-zone rewrite (outbound Cmd 106 Body/Limb -> Head). */
    public static final String ACTION_AIM        = "com.ff.zesty.proxy.AIM";
    public static final String ACTION_SET_APP    = "com.ff.zesty.proxy.SET_APP";
    public static final String ACTION_STATS      = "com.ff.zesty.proxy.STATS";
    public static final String ACTION_STOP       = "com.ff.zesty.proxy.STOP";

    // ---- Packet capture (dev) ----
    public static final String ACTION_CAPTURE_START = "com.ff.zesty.proxy.CAPTURE_START";
    public static final String ACTION_CAPTURE_STOP  = "com.ff.zesty.proxy.CAPTURE_STOP";
    public static final String ACTION_CAPTURE_DUMP  = "com.ff.zesty.proxy.CAPTURE_DUMP";
    public static final String ACTION_TELEMETRY_DUMP = "com.ff.zesty.proxy.TELEMETRY_DUMP";

    // ---- Broadcast action (MainActivity -> services) ----
    public static final String ACTION_APPLY_FLOAT_APPEARANCE = "com.ff.zesty.proxy.APPLY_FLOAT_APPEARANCE";

    // ---- State broadcast (engine -> UI, on every change + stats tick) ----
    public static final String ACTION_STATE = "com.ff.zesty.proxy.STATE";

    // ---- Extra keys ----
    public static final String EXTRA_BYTES_IN        = "bytes_in";
    public static final String EXTRA_BYTES_OUT       = "bytes_out";
    public static final String EXTRA_FILTER_LOG      = "filter_log";
    public static final String EXTRA_FILTER_ENABLED  = "filter_enabled";
    public static final String EXTRA_LOSS            = "loss";
    public static final String EXTRA_MODE            = "mode";
    public static final String EXTRA_PING            = "ping";
    public static final String EXTRA_PACKAGE         = "PACKAGE";
    public static final String EXTRA_PACKAGE_ALT     = "PKG";
    public static final String EXTRA_PACKAGES        = "PACKAGES";

    public static final String EXTRA_DEV_MODE        = "dev_mode";
    public static final String EXTRA_TELE_REPLAY_DELAY = "tele_replay_delay_ms";
    public static final String EXTRA_LOBBY_ROUTE     = "vpn_lobby_route";

    public static final String EXTRA_FLOAT_SIZE              = "float_size_percent";
    public static final String EXTRA_FLOAT_ALPHA             = "float_alpha_percent";

    // ESP switches ride the appearance broadcast so they apply without an
    // overlay restart.
    public static final String EXTRA_ESP_BOXES               = "esp_boxes";
    public static final String EXTRA_ESP_RADAR               = "esp_radar";
    public static final String EXTRA_ESP_LINE                = "esp_line";
    public static final String EXTRA_ESP_DEBUG               = "esp_debug";
    public static final String EXTRA_ESP_TUNE                = "esp_tune_panel";
    public static final String EXTRA_ESP_SKELETON            = "esp_skeleton";
    public static final String EXTRA_ESP_DIST_TEXT           = "esp_dist_text";
    public static final String EXTRA_ESP_NAME_TEXT           = "esp_name_text";
    public static final String EXTRA_ESP_HP_TEXT             = "esp_hp_text";
    public static final String EXTRA_ESP_WATERMARK           = "esp_watermark";
    public static final String EXTRA_AIM_SETUP               = "aim_setup_panel";
    public static final String EXTRA_AIM_ENABLED             = "aim_enabled";

    /** AI Aimbot head-lock toggle. */
    public static final String EXTRA_AIMBOT                  = "aimbot_enabled";

    /** Speed boost: scale outbound velocity by 1.1x (10% movement increase). */
    public static final String EXTRA_SPEED_BOOST             = "speed_boost_enabled";

    /** Buff speed: inject Cmd 170 BuffBehavior packet (ID 333, buff 988) every 2 s. */
    public static final String EXTRA_BUFF_SPEED              = "buff_speed_enabled";

    // ---- FreezeController ----
    /** Master aim-lag tracking toggle. */
    public static final String EXTRA_AIM_LAG        = "aim_lag_enabled";
    /** Auto-freeze on fire toggle. */
    public static final String EXTRA_AUTO_FREEZE     = "auto_freeze_fire";
    /** Auto-freeze on scope toggle. */
    public static final String EXTRA_SCOPE_FREEZE    = "scope_freeze_enabled";
    /** Freeze trigger mode: "fire", "scope", or "both". */
    public static final String EXTRA_FREEZE_MODE     = "freeze_trigger_mode";

    public static final String EXTRA_SHOW_GHOST      = "SHOW_GHOST";
    public static final String EXTRA_SHOW_FREEZE     = "SHOW_FREEZE";
    public static final String EXTRA_SHOW_TELE       = "SHOW_TELE";

    /** Packed ARGB int for the enemy ESP box stroke colour. */
    public static final String EXTRA_ESP_BOX_COLOR   = "esp_box_color";

    public static final String EXTRA_CAPTURE_HEX_LIMIT = "capture_hex_limit";
    public static final String EXTRA_CAPTURE_MODE      = "capture_mode";
}
