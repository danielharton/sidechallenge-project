package com.whatsapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;

import java.util.List;

/**
 * Notifications shaped like a typical messenger's:
 * one MessagingStyle notification per chat (title = contact name, or the number for strangers),
 * all in one group with a summary notification ("3 mesaje din 2 conversații").
 * That is exactly what the Scam Shield companion parses.
 */
public final class MessageNotifier {
    private static final String CHANNEL = "messages";
    private static final String GROUP = "com.whatsapp.test.MESSAGES";
    private static final int SUMMARY_ID = 1;
    private static final int HISTORY = 6;

    private MessageNotifier() {}

    public static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_HIGH);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private static int idFor(ChatStore.Conversation conv) {
        return 1000 + (conv.key.hashCode() & 0x7fffffff) % 100000;
    }

    public static void notifyIncoming(Context ctx, ChatStore.Conversation conv) {
        Context c = ctx.getApplicationContext();
        createChannel(c);
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;

        Person me = new Person.Builder().setName(c.getString(R.string.me)).setKey("me").build();
        Person.Builder sb = new Person.Builder().setName(conv.title).setKey(conv.key);
        if (conv.phone != null) sb.setUri("tel:" + conv.phone.replace(" ", ""));
        Person sender = sb.build();

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(me);
        List<ChatStore.Message> msgs = conv.messages;
        for (int i = Math.max(0, msgs.size() - HISTORY); i < msgs.size(); i++) {
            ChatStore.Message m = msgs.get(i);
            style.addMessage(m.text, m.time, m.outgoing ? null : sender); // null person = the user (me)
        }

        ChatStore.Message last = conv.last();
        Intent open = new Intent(c, ChatActivity.class).putExtra(ChatActivity.EXTRA_KEY, conv.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, idFor(conv), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(conv.title)
                .setContentText(last == null ? "" : last.text)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP)
                .setNumber(conv.unread)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addPerson(sender);

        int totalUnread = 0, chats = 0;
        NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
        for (ChatStore.Conversation cv : ChatStore.all()) {
            if (cv.unread > 0 && cv.last() != null) {
                totalUnread += cv.unread;
                chats++;
                inbox.addLine(cv.title + ": " + cv.last().text);
            }
        }
        String summaryText = totalUnread + " mesaje din " + chats + (chats == 1 ? " conversație" : " conversații");
        inbox.setSummaryText(summaryText);
        NotificationCompat.Builder summary = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(c.getString(R.string.app_name))
                .setContentText(summaryText)
                .setStyle(inbox)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true);

        try {
            nm.notify(idFor(conv), b.build());
            nm.notify(SUMMARY_ID, summary.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS denied (Android 13+)
        }
    }

    public static void cancel(Context c, ChatStore.Conversation conv) {
        NotificationManagerCompat.from(c).cancel(idFor(conv));
    }

    public static void cancelAll(Context c) {
        NotificationManagerCompat.from(c).cancelAll();
    }
}
