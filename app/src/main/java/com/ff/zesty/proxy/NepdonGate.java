package com.ff.zesty.proxy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import java.util.Calendar;

/** Local access gate. A real device cap must still be enforced server-side. */
public final class NepdonGate {
    public static final String TELEGRAM_URL = "https://t.me/nepdonstudio";
    public static final String WHATSAPP_URL = "https://whatsapp.com/channel/0029Vb8ytI36buMENU7E453o";
    public static final int MAX_DEVICES = 10000;
    private static final String PREFS = "zesty_proxy_access";
    private static final String LOGGED_IN = "logged_in";
    private static boolean noticeShown;

    private static final int[] USERNAME_XOR = {
            20, 31, 10, 30, 21, 20, 109, 106, 106
    };
    private static final int USERNAME_KEY = 0x5A;

    private NepdonGate() { }

    public static boolean isUsernameValid(String value) {
        if (value == null || value.length() != USERNAME_XOR.length) return false;
        int diff = 0;
        for (int i = 0; i < USERNAME_XOR.length; i++) {
            diff |= value.charAt(i) ^ (USERNAME_XOR[i] ^ USERNAME_KEY);
        }
        return diff == 0;
    }

    public static boolean isLoggedIn(Context context) {
        return context != null
                && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(LOGGED_IN, false);
    }

    public static void markLoggedIn(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(LOGGED_IN, true).apply();
    }

    public static boolean isExpired() {
        Calendar expiry = Calendar.getInstance();
        expiry.set(2036, Calendar.NOVEMBER, 7, 0, 0, 0);
        expiry.set(Calendar.MILLISECOND, 0);
        return System.currentTimeMillis() >= expiry.getTimeInMillis();
    }

    /** Shows nothing before expiry; after expiry this is the only blocking dialog. */
    public static void showExpiryDialog(final Activity activity) {
        if (activity == null || activity.isFinishing() || noticeShown || !isExpired()) return;
        noticeShown = true;
        new AlertDialog.Builder(activity, R.style.Theme_Nepdon_Dialog)
                .setTitle("ACCESS EXPIRED")
                .setMessage("Zesty Proxy access expired on 7 November 2026.\n\nJoin the Telegram channel for updates.")
                .setCancelable(false)
                .setPositiveButton("CLOSE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.finish();
                    }
                })
                .setNeutralButton("TELEGRAM", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openTelegram(activity);
                    }
                })
                .show();
    }

    public static void openTelegram(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)));
        } catch (Throwable ignored) { }
    }

    public static void openWhatsApp(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_URL)));
        } catch (Throwable ignored) { }
    }
}
