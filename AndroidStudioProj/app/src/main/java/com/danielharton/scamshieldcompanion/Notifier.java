package com.danielharton.scamshieldcompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Scam Shield's own notifications. They never contain the original message text. */
public final class Notifier {
    public static final int VPN_NOTIFICATION_ID = 41;
    private static final int WARNING_NOTIFICATION_ID = 42;
    private static final String CH_WARNINGS = "scam_warnings";
    private static final String CH_VPN = "whatsapp_block";

    private Notifier() {}

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel warn = new NotificationChannel(CH_WARNINGS,
                c.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel vpn = new NotificationChannel(CH_VPN,
                c.getString(R.string.channel_vpn), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(warn);
        nm.createNotificationChannel(vpn);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Notification vpnNotification(Context c) {
        createChannels(c);
        return new NotificationCompat.Builder(c, CH_VPN)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(c.getString(R.string.vpn_notification_title))
                .setContentText(c.getString(R.string.vpn_notification_text))
                .setContentIntent(openApp(c))
                .setOngoing(true)
                .build();
    }

    /** Fallback when the "draw over other apps" permission is missing. */
    public static void showWarning(Context c) {
        createChannels(c);
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return;
        Notification n = new NotificationCompat.Builder(c, CH_WARNINGS)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(c.getString(R.string.warning_notification_title))
                .setContentText(c.getString(R.string.warning_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(openApp(c))
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(WARNING_NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS revoked
        }
    }

    public static void cancelWarning(Context c) {
        NotificationManagerCompat.from(c).cancel(WARNING_NOTIFICATION_ID);
    }
}
