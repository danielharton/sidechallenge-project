package com.danielharton.scamshieldcompanion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

/** Status screen: shows which permissions are granted and lets the user grant them, plus a demo button. */
public class MainActivity extends AppCompatActivity {

    private static final String DEMO_TEXT =
            "Bună ziua! Contul dumneavoastră bancar va fi blocat în 30 de minute. "
                    + "Pentru deblocare, trimiteți-mi codul primit prin SMS și completați datele cardului pe bancadunarea-securitate.com";
    private static final String DEMO_SENDER = "Banca Dunărea";

    private TextView statusListener, statusOverlay, statusVpn, statusPost, textResult;
    private Button btnListener, btnOverlay, btnVpn, btnPost;

    private final ActivityResultLauncher<Intent> vpnConsent =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> refresh());
    private final ActivityResultLauncher<String> postPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> refresh());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        Notifier.createChannels(this);

        statusListener = findViewById(R.id.status_listener);
        statusOverlay = findViewById(R.id.status_overlay);
        statusVpn = findViewById(R.id.status_vpn);
        statusPost = findViewById(R.id.status_post);
        textResult = findViewById(R.id.text_result);
        btnListener = findViewById(R.id.btn_listener);
        btnOverlay = findViewById(R.id.btn_overlay);
        btnVpn = findViewById(R.id.btn_vpn);
        btnPost = findViewById(R.id.btn_post);

        btnListener.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        btnOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        btnVpn.setOnClickListener(v -> {
            Intent consent = VpnService.prepare(this);
            if (consent != null) vpnConsent.launch(consent);
            else refresh();
        });
        btnPost.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) postPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        });

        MaterialSwitch sw = findViewById(R.id.switch_monitoring);
        sw.setChecked(Prefs.monitoring(this));
        sw.setOnCheckedChangeListener((b, on) -> Prefs.setMonitoring(this, on));

        EditText edit = findViewById(R.id.edit_backend);
        edit.setText(Prefs.backendUrl(this));
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            Prefs.setBackendUrl(this, edit.getText().toString());
            Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_test).setOnClickListener(v -> {
            Prefs.setBackendUrl(this, edit.getText().toString());
            textResult.setText("Se verifică…");
            ScamShieldApiClient.analyze(this, "Bună, ne vedem mâine la cafea?", true, new ScamShieldApiClient.Callback() {
                @Override
                public void onResult(AnalysisResult r) {
                    textResult.setText("✓ Serverul răspunde (" + r.verdict + ").");
                }

                @Override
                public void onError(String reason) {
                    textResult.setText("✗ Serverul nu răspunde: " + reason
                            + "\nPorniți serverul pe laptop și verificați adresa.");
                }
            });
        });

        findViewById(R.id.btn_simulate).setOnClickListener(v -> simulate());
        findViewById(R.id.btn_unblock).setOnClickListener(v -> {
            AlertController.release(this);
            refresh();
        });
    }

    /** Demo: runs a synthetic scam through the real backend and the real alert path. */
    private void simulate() {
        textResult.setText("Se analizează mesajul de test…");
        IncomingMessage demo = new IncomingMessage("com.whatsapp", DEMO_SENDER, "+40 712 345 678", DEMO_TEXT, false);
        ScamShieldApiClient.analyze(this, demo.text, demo.knownContact, new ScamShieldApiClient.Callback() {
            @Override
            public void onResult(AnalysisResult r) {
                textResult.setText("Rezultat: " + r.verdict + " · " + r.riskScore + "/100 · " + r.scamType);
                if (r.isDangerous()) AlertController.raise(MainActivity.this, demo, r);
            }

            @Override
            public void onError(String reason) {
                textResult.setText("✗ Serverul nu răspunde: " + reason);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean listener = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(getPackageName());
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean vpn = VpnService.prepare(this) == null;
        boolean post = Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        show(statusListener, btnListener, listener);
        show(statusOverlay, btnOverlay, overlay);
        show(statusVpn, btnVpn, vpn);
        show(statusPost, btnPost, post);
        if (WhatsAppBlockVpnService.isRunning()) {
            textResult.setText("WhatsApp este oprit acum de Scam Shield.");
        }
    }

    private void show(TextView status, Button button, boolean granted) {
        status.setText(granted ? R.string.granted : R.string.not_granted);
        status.setTextColor(ContextCompat.getColor(this, granted ? R.color.safe_green : R.color.scam_red));
        button.setEnabled(!granted);
    }
}
