package com.ff.zesty.proxy.config;

import android.app.ActivityManager;
import android.content.Context;

import com.ff.zesty.proxy.config.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Knows which packages are "the game", and whether the tunnel is pointed at the
 * one actually being played.
 */
public final class GameTargets {

    private GameTargets() {}

    /** Free Fire Global */
    public static final String FREE_FIRE_GLOBAL = "com.dts.freefireth";
    /** Free Fire MAX */
    public static final String FREE_FIRE_MAX    = "com.dts.freefiremax";
    /** Free Fire India */
    public static final String FREE_FIRE_INDIA  = "com.dts.freefireth.in";
    /** Free Fire FFI */
    public static final String FREE_FIRE_FFI    = "com.garena.game.ffi";

    /**
     * Every Free Fire package we know of. Order is preference order for the
     * "which one should I be targeting" hint.
     */
    public static final String[] KNOWN = {
            FREE_FIRE_MAX,
            FREE_FIRE_GLOBAL,
            FREE_FIRE_INDIA,
            FREE_FIRE_FFI,
            "com.garena.game.kgvn",
            "com.garena.game.kgth",
            "com.garena.game.kgid",
    };

    /**
     * Display name for a package, shown in the target picker.
     */
    public static String displayName(String pkg) {
        if (FREE_FIRE_MAX.equals(pkg))    return "Free Fire MAX";
        if (FREE_FIRE_GLOBAL.equals(pkg)) return "Free Fire";
        if (FREE_FIRE_INDIA.equals(pkg))  return "Free Fire India";
        if (FREE_FIRE_FFI.equals(pkg))    return "Free Fire (FFI)";
        if ("com.garena.game.kgvn".equals(pkg)) return "Free Fire (Vietnam)";
        if ("com.garena.game.kgth".equals(pkg)) return "Free Fire (Thailand)";
        if ("com.garena.game.kgid".equals(pkg)) return "Free Fire (Indonesia)";
        int i = pkg.lastIndexOf('.');
        return i >= 0 ? pkg.substring(i + 1) : pkg;
    }

    public static boolean isKnownGame(String pkg) {
        if (pkg == null) return false;
        for (String k : KNOWN) {
            if (k.equals(pkg)) return true;
        }
        return false;
    }

    public static boolean isInstalled(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return false;
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Known game packages installed on this device. */
    public static List<String> installedGames(Context ctx) {
        List<String> out = new ArrayList<>(7);
        for (String k : KNOWN) {
            if (isInstalled(ctx, k)) out.add(k);
        }
        return out;
    }

    /**
     * True when the package currently has a running process.
     */
    public static boolean isRunning(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        java.io.File proc = new java.io.File("/proc");
        String[] pids = proc.list();
        if (pids == null) return fallbackRunning(ctx, pkg);
        for (String p : pids) {
            if (p.isEmpty() || !Character.isDigit(p.charAt(0))) continue;
            java.io.RandomAccessFile f = null;
            try {
                f = new java.io.RandomAccessFile("/proc/" + p + "/cmdline", "r");
                byte[] b = new byte[128];
                int n = f.read(b);
                if (n <= 0) continue;
                int end = 0;
                while (end < n && b[end] != 0) end++;
                if (end == pkg.length() && new String(b, 0, end, "UTF-8").equals(pkg)) {
                    return true;
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    if (f != null) f.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean fallbackRunning(Context ctx, String pkg) {
        try {
            ActivityManager am =
                    (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            if (ps == null) return false;
            for (ActivityManager.RunningAppProcessInfo p : ps) {
                if (pkg.equals(p.processName)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * A known game package that is running but is NOT in the tunnel's target
     * set. Null when there is nothing to report.
     */
    public static String untargetedRunningGame(Context ctx) {
        Set<String> targets = Prefs.getTargetPackages(ctx);
        for (String k : KNOWN) {
            if (targets.contains(k)) continue;
            if (!isInstalled(ctx, k)) continue;
            if (isRunning(ctx, k)) return k;
        }
        return null;
    }

    /**
     * One line for the dev HUD.
     */
    public static String debugLine(Context ctx) {
        Set<String> targets = Prefs.getTargetPackages(ctx);
        if (targets.isEmpty()) return "NO TARGET APP SELECTED";
        StringBuilder sb = new StringBuilder(96);
        boolean anyRunning = false;
        for (String t : targets) {
            boolean inst = isInstalled(ctx, t);
            boolean run = inst && isRunning(ctx, t);
            anyRunning |= run;
            sb.append(shortName(t));
            sb.append(!inst ? "(NOT INSTALLED)" : (run ? "(run)" : "(stopped)"));
            sb.append(' ');
        }
        String other = untargetedRunningGame(ctx);
        if (other != null) {
            sb.append("<< PLAYING ").append(shortName(other)).append(" — NOT TARGETED");
        } else if (!anyRunning) {
            sb.append("<< target not running");
        }
        return sb.toString();
    }

    private static String shortName(String pkg) {
        int i = pkg.lastIndexOf('.');
        return i >= 0 ? pkg.substring(i + 1) : pkg;
    }
}
