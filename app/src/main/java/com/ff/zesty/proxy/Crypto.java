package com.ff.zesty.proxy;

import android.util.Base64;

public class Crypto {
    private static final String KEY = "YUNG_PING_SECRET";

    public static String enc(String str) {
        if (str == null || str.isEmpty()) return "";
        try {
            byte[] bytes = str.getBytes("UTF-8");
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ KEY.charAt(i % KEY.length()));
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static String dec(String str) {
        if (str == null || str.isEmpty()) return "";
        try {
            byte[] bytes = Base64.decode(str, Base64.NO_WRAP);
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ KEY.charAt(i % KEY.length()));
            }
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
