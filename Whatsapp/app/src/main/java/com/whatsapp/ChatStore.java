package com.whatsapp;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory chats (nothing is written to disk). All calls on the main thread. */
public final class ChatStore {
    public static final class Message {
        public final String text;
        public final boolean outgoing;
        public final long time;
        public boolean pending; // outgoing message waiting for a connection

        Message(String text, boolean outgoing, long time, boolean pending) {
            this.text = text;
            this.outgoing = outgoing;
            this.time = time;
            this.pending = pending;
        }
    }

    public static final class Conversation {
        public final String key;
        public final String title;
        public final String phone;       // null if unknown
        public final boolean knownContact;
        public final List<Message> messages = new ArrayList<>();
        public int unread;

        Conversation(String key, String title, String phone, boolean knownContact) {
            this.key = key;
            this.title = title;
            this.phone = phone;
            this.knownContact = knownContact;
        }

        public Message last() {
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        }
    }

    public interface Listener {
        void onChanged();
    }

    private static final Map<String, Conversation> CONVERSATIONS = new LinkedHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ChatStore() {}

    public static Conversation getOrCreate(String key, String title, String phone, boolean known) {
        Conversation c = CONVERSATIONS.get(key);
        if (c == null) {
            c = new Conversation(key, title, phone, known);
            CONVERSATIONS.put(key, c);
        }
        return c;
    }

    public static Conversation get(String key) {
        return CONVERSATIONS.get(key);
    }

    /** Most recent conversation first. */
    public static List<Conversation> all() {
        List<Conversation> out = new ArrayList<>(CONVERSATIONS.values());
        out.sort((a, b) -> Long.compare(b.last() == null ? 0 : b.last().time, a.last() == null ? 0 : a.last().time));
        return out;
    }

    public static Message add(Conversation c, String text, boolean outgoing, boolean pending) {
        Message m = new Message(text, outgoing, System.currentTimeMillis(), pending);
        c.messages.add(m);
        if (!outgoing) c.unread++;
        changed();
        return m;
    }

    public static void clear() {
        CONVERSATIONS.clear();
        changed();
    }

    public static void changed() {
        MAIN.post(() -> {
            for (Listener l : LISTENERS) l.onChanged();
        });
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }
}
