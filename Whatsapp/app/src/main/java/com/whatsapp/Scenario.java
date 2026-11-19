package com.whatsapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One Scam Shield scenario file (official JSON format: id, channel, sender, messages[]). */
public final class Scenario {
    public static final class Msg {
        public final int n;
        public final boolean fromReader;
        public final String text;

        Msg(int n, boolean fromReader, String text) {
            this.n = n;
            this.fromReader = fromReader;
            this.text = text;
        }
    }

    public final String id;
    public final String channel;
    public final String senderName;     // may be null
    public final String senderAddress;  // phone, e-mail or handle; may be null
    public final boolean knownContact;
    public final List<Msg> messages;

    private Scenario(String id, String channel, String senderName, String senderAddress, boolean knownContact, List<Msg> messages) {
        this.id = id;
        this.channel = channel;
        this.senderName = senderName;
        this.senderAddress = senderAddress;
        this.knownContact = knownContact;
        this.messages = messages;
    }

    public static Scenario parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONObject s = o.optJSONObject("sender");
        String name = s == null || s.isNull("name") ? null : s.optString("name", null);
        String addr = s == null || s.isNull("address") ? null : s.optString("address", null);
        boolean known = s != null && s.optBoolean("known_contact", false);
        List<Msg> msgs = new ArrayList<>();
        JSONArray arr = o.optJSONArray("messages");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m == null) continue;
                msgs.add(new Msg(m.optInt("n", i + 1), "reader".equals(m.optString("from")), m.optString("text", "")));
            }
        }
        return new Scenario(o.optString("id", "?"), o.optString("channel", "whatsapp"), name, addr, known, msgs);
    }

    /**
     * How the chat is titled, like a real messenger: saved contacts by name, strangers by their number.
     * If an unknown sender only has a display name (e.g. a marketplace buyer), that name is shown.
     */
    public String displayTitle() {
        if (knownContact && senderName != null) return senderName;
        if (senderAddress != null && looksLikePhone(senderAddress)) return senderAddress;
        if (senderName != null) return senderName;
        return senderAddress != null ? senderAddress : id;
    }

    public String phoneOrNull() {
        return senderAddress != null && looksLikePhone(senderAddress) ? senderAddress : null;
    }

    static boolean looksLikePhone(String s) {
        return s.trim().matches("^\\+?[0-9x][0-9x\\s\\-().]{5,}$");
    }
}
