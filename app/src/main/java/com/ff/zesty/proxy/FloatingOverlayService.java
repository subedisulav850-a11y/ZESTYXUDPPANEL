package com.ff.zesty.proxy;

import com.ff.zesty.proxy.ui.aim.AimConfig;
import com.ff.zesty.proxy.ui.aim.AimController;
import com.ff.zesty.proxy.ui.aim.Daemon;
import com.ff.zesty.proxy.freeze.FreezeController;

import com.ff.zesty.proxy.config.Prefs;
import com.ff.zesty.proxy.config.VpnActions;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.ff.zesty.proxy.ui.overlay.DevPopup;
import com.ff.zesty.proxy.ui.button.OverlayAnimations;
import com.ff.zesty.proxy.ui.button.OverlayButton;

public class FloatingOverlayService extends Service {

    private static final String CH_ID = "gbf";
    private static final int NOTIF_ID = 2;

    private boolean onFreeze = false;
    private boolean onGhost = false;
    private boolean onJitter = false;
    private boolean addedF = false;
    private boolean addedG = false;
    private boolean addedJ = false;
    // aim/zoom overlay buttons removed — replaced by FreezeController
    /**
     * Aim is held, and it is CLIENT-SIDE ONLY.
     *
     * <p>FREEZE / GHOST / TELE deliberately avoid optimistic flips: they send an
     * intent and wait for the engine's ACTION_STATE broadcast to repaint, because
     * the engine owns that state. Aim is different — the packet engine has no aim
     * state to be the truth about, it lives entirely in the renderer. So this is
     * flipped locally and painted immediately.</p>
     */
    private boolean onAim = false;
    /**
     * Capture state, kept for the status broadcast only.
     *
     * <p>THE BUTTON IS GONE. Capture is always-on: {@code VpnEngine} opens a
     * fresh dump on every engine start and the writer streams to disk in real
     * time. A button could therefore only ever turn OFF the recording the
     * diagnostics depend on -- and a dump that stops when you most need it is
     * worse than none.</p>
     */
    /** Reused by the title poll; never allocate on a repeating callback. */
    private final int[] roleScratch = new int[3];


    private TextView btnFreeze;
    private TextView btnGhost;
    private TextView btnJitter;
    // aim and zoom overlay buttons removed
    private WindowManager wm;
    private WindowManager.LayoutParams freezeParams;
    private WindowManager.LayoutParams ghostParams;
    private WindowManager.LayoutParams teleParams;
    private BroadcastReceiver rcv;
    private ToneGenerator toneGenerator;
    private Typeface floatingButtonFont;

    private OverlayButton button;
    private OverlayAnimations animations;
    private DevPopup devPopup;
    private com.ff.zesty.proxy.ui.overlay.EspOverlay espOverlay;
    private com.ff.zesty.proxy.ui.panel.EspSetupPanel espTunePanel;
    private com.ff.zesty.proxy.ui.panel.AimSetupPanel aimSetupPanel;
    /** Responsive in-game Aim + Overlay control surface. */
    private com.ff.zesty.proxy.ui.panel.FloatingControlPanel floatingControlPanel;
    /**
     * The full-screen layout editor. Owned here rather than by a panel so both
     * panels can open the same one and there is exactly one window to remove
     * when the service dies -- an editor left attached would hold every touch
     * on the device with nothing on screen to dismiss it.
     */
    private com.ff.zesty.proxy.ui.edit.LayoutEditor layoutEditor;

    private int floatAlphaPercent = 92;
    private int floatSizePercent = 100;

    private boolean showGhost = false;
    private boolean showFreeze = false;
    private boolean showTele = false;
    private boolean showDevMode = false;
    private boolean showEspTune = false;
    private boolean showAimSetup = false;
    // aim/zoom buttons removed; replaced by FreezeController toggles in MainActivity

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        doForeground();
        wm = (WindowManager) getSystemService("window");
        initTone();
        // assets/fonts/chudep.ttf was a ZERO-BYTE FILE, so this always threw
        // and every overlay button silently ran on the catch branch. The asset
        // is deleted and the fallback is now the actual, stated intent rather
        // than an accident that looked deliberate.
        floatingButtonFont = Typeface.DEFAULT_BOLD;

