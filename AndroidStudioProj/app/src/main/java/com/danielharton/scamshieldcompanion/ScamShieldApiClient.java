package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to the existing FastAPI backend. All network work runs off the main thread. */
public final class ScamShieldApiClient {
    private static final String TAG = "ScamShieldApi";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Ports the backend is usually started on (app.py defaults to 8000; the demo setup uses 8002). */
    private static final int[] KNOWN_PORTS = {8000, 8002};
    /** Last base URL that answered; tried first so a fallback costs only one failed connect. */
    private static volatile String lastWorking;

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
                String body = postWithFallback(base, "/api/analyze_text", req.toString());
                AnalysisResult r = AnalysisResult.fromJson(body);
                MAIN.post(() -> cb.onResult(r));
            } catch (Exception e) {
                // Development log only: never includes the message text.
                Log.w(TAG, "analysis failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                MAIN.post(() -> cb.onError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        });
    }

    /**
     * Tries the configured backend first; if nothing is listening there (connection refused / timeout),
     * tries the same host on the other usual port. HTTP errors are not retried: the server did answer.
     */
    private static String postWithFallback(String base, String path, String json) throws Exception {
        List<String> candidates = new ArrayList<>();
        if (lastWorking != null && sameHost(lastWorking, base)) candidates.add(lastWorking);
        if (!candidates.contains(base)) candidates.add(base);
        for (String alt : alternatives(base)) if (!candidates.contains(alt)) candidates.add(alt);

        IOException firstFailure = null;
        for (String candidate : candidates) {
            try {
                String body = post(candidate + path, json);
                lastWorking = candidate;
                return body;
            } catch (HttpStatusException e) {
                throw e;
            } catch (IOException e) {
                Log.w(TAG, "backend unreachable at " + candidate + ": " + e.getClass().getSimpleName());
                if (firstFailure == null) firstFailure = e;
            }
        }
        throw firstFailure != null ? firstFailure : new IOException("no backend URL");
    }

    private static List<String> alternatives(String base) {
        List<String> out = new ArrayList<>();
        try {
            URL u = new URL(base);
            for (int port : KNOWN_PORTS) {
                if (port == u.getPort()) continue;
                out.add(new URL(u.getProtocol(), u.getHost(), port, u.getPath()).toString());
            }
        } catch (Exception ignored) {
            // Malformed URL: only the configured one is tried and its error is reported.
        }
        return out;
    }

    private static boolean sameHost(String a, String b) {
        try {
            return new URL(a).getHost().equalsIgnoreCase(new URL(b).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static final class HttpStatusException extends IOException {
        HttpStatusException(int code) {
            super("HTTP " + code);
        }
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
            if (code >= 400) throw new HttpStatusException(code);
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
