package com.whatsapp;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plays a scenario as if it were happening live: "sender" messages arrive as incoming messages
 * with a notification, "reader" messages appear as the user's replies (no notification), one by one.
 */
public final class ReplayEngine {
    public static final String ASSET_ROOT = "scenarios";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Runnable> QUEUE = new ArrayList<>();

    private ReplayEngine() {}

    /** Queue one scenario; returns the time (ms from now) when its last message is delivered. */
    public static long play(Context ctx, Scenario sc, long startDelayMs) {
        Context c = ctx.getApplicationContext();
        long step = Settings.delaySeconds(c) * 1000L;
        // Each scenario gets its own chat, even if two files share a sender.
        String key = sc.id + "|" + sc.displayTitle();
        long t = startDelayMs;
        for (Scenario.Msg m : sc.messages) {
            final Scenario.Msg msg = m;
            Runnable r = () -> {
                ChatStore.Conversation conv = ChatStore.getOrCreate(key, sc.displayTitle(), sc.phoneOrNull(), sc.knownContact);
                if (msg.fromReader) {
                    ChatStore.add(conv, msg.text, true, !ConnectionMonitor.isOnline());
                } else {
                    ChatStore.add(conv, msg.text, false, false);
                    if (!ChatActivity.isOpen(key)) MessageNotifier.notifyIncoming(c, conv);
                    else conv.unread = 0;
                }
            };
            QUEUE.add(r);
            MAIN.postDelayed(r, t);
            // Replies come a bit faster than the other side's messages.
            t += msg.fromReader ? step / 2 : step;
        }
        return t;
    }

    public static void playAll(Context ctx, List<Scenario> list) {
        long t = 500;
        for (Scenario sc : list) {
            // Leave time between scenarios: the companion shows one warning at a time.
            t = play(ctx, sc, t) + 20_000;
        }
    }

    public static void stopAll() {
        for (Runnable r : QUEUE) MAIN.removeCallbacks(r);
        QUEUE.clear();
    }

    // ---------------------------------------------------------------- assets

    public static List<String> assetFolders(Context c) {
        try {
            String[] dirs = c.getAssets().list(ASSET_ROOT);
            List<String> out = new ArrayList<>(Arrays.asList(dirs == null ? new String[0] : dirs));
            out.sort(String::compareTo);
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<String> assetFiles(Context c, String folder) {
        try {
            String[] files = c.getAssets().list(ASSET_ROOT + "/" + folder);
            List<String> out = new ArrayList<>();
            if (files != null) for (String f : files) if (f.endsWith(".json")) out.add(folder + "/" + f);
            out.sort(String::compareTo);
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static Scenario loadAsset(Context c, String relPath) throws Exception {
        AssetManager am = c.getAssets();
        try (InputStream in = am.open(ASSET_ROOT + "/" + relPath)) {
            return Scenario.parse(readAll(in));
        }
    }

    /** For adb: play "validation_set/V04.json" or a whole folder "validation_set". */
    public static void playFromIntent(Context c, String file, String folder) {
        try {
            if (file != null) {
                play(c, loadAsset(c, file), 500);
                toast(c, "Rulez " + file);
            } else if (folder != null) {
                List<Scenario> list = new ArrayList<>();
                for (String f : assetFiles(c, folder)) list.add(loadAsset(c, f));
                playAll(c, list);
                toast(c, "Rulez " + list.size() + " scenarii din " + folder);
            }
        } catch (Exception e) {
            toast(c, "Nu pot citi scenariul: " + e.getMessage());
        }
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }
}
