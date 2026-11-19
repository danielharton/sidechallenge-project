package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts WhatsApp off the network without touching other apps:
 * a local VPN that ONLY WhatsApp is routed into (addAllowedApplication), and every packet it sends is dropped.
 * Nothing is forwarded anywhere and nothing is inspected or logged.
 *
 * Requires the one-time VPN consent (VpnService.prepare) granted in MainActivity.
 */
public class WhatsAppBlockVpnService extends VpnService {
    private static final String TAG = "WhatsAppBlockVpn";
    private static final String ACTION_START = "com.danielharton.scamshieldcompanion.VPN_START";
    private static final String ACTION_STOP = "com.danielharton.scamshieldcompanion.VPN_STOP";

    private static volatile boolean running = false;

    private ParcelFileDescriptor tun;
    private Thread drainThread;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        Intent i = new Intent(context, WhatsAppBlockVpnService.class).setAction(ACTION_START);
        try {
            context.startService(i);
        } catch (IllegalStateException backgroundRestricted) {
            // Android 8+ may refuse a plain background start; a foreground start is allowed while our overlay is visible.
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        }
    }

    public static void stop(Context context) {
        if (!running) return;
        Intent i = new Intent(context, WhatsAppBlockVpnService.class).setAction(ACTION_STOP);
        try {
            context.startService(i);
        } catch (IllegalStateException e) {
            Log.w(TAG, "stop via intent refused; service will stop when revoked");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopBlocking();
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground();
        if (!running) startBlocking();
        return START_NOT_STICKY; // after a crash/reboot WhatsApp must NOT stay blocked
    }

    private void goForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.VPN_NOTIFICATION_ID, Notifier.vpnNotification(this),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Notifier.VPN_NOTIFICATION_ID, Notifier.vpnNotification(this));
            }
        } catch (Exception e) {
            // An established VPN is kept alive by the system anyway.
            Log.w(TAG, "startForeground failed: " + e.getClass().getSimpleName());
        }
    }

    private void startBlocking() {
        Builder b = new Builder()
                .setSession("Scam Shield – WhatsApp oprit")
                .addAddress("10.215.77.1", 32)
                .addRoute("0.0.0.0", 0)
                .setBlocking(false); // non-blocking so the drain thread can exit and release the tun interface
        try {
            b.addAddress("fd00:5c:5d::1", 128);
            b.addRoute("::", 0);
        } catch (IllegalArgumentException ignored) {
            // device without IPv6 support in VPNs
        }

        int routed = 0;
        for (String pkg : packagesToBlock(this)) {
            try {
                b.addAllowedApplication(pkg); // only these apps enter the VPN; everything else is untouched
                routed++;
            } catch (PackageManager.NameNotFoundException notInstalled) {
                // skip
            }
        }
        if (routed == 0) {
            Log.w(TAG, "WhatsApp is not installed: nothing to block");
            stopSelf();
            return;
        }

        try {
            tun = b.establish();
        } catch (Exception e) {
            Log.w(TAG, "establish failed: " + e.getClass().getSimpleName());
        }
        if (tun == null) {
            // VPN consent missing or revoked (e.g. another VPN app took over).
            stopSelf();
            return;
        }
        running = true;

        // Not closed here: the ParcelFileDescriptor owns the fd and is closed in stopBlocking().
        final FileChannel in = new FileInputStream(tun.getFileDescriptor()).getChannel();
        drainThread = new Thread(() -> {
            ByteBuffer buf = ByteBuffer.allocate(32767);
            while (running) {
                int n;
                try {
                    buf.clear();
                    n = in.read(buf); // read and drop: WhatsApp's packets go nowhere
                } catch (IOException e) {
                    n = 0;
                }
                if (n <= 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException stop) {
                        return;
                    }
                }
            }
        }, "vpn-drain");
        drainThread.start();
    }

    /** Installed WhatsApp packages (plus, in debug builds only, an optional test package). Never empty-means-everything. */
    static List<String> packagesToBlock(Context c) {
        List<String> out = new ArrayList<>();
        for (String pkg : ScamNotificationListener.WHATSAPP_PACKAGES) {
            if (isInstalled(c, pkg)) out.add(pkg);
        }
        String debugPkg = Prefs.debugBlockPackage(c);
        if (debugPkg != null && isInstalled(c, debugPkg)) out.add(debugPkg);
        return out;
    }

    static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void stopBlocking() {
        running = false;
        if (drainThread != null) {
            drainThread.interrupt();
            try {
                drainThread.join(500);
            } catch (InterruptedException ignored) {
            }
            drainThread = null;
        }
        if (tun != null) {
            try {
                tun.close();
            } catch (IOException ignored) {
            }
            tun = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override
    public void onRevoke() {
        // User disconnected the VPN from system settings.
        stopBlocking();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopBlocking();
        super.onDestroy();
    }
}
