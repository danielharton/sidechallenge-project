package com.danielharton.scamshieldcompanion;

import android.content.Context;
import android.net.VpnService;
import android.os.Build;
import android.provider.Settings;

/** What happens when the backend says SCAM or SUSPICIOUS. Called on the main thread. */
public final class AlertController {
    private AlertController() {}

    public static void raise(Context context, IncomingMessage msg, AnalysisResult result) {
        Context app = context.getApplicationContext();

        // Only claim "WhatsApp is blocked" when there is something to block and we may start a VPN.
        boolean vpnAllowed = VpnService.prepare(app) == null
                && !WhatsAppBlockVpnService.packagesToBlock(app).isEmpty();
        boolean overlayAllowed = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(app);

        if (overlayAllowed) {
            // Show the window first: a visible overlay also lets us start the VPN service from the background.
            ScamOverlay.show(app, msg, result, vpnAllowed);
        } else {
            Notifier.showWarning(app);
        }
        if (vpnAllowed) {
            WhatsAppBlockVpnService.start(app);
        }
    }

    /** Called by the overlay button and by the "unblock" button in MainActivity. */
    public static void release(Context context) {
        Context app = context.getApplicationContext();
        WhatsAppBlockVpnService.stop(app);
        ScamOverlay.dismiss();
        Notifier.cancelWarning(app);
    }
}
