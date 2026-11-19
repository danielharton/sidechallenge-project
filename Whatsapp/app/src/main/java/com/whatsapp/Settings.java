package com.whatsapp;

import android.content.Context;
import android.content.SharedPreferences;

/** Test settings only. */
public final class Settings {
    private static final String FILE = "test_messenger";
    /** Any URL reachable from the emulator works; the Scam Shield backend is handy. */
    public static final String DEFAULT_SERVER = "http://10.0.2.2:8002/api/status";

    private Settings() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String serverUrl(Context c) {
        return sp(c).getString("server", DEFAULT_SERVER);
    }

    public static void setServerUrl(Context c, String v) {
        sp(c).edit().putString("server", v.trim()).apply();
    }

    public static int delaySeconds(Context c) {
        return sp(c).getInt("delay", 3);
    }

    public static void setDelaySeconds(Context c, int v) {
        sp(c).edit().putInt("delay", Math.max(1, Math.min(60, v))).apply();
    }
}
