package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to the existing FastAPI backend. All network work runs off the main thread. */
public final class ScamShieldApiClient {
    private static final String TAG = "ScamShieldApi";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(AnalysisResult result);
        void onError(String reason);
    }

    private ScamShieldApiClient() {}

    public static void analyze(Context context, String text, boolean knownContact, Callback cb) {
        final String base = Prefs.backendUrl(context);
        IO.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("text", text.length() > 20000 ? text.substring(0, 20000) : text);
                req.put("known_contact", knownContact);
                String body = post(base + "/api/analyze_text", req.toString());
                AnalysisResult r = AnalysisResult.fromJson(body);
                MAIN.post(() -> cb.onResult(r));
            } catch (Exception e) {
                // Development log only: never includes the message text.
                Log.w(TAG, "analysis failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                MAIN.post(() -> cb.onError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        });
    }

    private static String post(String url, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = is == null ? "" : readAll(is);
            if (code >= 400) throw new Exception("HTTP " + code);
            return body;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
