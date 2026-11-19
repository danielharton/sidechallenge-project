package com.danielharton.scamshieldcompanion;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Receives notifications only after the user enables "Notification access" for Scam Shield.
 * Looks only at WhatsApp, takes the newest message, asks the backend, and raises the alarm on SCAM/SUSPICIOUS.
 */
public class ScamNotificationListener extends NotificationListenerService {
    private static final String TAG = "ScamListener";

    public static final Set<String> WHATSAPP_PACKAGES =
            new HashSet<>(Arrays.asList("com.whatsapp", "com.whatsapp.w4b"));

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9xX*][0-9xX*\\s\\-().]{6,}$");
    // WhatsApp status/summary lines that are not real messages.
    private static final Pattern NOT_A_MESSAGE = Pattern.compile(
            "(?i)^(\\d+ (mesaje|messages|new messages|mesaje noi).*|.*(checking for new messages|se caută mesaje noi|backup|în desfășurare).*)$");

    /** In-memory de-duplication: WhatsApp re-posts the same notification many times within seconds. */
    private static final long DEDUP_WINDOW_MS = 60_000;
    private static final Map<String, Long> SEEN = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 300;
        }
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (Exception e) {
            // Never let a strange notification crash the listener.
            Log.w(TAG, "skipped notification: " + e.getClass().getSimpleName());
        }
    }

    private void handle(StatusBarNotification sbn) {
        if (!Prefs.monitoring(this)) return;
        String pkg = sbn.getPackageName();
        if (getPackageName().equals(pkg)) return;              // never analyse our own warnings
        if (!WHATSAPP_PACKAGES.contains(pkg)) return;          // only WhatsApp is sent anywhere

        Notification n = sbn.getNotification();

        IncomingMessage msg = extract(pkg, n);
        if (msg == null) return;

        String key = pkg + "|" + msg.senderName + "|" + msg.text;
        long now = System.currentTimeMillis();
        synchronized (SEEN) {
            Long seenAt = SEEN.get(key);
            if (seenAt != null && now - seenAt < DEDUP_WINDOW_MS) return; // same message re-posted
            SEEN.put(key, now);
        }

        ScamShieldApiClient.analyze(this, msg.text, msg.knownContact, new ScamShieldApiClient.Callback() {
            @Override
            public void onResult(AnalysisResult result) {
                if (result.isDangerous()) {
                    AlertController.raise(ScamNotificationListener.this, msg, result);
                }
            }

            @Override
            public void onError(String reason) {
                // Backend down: no alert, but also no "SAFE". Allow a retry next time the same text appears.
                synchronized (SEEN) {
                    SEEN.remove(key);
                }
            }
        });
    }

    /** Full conversation from MessagingStyle when available; otherwise title + (big) text. */
    static IncomingMessage extract(String pkg, Notification n) {
        Bundle extras = n.extras;
        if (extras == null) return null;
        String title = str(extras.getCharSequence(Notification.EXTRA_TITLE));

        String sender = null;
        String text = null;
        String phone = null;
        boolean hasIncoming = false;

        NotificationCompat.MessagingStyle style =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n);
        if (style != null) {
            List<NotificationCompat.MessagingStyle.Message> msgs = style.getMessages();
            if (!msgs.isEmpty()) {
                StringBuilder conv = new StringBuilder();
                for (NotificationCompat.MessagingStyle.Message m : msgs) {
                    CharSequence cs = m.getText();
                    if (cs == null) continue;
                    String line = cs.toString().trim();
                    if (line.isEmpty() || NOT_A_MESSAGE.matcher(line).matches()) continue;

                    boolean isUser = isUserMessage(style, m);
                    if (!isUser) {
                        hasIncoming = true;
                        Person p = m.getPerson();
                        if (p != null) {
                            if (sender == null && p.getName() != null) {
                                sender = str(p.getName());
                            }
                            if (phone == null && p.getUri() != null) {
                                phone = phoneFromUri(p.getUri());
                            }
                        }
                    }

                    String singleLine = line.replace('\r', ' ').replace('\n', ' ').trim();
                    if (conv.length() > 0) conv.append("\n");
                    if (isUser) {
                        conv.append("Eu: ").append(singleLine);
                    } else {
                        conv.append(singleLine);
                    }
                }

                if (conv.length() > 0) {
                    text = conv.toString();
                }

                if (sender == null && style.getConversationTitle() != null) {
                    sender = str(style.getConversationTitle());
                }
            }
        }

        CharSequence big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String fallbackText = str(big != null ? big : extras.getCharSequence(Notification.EXTRA_TEXT));
        if (fallbackText == null) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                StringBuilder joined = new StringBuilder();
                for (CharSequence line : lines) {
                    String value = str(line);
                    if (value == null || value.trim().isEmpty()
                            || NOT_A_MESSAGE.matcher(value.trim()).matches()) continue;
                    if (joined.length() > 0) joined.append("\n");
                    joined.append(value.trim());
                }
                fallbackText = joined.length() == 0 ? null : joined.toString();
            }
        }
        // Only the user's own replies (no incoming line): fall back to the notification text.
        if (text == null || !hasIncoming) {
            text = fallbackText;
        }
        if (sender == null) sender = title;
        if (text == null || text.trim().isEmpty() || NOT_A_MESSAGE.matcher(text.trim()).matches()) return null;

        if (phone == null) {
            String[] people = extras.getStringArray(Notification.EXTRA_PEOPLE);
            if (people != null) {
                for (String uri : people) {
                    phone = phoneFromUri(uri);
                    if (phone != null) break;
                }
            }
        }
        boolean senderIsNumber = sender != null && PHONE.matcher(sender.trim()).matches();
        if (senderIsNumber) {
            phone = sender.trim();
        } else if (phone == null && sender != null && PHONE.matcher(sender.trim()).matches()) {
            phone = sender.trim();
        }

        // WhatsApp shows a name for saved contacts and the raw number for strangers.
        boolean known = sender != null && !senderIsNumber;
        return new IncomingMessage(pkg, sender == null ? "Necunoscut" : sender, phone, text.trim(), known);
    }

    private static boolean isUserMessage(NotificationCompat.MessagingStyle style, NotificationCompat.MessagingStyle.Message m) {
        Person p = m.getPerson();
        if (p == null) return true;
        Person user = style.getUser();
        if (user != null) {
            if (p.equals(user)) return true;
            if (p.getKey() != null && p.getKey().equals(user.getKey())) return true;
            CharSequence uName = user.getName();
            CharSequence pName = p.getName();
            if (uName != null && pName != null && uName.toString().equalsIgnoreCase(pName.toString())) return true;
        }
        CharSequence name = p.getName();
        if (name != null) {
            String s = name.toString().trim();
            if ("eu".equalsIgnoreCase(s) || "me".equalsIgnoreCase(s) || "you".equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private static String phoneFromUri(String uri) {
        if (uri != null && uri.startsWith("tel:")) return uri.substring(4);
        return null;
    }

    private static String str(CharSequence cs) {
        return cs == null ? null : cs.toString();
    }
}
