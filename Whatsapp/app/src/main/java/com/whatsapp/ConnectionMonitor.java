package com.whatsapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pings a server every few seconds so the app can show "connected" / "no connection".
 * When the Scam Shield companion blocks com.whatsapp through its VPN, this turns red:
 * a visible proof that the block works. Pending outgoing messages are sent when it comes back.
 */
public final class ConnectionMonitor {
    public interface Listener {
        void onConnectionChanged(boolean online);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final long PERIOD_MS = 2500;

    private static volatile boolean online = true;
    private static boolean running = false;
    private static Context app;

    private ConnectionMonitor() {}

    public static boolean isOnline() {
        return online;
    }

    public static void start(Context c) {
        app = c.getApplicationContext();
        if (running) return;
        running = true;
        MAIN.post(TICK);
    }

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            final String url = Settings.serverUrl(app);
            IO.execute(() -> {
                boolean ok = probe(url);
                MAIN.post(() -> {
                    if (ok != online) {
                        online = ok;
                        for (Listener l : LISTENERS) l.onConnectionChanged(ok);
                        if (ok) flushPending();
                    }
                });
            });
            MAIN.postDelayed(this, PERIOD_MS);
        }
    };

    private static boolean probe(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            return code > 0 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void flushPending() {
        boolean any = false;
        for (ChatStore.Conversation conv : ChatStore.all()) {
            for (ChatStore.Message m : conv.messages) {
                if (m.pending) {
                    m.pending = false;
                    any = true;
                }
            }
        }
        if (any) ChatStore.changed();
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }
}
