package com.danielharton.scamshieldcompanion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Fields returned by POST /api/analyze_text (engine.analyze_text in the Python backend). */
public final class AnalysisResult {
    public final String verdict;      // SAFE | SUSPICIOUS | SCAM
    public final String scamType;
    public final int riskScore;
    public final List<String> redFlags;
    public final String warningRo;

    private AnalysisResult(String verdict, String scamType, int riskScore, List<String> redFlags, String warningRo) {
        this.verdict = verdict;
        this.scamType = scamType;
        this.riskScore = riskScore;
        this.redFlags = redFlags;
        this.warningRo = warningRo;
    }

    public boolean isDangerous() {
        return "SCAM".equals(verdict) || "SUSPICIOUS".equals(verdict);
    }

    /** Throws if the backend answered with {"error": ...} or without a verdict: never turn an error into SAFE. */
    public static AnalysisResult fromJson(String body) throws JSONException {
        JSONObject o = new JSONObject(body);
        if (o.has("error")) throw new JSONException("backend error: " + o.optString("error"));
        if (!o.has("verdict")) throw new JSONException("no verdict in response");
        List<String> flags = new ArrayList<>();
        JSONArray arr = o.optJSONArray("red_flags");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) flags.add(arr.optString(i));
        }
        return new AnalysisResult(
                o.getString("verdict"),
                o.optString("scam_type", "OTHER"),
                o.optInt("risk_score", 0),
                flags,
                o.optString("warning_ro", ""));
    }
}