        button = new OverlayButton(this, wm, floatingButtonFont, new OverlayButton.ClickListener() {
            @Override
            public void onClick(int id) {
                click(id);
            }
        });
        // AIM is now a plain toggle (id 9), handled in click() like freeze —
        // not a hold. So no setHold() wiring; the button just flips the endless
        // test-swipe on and off.
        AimController.init();
        // Where the nvmux daemon binary is extracted to (see extractNativeLibs);
        // the injector spawns it from here via Shizuku when the swipe starts.
        Daemon.setLibDir(getApplicationInfo().nativeLibraryDir);
        // MASK BAR REMOVED (watermark over gameplay).
        animations = new OverlayAnimations(this, wm);
        devPopup = new DevPopup(this, wm);
        espOverlay = new com.ff.zesty.proxy.ui.overlay.EspOverlay(this, wm);
        espTunePanel = new com.ff.zesty.proxy.ui.panel.EspSetupPanel(this, wm);
        layoutEditor = new com.ff.zesty.proxy.ui.edit.LayoutEditor(this, wm);
        floatingControlPanel = new com.ff.zesty.proxy.ui.panel.FloatingControlPanel(this, wm);
        aimSetupPanel = new com.ff.zesty.proxy.ui.panel.AimSetupPanel(this, wm,
                new com.ff.zesty.proxy.ui.panel.AimSetupPanel.EditorHook() {
                    @Override
                    public void openEditor() {
                        if (layoutEditor != null) layoutEditor.open();
                    }
                });
        // Push the saved projection calibration into the renderer BEFORE the
        // overlay starts drawing, so the first frame already uses it.
        // Guarded: nothing below this line may be skipped because a tuning
        // value failed to load. espOverlay.show() is what puts the ESP, the
        // tune panel and the crosshair on screen, and it used to sit behind an
        // unguarded load() that could throw via the Shizuku-backed injector.
        try {
            com.ff.zesty.proxy.config.EspTuning.load(this);
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "EspTuning.load failed; using defaults", t);
        }
        // The placed swipe/fire geometry. Guarded for the same reason: a bad
        // stored fraction must not stop the overlay being shown.
        try {
            com.ff.zesty.proxy.ui.edit.Layout.load(this);
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "Layout.load failed; using defaults", t);
        }
        // Arm the scope-in watcher from the loaded preference. Must come AFTER
        // EspTuning.load, which is what reads that preference, and after
        // AimController.init(), which creates the worker thread it runs on.
        try {
            // ALWAYS on: this watcher is what arms and releases the daemon,
            // not an optional extra. The panel is grabbed on scope-in and
            // given back on scope-out.
            
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "scope watcher failed to arm", t);
        }
        // Arm the fire-press steer. The trigger has to be the press itself:
        // the scope latch comes off the wire, so it lands well after the scope
        // is actually up -- which is why the spin kept firing once the player
        // had already come out of it.
        try {
            pushRealScreenSize();
            // Take the grab NOW, at service start, while nothing is touching the
            // screen. The daemon defers it if a finger happens to be down. It
            // must not be taken later, mid-gesture: EVIOCGRAB during a touch
            // strands the framework's pointer on the real device permanently.
            if (AimConfig.TEST_MODE) AimController.arm();
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "aim input path failed to arm", t);
        }
        // The feature registry, AFTER EspTuning so that a setting which has
        // been migrated to a Feature wins over its old hand-written load line.
        // See EspTuning.load's note: during the migration a key may briefly
        // appear in both, and the registry is the one that should decide.
        // Guarded for the same reason the call above is: a bad stored value
        // must not stop the overlay being shown.
        try {
            com.ff.zesty.proxy.features.Registry.install(this);
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "Feature registry failed; using defaults", t);
            com.ff.zesty.proxy.diag.Diag.hit(
                    com.ff.zesty.proxy.diag.Slot.CFG_NO_PREFS);
        }
        // ESP overlay: full-screen box/radar renderer + calibration readout,
        // live while the VPN engine is alive.
        espOverlay.show(new com.ff.zesty.proxy.ui.overlay.EspOverlay.EspStateProvider() {
            @Override
            public String stateText() {
                com.ff.zesty.proxy.tunnel.VpnEngineState s = PacketVpnService.snapshot();
                if (s == null || !s.engineRun) return "ESP: engine off";
                com.ff.zesty.proxy.esp.EspEngine esp = PacketVpnService.espEngine();
                if (esp == null) return "ESP: not running";
                return esp.calibrationReadout();
            }

            @Override
            public com.ff.zesty.proxy.esp.EspEngine engine() {
                com.ff.zesty.proxy.tunnel.VpnEngineState s = PacketVpnService.snapshot();
                if (s == null || !s.engineRun) return null;
                return PacketVpnService.espEngine();
            }
        });

        android.content.SharedPreferences prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        showGhost = prefs.getBoolean(Prefs.KEY_GHOST, true);
        showFreeze = prefs.getBoolean(Prefs.KEY_FREEZE, true);
        showTele = prefs.getBoolean(Prefs.KEY_TELE, false);
        showDevMode = BuildConfig.DEBUG && prefs.getBoolean(Prefs.KEY_DEV_MODE, false);
        // Shipped in all variants now (slimmed prediction-only panel is a user
        // feature). Debug-readout and dev-mode stay BuildConfig.DEBUG-gated.
        showEspTune = prefs.getBoolean(Prefs.KEY_ESP_TUNE, false);
        // The old Aim setup chip is retired; Aim controls now live in the
        // responsive Zesty floating control panel.
        showAimSetup = false;
        // FreezeController — seed from saved prefs on first load
        FreezeController.setAimLag(    prefs.getBoolean(Prefs.KEY_AIM_LAG,     false));
        FreezeController.setAutoFreeze( prefs.getBoolean(Prefs.KEY_AUTO_FREEZE,  false));
        FreezeController.setScopeFreeze(prefs.getBoolean(Prefs.KEY_SCOPE_FREEZE, false));
        applyFreezeModeFromPrefs(prefs.getString(Prefs.KEY_FREEZE_MODE, "fire"));
        // ESP controls. Boxes and radar are independent (radar is useful on
        // its own — it needs no camera calibration), and the debug readout
        // can be switched off without losing the ESP itself.
        com.ff.zesty.proxy.config.EspConfig.SHOW_BOXES =
                prefs.getBoolean(Prefs.KEY_ESP_BOXES, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_RADAR =
                prefs.getBoolean(Prefs.KEY_ESP_RADAR, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_ESP_LINE =
                prefs.getBoolean(Prefs.KEY_ESP_LINE, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_SKELETON =
                prefs.getBoolean(Prefs.KEY_ESP_SKELETON, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_DIST_TEXT =
                prefs.getBoolean(Prefs.KEY_ESP_DIST_TEXT, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_NAME_TEXT =
                prefs.getBoolean(Prefs.KEY_ESP_NAME_TEXT, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_HP_TEXT =
                prefs.getBoolean(Prefs.KEY_ESP_HP_TEXT, true);
        com.ff.zesty.proxy.config.EspConfig.SHOW_WATERMARK =
                prefs.getBoolean(Prefs.KEY_ESP_WATERMARK, true);
        com.ff.zesty.proxy.config.EspConfig.BOX_COLOR_ARGB =
                prefs.getInt(Prefs.KEY_ESP_BOX_COLOR,
                        android.graphics.Color.argb(235, 255, 64, 64));
        if (espOverlay != null) {
            espOverlay.setDebugVisible(
                    BuildConfig.DEBUG && prefs.getBoolean(Prefs.KEY_ESP_DEBUG, false));
        }
        if (showEspTune) espTunePanel.show(tuneHint);
        if (showAimSetup) aimSetupPanel.show(tuneHint);
        floatAlphaPercent = prefs.getInt(Prefs.KEY_FLOAT_ALPHA, 92);
        floatSizePercent = prefs.getInt(Prefs.KEY_FLOAT_SIZE, 100);
        if (floatAlphaPercent < 35) floatAlphaPercent = 35;
        if (floatAlphaPercent > 100) floatAlphaPercent = 100;
        if (floatSizePercent < 80) floatSizePercent = 80;
        if (floatSizePercent > 260) floatSizePercent = 260;

        // The status title. Not branding this time: it answers the two
        // questions actually asked when something looks wrong -- can the
        // engine work yet, and how many players can it see. The old banner
        // drove an infinite 120 Hz shadow-blur animator; this one repaints
        // only when its text changes.
        animations.makeTopBanner(floatSizePercent);
        if (showDevMode) devPopup.make(floatSizePercent);
        // freeze and ghost floating buttons removed per UX update
        if (showTele) storeButton(button.makeBtn(getString(R.string.tele), 18, 248, 4, floatAlphaPercent, floatSizePercent, R.drawable.ic_tele));
        // NO CAPTURE BUTTON. Capture is always-on -- VpnEngine opens a fresh
        // dump on every engine start -- so a button could only ever turn OFF
        // the recording the diagnostics depend on.
        //
        // This line was creating the view unconditionally while its storeButton
        // case had already been deleted, so the returned view went nowhere: it
        // was drawn, never painted, never clickable, and impossible to remove.
        // Deleting the wiring without deleting the construction is how you get
        // a button that exists and does nothing.
        // aim/zoom overlay buttons removed; controlled via FreezeController toggles

        doRegister();
        // The primary settings surface is now available over gameplay. Its
        // width/height and drag bounds are calculated for the current display.
        if (floatingControlPanel != null) floatingControlPanel.show();

        // Paint from engine reality: if the VPN service is already running
        // (overlay started after engine, or both sticky-restored), the
        // buttons must show the live filter states — not hardcoded off.
        com.ff.zesty.proxy.tunnel.VpnEngineState s = PacketVpnService.snapshot();
        if (s != null) {
            onFreeze = s.freezeActive;
            onGhost = s.ghostActive;
            onJitter = s.jitterActive;
        }
        if (btnJitter != null) button.paintBtn(btnJitter, getString(R.string.tele), onJitter, floatAlphaPercent, R.drawable.ic_tele);
        animations.startRainbowAnimation();
        animations.startPulseAnimation();
    }

    /** Feeds the tune panel the renderer's live nearest-target readout. */
    private final com.ff.zesty.proxy.ui.panel.SetupPanel.HintProvider tuneHint =
            new com.ff.zesty.proxy.ui.panel.SetupPanel.HintProvider() {
                @Override
                public String hint() {
                    return (espOverlay == null) ? "ESP overlay off" : espOverlay.calibHint();
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (rcv != null) unregisterReceiver(rcv);
        } catch (Exception e) { }
        rm(btnFreeze, addedF);
        rm(btnGhost, addedG);
        rm(btnJitter, addedJ);
        if (espOverlay != null) espOverlay.hide();
        if (espTunePanel != null) espTunePanel.hide();
        if (aimSetupPanel != null) aimSetupPanel.hide();
        if (floatingControlPanel != null) floatingControlPanel.hide();
        if (layoutEditor != null) layoutEditor.close();
        animations.removeBanner();
        devPopup.remove();
        animations.cancelAll();
        // Stop the swipe and kill the nvmux daemon so it does not outlive the
        // overlay holding the panel grabbed.
        try {
            AimController.shutdown();
        } catch (Throwable ignored) {
        }
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void doForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService("notification");
                if (nm != null) {
                    NotificationChannel ch = new NotificationChannel(CH_ID, "GB", NotificationManager.IMPORTANCE_MIN);
                    ch.setSound(null, null);
                    nm.createNotificationChannel(ch);
                }
                Notification n = new Notification.Builder(this, CH_ID)
                        .setContentTitle("Gaming - Low Ping")
                        .setContentText("Latency reduction server activated")
                        .setSmallIcon(0x1080039)
                        .build();
                startForeground(NOTIF_ID, n);
            } else {
                Notification n = new Notification.Builder(this)
                        .setContentTitle("Gaming - Low Ping")
                        .setContentText("Latency reduction server activated")
                        .setSmallIcon(0x1080039)
                        .build();
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            try {
                Notification n = new Notification();
                n.icon = 0x1080039;
                startForeground(NOTIF_ID, n);
            } catch (Exception e2) { }
        }
    }

    private void doRegister() {
        rcv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (VpnActions.ACTION_APPLY_FLOAT_APPEARANCE.equals(action)) {
                    applyAppearanceIntent(intent);
                    return;
                }
                if (VpnActions.ACTION_STATE.equals(action)) {
                    applyEngineState(intent);
                    return;
                }
                if (!VpnActions.ACTION_STATS.equals(action)) return;

                String mode = intent.getStringExtra(VpnActions.EXTRA_MODE);
                if (mode == null) mode = "STANDBY";
            }
        };

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(VpnActions.ACTION_STATS);
            filter.addAction(VpnActions.ACTION_STATE);
            filter.addAction(VpnActions.ACTION_APPLY_FLOAT_APPEARANCE);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(rcv, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                // API 21-32: gate with the signature permission so a co-installed
                // app can't spoof appearance/state or flip dev-mode (audit M1).
                registerReceiver(rcv, filter, "com.ff.zesty.proxy.permission.INTERNAL_BROADCAST", null);
            }
        } catch (Exception e) {
            // A dead receiver means every button freezes at its last paint and
            // the overlay silently stops following the engine.
            com.ff.zesty.proxy.diag.Diag.hit(
                    com.ff.zesty.proxy.diag.Slot.UI_RECEIVER_REGISTER_FAILED);
        }
    }

    /** Repaints every button from the engine's broadcast truth. */
    private void applyEngineState(Intent intent) {
        onFreeze = intent.getBooleanExtra("freeze_active", false);
        onGhost = intent.getBooleanExtra("ghost_active", false);
        onJitter = intent.getBooleanExtra("jitter_active", false);
        boolean engineRun = intent.getBooleanExtra("engine_run", false);
        boolean matchReady = intent.getBooleanExtra("match_ready", false);
        if (btnJitter != null) button.paintBtn(btnJitter, getString(R.string.tele), onJitter, floatAlphaPercent, R.drawable.ic_tele);
        // The banner is the live engine-status indicator: IDLE (engine off),
        // LEARNING (movement stream not yet confirmed — effects won't bite
        // yet), READY (armed), ACTIVE (an effect is running).
        if (animations != null) {
            // Player counts, but only while the engine is live AND boxes are
            // on -- a count shown when nothing is drawing is a number with no
            // relationship to the screen.
            int en = -1, tm = 0, uk = 0;
            boolean riding = false;
            if (engineRun
                    && com.ff.zesty.proxy.config.EspConfig.SHOW_BOXES) {
                com.ff.zesty.proxy.esp.EspEngine esp = PacketVpnService.espEngine();
                if (esp != null) {
                    // 3 s: long enough to ride out the 198 ms entity tick and a
                    // dropped burst, short enough that someone who has stopped
                    // being streamed drops off while you are still looking.
                    esp.roleCounts(3000L, roleScratch);
                    en = roleScratch[0];
                    tm = roleScratch[1];
                    uk = roleScratch[2];
                    riding = esp.mounted();
                }
            }
            int status;
            if (!engineRun) {
                status = com.ff.zesty.proxy.ui.button.OverlayAnimations.STATUS_IDLE;
            } else if (!matchReady) {
                status = com.ff.zesty.proxy.ui.button.OverlayAnimations.STATUS_LEARNING;
            } else if (onFreeze || onGhost || onJitter) {
                status = com.ff.zesty.proxy.ui.button.OverlayAnimations.STATUS_ACTIVE;
            } else {
                status = com.ff.zesty.proxy.ui.button.OverlayAnimations.STATUS_READY;
            }
            animations.setBannerStatus(status, en, tm, uk, riding);
        }
    }

    private void storeButton(OverlayButton.ButtonResult r) {
        switch (r.id) {
            case 2: btnFreeze = r.view; freezeParams = r.params; addedF = r.added; break;
            case 3: btnGhost = r.view; ghostParams = r.params; addedG = r.added; break;
            case 4: btnJitter = r.view; teleParams = r.params; addedJ = r.added; break;
        }
    }

    private void initTone() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 95);
        } catch (Exception e) {
            toneGenerator = null;
        }
    }

    private void playToggleBeep(boolean isOn) {
        if (toneGenerator == null) return;
        try {
            toneGenerator.startTone(
                    isOn ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD : ToneGenerator.TONE_PROP_ACK,
                    isOn ? 120 : 105
            );
        } catch (Exception e) { }
    }

    private void applyAppearanceIntent(Intent intent) {
        if (intent == null) return;
        int nextSize = intent.getIntExtra(VpnActions.EXTRA_FLOAT_SIZE, floatSizePercent);
        int nextAlpha = intent.getIntExtra(VpnActions.EXTRA_FLOAT_ALPHA, floatAlphaPercent);
        if (nextSize < 80) nextSize = 80;
        if (nextSize > 260) nextSize = 260;
        if (nextAlpha < 35) nextAlpha = 35;
        if (nextAlpha > 100) nextAlpha = 100;
        floatSizePercent = nextSize;
        floatAlphaPercent = nextAlpha;
        showGhost = intent.getBooleanExtra(VpnActions.EXTRA_SHOW_GHOST, showGhost);
        showFreeze = intent.getBooleanExtra(VpnActions.EXTRA_SHOW_FREEZE, showFreeze);
        showTele = intent.getBooleanExtra(VpnActions.EXTRA_SHOW_TELE, showTele);
        showDevMode = intent.getBooleanExtra(VpnActions.EXTRA_DEV_MODE, showDevMode);
        // ESP switches used to be read only in onCreate, so flipping one in the
        // app did nothing until the overlay was restarted. They ride the same
        // appearance broadcast as everything else now.
        com.ff.zesty.proxy.config.EspConfig.SHOW_BOXES =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_BOXES,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_BOXES);
        // Skeleton is tied to ESP Box — same toggle, no extra UI needed.
        com.ff.zesty.proxy.config.EspConfig.SHOW_SKELETON =
                com.ff.zesty.proxy.config.EspConfig.SHOW_BOXES;
        com.ff.zesty.proxy.config.EspConfig.SHOW_RADAR =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_RADAR,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_RADAR);
        com.ff.zesty.proxy.config.EspConfig.SHOW_ESP_LINE =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_LINE,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_ESP_LINE);
        com.ff.zesty.proxy.config.EspConfig.SHOW_SKELETON =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_SKELETON,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_SKELETON);
        com.ff.zesty.proxy.config.EspConfig.SHOW_DIST_TEXT =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_DIST_TEXT,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_DIST_TEXT);
        com.ff.zesty.proxy.config.EspConfig.SHOW_NAME_TEXT =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_NAME_TEXT,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_NAME_TEXT);
        com.ff.zesty.proxy.config.EspConfig.SHOW_HP_TEXT =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_HP_TEXT,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_HP_TEXT);
        com.ff.zesty.proxy.config.EspConfig.SHOW_WATERMARK =
                intent.getBooleanExtra(VpnActions.EXTRA_ESP_WATERMARK,
                        com.ff.zesty.proxy.config.EspConfig.SHOW_WATERMARK);
        com.ff.zesty.proxy.config.EspConfig.BOX_COLOR_ARGB =
                intent.getIntExtra(VpnActions.EXTRA_ESP_BOX_COLOR,
                        com.ff.zesty.proxy.config.EspConfig.BOX_COLOR_ARGB);
        if (espOverlay != null) {
            espOverlay.setDebugVisible(intent.getBooleanExtra(
                    VpnActions.EXTRA_ESP_DEBUG, espOverlay.isDebugVisible()));
        }
        showEspTune = intent.getBooleanExtra(VpnActions.EXTRA_ESP_TUNE, showEspTune);
        // Ignore the retired Aim setup-chip flag. The floating control panel
        // is the single Aim control surface now.
        showAimSetup = false;

        // Aim Legit toggle: enable/disable the aim loop.
        if (intent.hasExtra(VpnActions.EXTRA_AIMBOT)) {
            boolean aimOn = intent.getBooleanExtra(VpnActions.EXTRA_AIMBOT, false);
            com.ff.zesty.proxy.ui.aim.AimConfig.AIM_ENABLED = aimOn;
            if (aimOn) {
                // Force AIM_BOX_FRAC to skull so head-lock starts immediately
                com.ff.zesty.proxy.config.EspConfig.AIM_BOX_FRAC =
                        com.ff.zesty.proxy.ui.aim.AimConfig.BOX_HEAD;
            }
            com.ff.zesty.proxy.ui.aim.AimController.setActive(aimOn);
        }

        // Buff speed: arm/disarm the Cmd-170 buff-inject thread in DefaultFilterEngine.
        if (intent.hasExtra(VpnActions.EXTRA_BUFF_SPEED)) {
            boolean buffOn = intent.getBooleanExtra(VpnActions.EXTRA_BUFF_SPEED, false);
            com.ff.zesty.proxy.filter.FilterEngine fe = PacketVpnService.filterEngineOrNull();
            if (fe instanceof com.ff.zesty.proxy.filter.DefaultFilterEngine) {
                com.ff.zesty.proxy.filter.BuffSpeedFilter bsf =
                        ((com.ff.zesty.proxy.filter.DefaultFilterEngine) fe).buffSpeed();
                if (buffOn) bsf.start(); else bsf.stop();
            }
        }

        // FreezeController — apply toggled states from AppearanceBroadcaster
        if (intent.hasExtra(VpnActions.EXTRA_AIM_LAG)) {
            FreezeController.setAimLag(intent.getBooleanExtra(VpnActions.EXTRA_AIM_LAG, false));
        }
        if (intent.hasExtra(VpnActions.EXTRA_AUTO_FREEZE)) {
            FreezeController.setAutoFreeze(intent.getBooleanExtra(VpnActions.EXTRA_AUTO_FREEZE, false));
        }
        if (intent.hasExtra(VpnActions.EXTRA_SCOPE_FREEZE)) {
            FreezeController.setScopeFreeze(intent.getBooleanExtra(VpnActions.EXTRA_SCOPE_FREEZE, false));
        }
        if (intent.hasExtra(VpnActions.EXTRA_FREEZE_MODE)) {
            applyFreezeModeFromPrefs(intent.getStringExtra(VpnActions.EXTRA_FREEZE_MODE));
        }

        applyRealtimeAppearance();
    }

    /**
     * Tell the injector the TRUE physical screen size.
     *
     * <p>Not the overlay view's size, and not {@code getDisplayMetrics()}. The
     * daemon maps raw panel coordinates onto the whole physical panel, so a
     * fire circle sized against an inset view lands ~170 px left of the real
     * button and every press reads as a miss. {@code getRealMetrics} is the
     * only call that reports the panel itself, so it is the right call here
     * regardless of what the view reports.</p>
     *
     * <p><b>UNRESOLVED, 2026-09-20 — do not build on either number.</b> This
     * javadoc has said the ESP view measures <b>2117x1080</b> against a
     * 2340x1080 display (223 px of cutout and nav-bar inset). The renderer's
     * own live VIEWPORT row, recorded the same session, reads
     * {@code 2340x1080 org 0,0 exact}. They cannot both be current, and the
     * 2117 note was written AFTER {@code FLAG_LAYOUT_NO_LIMITS} went on the ESP
     * window, which is exactly the flag that would have made it stale.</p>
     *
     * <p>It does not affect the projection either way — a WIDTH mismatch
     * cancels out of both projection scales, so it could only ever translate,
     * never rescale. It DOES affect the aim editor, which draws its placed
     * geometry against the view's {@code w/h} while {@link com.ff.zesty.proxy.ui.aim.AimController}
     * resolves the same fractions against {@code getRealMetrics}. To settle it:
     * open the ESP setup panel in game and read the VIEWPORT row, or run
     * {@code dumpsys window windows} with the overlay up (overlay frames never
     * appear in a uiautomator dump — {@code FLAG_NOT_FOCUSABLE}).</p>
     */
    private void pushRealScreenSize() {
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                AimController.setScreen(dm.widthPixels, dm.heightPixels);
            }
        } catch (Throwable t) {
            android.util.Log.w("Fakelag", "real screen size unavailable", t);
        }
    }

    private void applyRealtimeAppearance() {
        pushRealScreenSize();
        syncFloatingButtonVisibility();
        button.updateButtonLayout(btnJitter, teleParams, 4, onJitter, floatAlphaPercent, floatSizePercent);
        devPopup.update(showDevMode, floatSizePercent);
        if (espTunePanel != null) espTunePanel.update(showEspTune, tuneHint);
        if (aimSetupPanel != null) aimSetupPanel.update(showAimSetup, tuneHint);
        // aim/zoom buttons removed
    }

    private void syncFloatingButtonVisibility() {
        // freeze and ghost floating buttons removed per UX update
        if (showTele) {
            if (btnJitter == null) storeButton(button.makeBtn(getString(R.string.tele), 18, 248, 4, floatAlphaPercent, floatSizePercent, R.drawable.ic_tele));
            button.paintBtn(btnJitter, getString(R.string.tele), onJitter, floatAlphaPercent, R.drawable.ic_tele);
        } else {
            rm(btnJitter, addedJ);
            btnJitter = null;
            teleParams = null;
            addedJ = false;
        }
    }

    private void click(int id) {
        // No optimistic flips: the engine's ACTION_STATE broadcast repaints
        // the button when the toggle actually lands (queued during restarts).
        // The beep still fires immediately as touch feedback.
        // Beep only if the tap can actually land. It used to beep
        // unconditionally, so after onRevoke() -- another VPN app takes the
        // slot -- every press was discarded while still sounding accepted.
        boolean live = PacketVpnService.engineAcceptsTaps();
        if (id == 2) {
            if (live) playToggleBeep(!onFreeze);
            send(VpnActions.ACTION_FREEZE);
        } else if (id == 3) {
            if (live) playToggleBeep(!onGhost);
            send(VpnActions.ACTION_GHOST);
        } else if (id == 4) {
            if (live) playToggleBeep(!onJitter);
            send(VpnActions.ACTION_JITTER);
        }
        // ids 9 (aim) and 10 (zoom) buttons removed — use FreezeController toggles in app
    }

    /**
     * The AIM button: the master switch for everything that touches the screen.
     *
     * <p>It used to gate only the corrective-swipe loop, while the scope-in
     * steer ran off {@code TEST_MODE} alone — so turning AIM off left the steer
     * firing on every scope. A control labelled AIM that does not stop the aim
     * is worse than no control.</p>
     *
     * <p>Off does NOT release the grab. Releasing mid-touch strands a pointer
     * exactly as grabbing mid-touch does; the daemon keeps the panel and simply
     * stops being told to do anything with it.</p>
     */
    private void setAimActive(boolean on) {
        onAim = on;
        if (on) {
            pushRealScreenSize();
            // Spawn the daemon NOW rather than on the first acquisition. It used
            // to start inside fireAtTarget(), after the freshness gate, so with
            // nothing acquired there was no daemon, no process and no log --
            // and "no target" was indistinguishable from "the input path never
            // came up".
            AimController.arm();
            AimController.setActive(true);
            startAimLoop();
        } else {
            stopAimLoop();
            AimController.setActive(false);
        }
        // aim overlay button removed
    }

    // ---- real-aim loop ----------------------------------------------------
    // While the Aim toggle is on, fire ONE corrective swipe whenever a target is
    // acquired. Deliberately not a tracking controller: each swipe is computed
    // once, open-loop, from the error the renderer measured (see AimController
    // .fireAtTarget). The renderer's own gates decide whether a target exists at
    // all -- team known, stationary, in cone, in range.
    /** Main-looper handler for the aim loop. */
    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long AIM_LOOP_MS = 60L;
    /** Minimum gap between swipes — tightened for head-lock responsiveness. */
    private static final long AIM_SHOT_GAP_MS = 100L;
    private long lastAimShotMs = 0L;
    private Runnable aimLoop;

    private void startAimLoop() {
        stopAimLoop();
        aimLoop = new Runnable() {
            @Override
            public void run() {
                if (!onAim) return;
                long now = System.currentTimeMillis();
                if (now - lastAimShotMs >= AIM_SHOT_GAP_MS) {
                    if (AimController.fireAtTarget()) lastAimShotMs = now;
                }
                if (handler != null) handler.postDelayed(this, AIM_LOOP_MS);
            }
        };
        if (handler != null) handler.post(aimLoop);
    }

    private void stopAimLoop() {
        if (handler != null && aimLoop != null) handler.removeCallbacks(aimLoop);
        aimLoop = null;
    }

    /**
     * Maps the freeze mode string from Prefs to the right FreezeController flags.
     * "fire"  → autoFreeze on,  scopeFreeze off
     * "scope" → autoFreeze off, scopeFreeze on
     * "both"  → autoFreeze on,  scopeFreeze on
     */
    private void applyFreezeModeFromPrefs(String mode) {
        if (mode == null) mode = "fire";
        switch (mode) {
            case "scope":
                FreezeController.setAutoFreeze(false);
                FreezeController.setScopeFreeze(true);
                break;
            case "both":
                FreezeController.setAutoFreeze(true);
                FreezeController.setScopeFreeze(true);
                break;
            default: // "fire"
                FreezeController.setAutoFreeze(true);
                FreezeController.setScopeFreeze(false);
                break;
        }
    }

    private void send(String action) {
        // Filter taps take the in-process path when the service is alive: it
        // is the same process (no android:process in the manifest), so the
        // intent was a binder round trip to AMS that came back to this very
        // looper. For freeze that round trip sat in front of the packet
        // filter on every single press. See PacketVpnService.applyFilterTap.
        //
        // Anything else -- and the case where the service is not running, in
        // which case only an intent can start it -- still goes via the intent.
        com.ff.zesty.proxy.filter.FilterKind kind =
                com.ff.zesty.proxy.filter.FilterKind.fromAction(action);
        if (kind != null && PacketVpnService.applyFilterTap(kind)) {
            return;
        }
        try {
            Intent i = new Intent(this, PacketVpnService.class);
            i.setAction(action);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception e) { }
    }

    private void rm(View v, boolean added) {
        if (!added || v == null) return;
        try {
            wm.removeView(v);
        } catch (Exception e) { }
    }
}
