package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.content.SharedPreferences;

/** Only settings are stored. Message contents are never saved. */
public final class Prefs {
    private static final String FILE = "scam_shield_prefs";
    private static final String KEY_BACKEND = "backend_url";
    private static final String KEY_MONITORING = "monitoring";
    /** Android emulator -> host laptop. A real phone needs the laptop's LAN IP. */
    public static final String DEFAULT_BACKEND = "http://10.0.2.2:8000";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String backendUrl(Context c) {
        return sp(c).getString(KEY_BACKEND, DEFAULT_BACKEND);
    }

    public static void setBackendUrl(Context c, String url) {
        String clean = url.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        sp(c).edit().putString(KEY_BACKEND, clean).apply();
    }

    /**
     * Debug builds only: one extra package to route into the blocking VPN, so the block can be tested
     * on an emulator without WhatsApp. Set with run-as; ignored in release (non-debuggable) builds.
     */
    public static String debugBlockPackage(Context c) {
        boolean debuggable = (c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return debuggable ? sp(c).getString("debug_block_package", null) : null;
    }

    public static boolean monitoring(Context c) {
        return sp(c).getBoolean(KEY_MONITORING, true);
    }

    public static void setMonitoring(Context c, boolean on) {
        sp(c).edit().putBoolean(KEY_MONITORING, on).apply();
    }
}
