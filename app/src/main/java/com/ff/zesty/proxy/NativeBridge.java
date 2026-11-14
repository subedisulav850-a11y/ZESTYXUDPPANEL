package com.ff.zesty.proxy;

import android.util.Base64;

public class NativeBridge {

    private static String dec(String encoded) {
        try {
            byte[] data = Base64.decode(encoded, Base64.DEFAULT);
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public static String getTelegram() {
        return dec("aHR0cHM6Ly90Lm1lL1hJTk9YWFI=");
    }

    public static String getApiUrl() {
        return dec("aHR0cHM6Ly9rZXkubGlnaHRrdXJvLnNpdGUvY29ubmVjdA==");
    }

    public static String getSecretKey() {
        return dec("WVVOR1BJTkcyMDI2U2VjcmV0S2V5MzJCeXRlc0xlbg==");
    }

    public static String getSecretIv() {
        return dec("WVVOR1BJTkdJbml0VmVjMQ==");
    }

    public static String getGame() {
        return dec("UFVCRw==");
    }
}
