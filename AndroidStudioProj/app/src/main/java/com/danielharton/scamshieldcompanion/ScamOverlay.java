package com.danielharton.scamshieldcompanion;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * Full-screen warning drawn above every app (TYPE_APPLICATION_OVERLAY).
 * The button stays grey and disabled for 15 seconds, then fades to green; pressing it
 * gives WhatsApp its network back and closes the warning.
 */
public final class ScamOverlay {
    private static final String TAG = "ScamOverlay";
    private static final long WAIT_MS = 15_000;

    private static View view;
    private static CountDownTimer timer;

    private ScamOverlay() {}

    public static boolean isShowing() {
        return view != null;
    }

    public static void show(Context appContext, IncomingMessage msg, AnalysisResult result, boolean networkBlocked) {
        if (view != null) {
            update(msg, result, networkBlocked);
            return;
        }
        Context ctx = new ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        View root = LayoutInflater.from(ctx).inflate(R.layout.overlay_warning, null);

        boolean scam = "SCAM".equals(result.verdict);
        int accent = ContextCompat.getColor(ctx, scam ? R.color.scam_red : R.color.warn_amber);

        TextView title = root.findViewById(R.id.overlay_title);
        title.setText(scam ? R.string.overlay_title : R.string.overlay_title_suspicious);
        title.setTextColor(accent);
        ((TextView) root.findViewById(R.id.overlay_icon)).setTextColor(accent);
        GradientDrawable card = (GradientDrawable) root.findViewById(R.id.overlay_card).getBackground().mutate();
        card.setStroke(dp(ctx, 5), accent);

        ((TextView) root.findViewById(R.id.overlay_score)).setText(
                "Scor de risc " + result.riskScore + "/100 · " + result.scamType);
        ((TextView) root.findViewById(R.id.overlay_warning)).setText(
                TextUtils.isEmpty(result.warningRo) ? "Mesajul are semne de înșelătorie. Nu răspundeți și nu dați date." : result.warningRo);
        ((TextView) root.findViewById(R.id.overlay_block_status)).setText(
                networkBlocked ? R.string.overlay_blocked : R.string.overlay_not_blocked);
        ((TextView) root.findViewById(R.id.overlay_sender)).setText(msg.senderName);
        ((TextView) root.findViewById(R.id.overlay_phone)).setText(
                msg.phoneNumber != null ? msg.phoneNumber : (msg.knownContact ? "salvat în agendă (WhatsApp nu arată numărul)" : "necunoscut"));
        ((TextView) root.findViewById(R.id.overlay_message)).setText(msg.text);
        ((TextView) root.findViewById(R.id.overlay_flags)).setText(TextUtils.join("  ", result.redFlags));

        ProgressBar progress = root.findViewById(R.id.overlay_progress);
        Button button = root.findViewById(R.id.overlay_button);
        button.setEnabled(false);
        button.setAlpha(0.85f);
        button.setText(ctx.getString(R.string.overlay_wait, (int) (WAIT_MS / 1000)));
        button.setOnClickListener(v -> AlertController.release(appContext));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.dimAmount = 0.6f;

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            Log.w(TAG, "cannot draw overlay: " + e.getClass().getSimpleName());
            Notifier.showWarning(appContext);
            return;
        }
        view = root;

        timer = new CountDownTimer(WAIT_MS, 100) {
            @Override
            public void onTick(long left) {
                progress.setProgress((int) (WAIT_MS - left));
                int secs = (int) Math.ceil(left / 1000.0);
                button.setText(ctx.getString(R.string.overlay_wait, secs));
            }

            @Override
            public void onFinish() {
                progress.setProgress((int) WAIT_MS);
                activate(ctx, button);
            }
        }.start();
    }

    /** Grey -> green colour transition, then the button becomes clickable. */
    private static void activate(Context ctx, Button button) {
        int from = ContextCompat.getColor(ctx, R.color.button_disabled);
        int to = ContextCompat.getColor(ctx, R.color.button_active);
        GradientDrawable bg = (GradientDrawable) button.getBackground().mutate();
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(700);
        anim.addUpdateListener(a -> {
            bg.setColor((Integer) a.getAnimatedValue());
            button.setAlpha(0.85f + 0.15f * a.getAnimatedFraction());
        });
        anim.start();
        button.setText(R.string.overlay_continue);
        button.setEnabled(true);
        button.animate().scaleX(1.04f).scaleY(1.04f).setDuration(250)
                .withEndAction(() -> button.animate().scaleX(1f).scaleY(1f).setDuration(250).start())
                .start();
    }

    public static void update(IncomingMessage msg, AnalysisResult result, boolean networkBlocked) {
        if (view == null) return;
        Context ctx = view.getContext();
        boolean scam = "SCAM".equals(result.verdict);
        int accent = ContextCompat.getColor(ctx, scam ? R.color.scam_red : R.color.warn_amber);

        TextView title = view.findViewById(R.id.overlay_title);
        title.setText(scam ? R.string.overlay_title : R.string.overlay_title_suspicious);
        title.setTextColor(accent);
        ((TextView) view.findViewById(R.id.overlay_icon)).setTextColor(accent);
        GradientDrawable card = (GradientDrawable) view.findViewById(R.id.overlay_card).getBackground().mutate();
        card.setStroke(dp(ctx, 5), accent);

        ((TextView) view.findViewById(R.id.overlay_score)).setText(
                "Scor de risc " + result.riskScore + "/100 · " + result.scamType);
        ((TextView) view.findViewById(R.id.overlay_warning)).setText(
                TextUtils.isEmpty(result.warningRo) ? "Mesajul are semne de înșelătorie. Nu răspundeți și nu dați date." : result.warningRo);
        ((TextView) view.findViewById(R.id.overlay_block_status)).setText(
                networkBlocked ? R.string.overlay_blocked : R.string.overlay_not_blocked);
        ((TextView) view.findViewById(R.id.overlay_sender)).setText(msg.senderName);
        ((TextView) view.findViewById(R.id.overlay_phone)).setText(
                msg.phoneNumber != null ? msg.phoneNumber : (msg.knownContact ? "salvat în agendă (WhatsApp nu arată numărul)" : "necunoscut"));
        ((TextView) view.findViewById(R.id.overlay_message)).setText(msg.text);
        ((TextView) view.findViewById(R.id.overlay_flags)).setText(TextUtils.join("  ", result.redFlags));
    }

    public static void dismiss() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (view != null) {
            try {
                WindowManager wm = (WindowManager) view.getContext().getApplicationContext()
                        .getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(view);
            } catch (Exception ignored) {
            }
            view = null;
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
