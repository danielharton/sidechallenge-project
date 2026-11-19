package com.whatsapp;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Pick a bundled scenario (assets/scenarios/<folder>/*.json), a whole folder, or import JSON files. */
public class ScenariosActivity extends AppCompatActivity {

    private static final class Entry {
        final String folder;      // header row when file == null && scenario == null
        final String file;        // bundled asset path
        final Scenario scenario;  // imported scenario

        Entry(String folder, String file, Scenario scenario) {
            this.folder = folder;
            this.file = file;
            this.scenario = scenario;
        }

        boolean isHeader() {
            return file == null && scenario == null;
        }
    }

    /** Imported files stay available until the app process ends. */
    private static final List<Scenario> IMPORTED = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private BaseAdapter adapter;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), this::onPicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scenarios);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sc_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        EditText server = findViewById(R.id.server);
        EditText delay = findViewById(R.id.delay);
        server.setText(Settings.serverUrl(this));
        delay.setText(String.valueOf(Settings.delaySeconds(this)));
        findViewById(R.id.save).setOnClickListener(v -> {
            Settings.setServerUrl(this, server.getText().toString());
            try {
                Settings.setDelaySeconds(this, Integer.parseInt(delay.getText().toString().trim()));
            } catch (NumberFormatException ignored) {
            }
            Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.import_json).setOnClickListener(v ->
                picker.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}));

        ListView list = findViewById(R.id.sc_list);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onClick(entries.get(pos)));
        rebuild();
    }

    private void rebuild() {
        entries.clear();
        if (!IMPORTED.isEmpty()) {
            entries.add(new Entry("importate", null, null));
            for (Scenario s : IMPORTED) entries.add(new Entry("importate", null, s));
        }
        for (String folder : ReplayEngine.assetFolders(this)) {
            entries.add(new Entry(folder, null, null));
            for (String f : ReplayEngine.assetFiles(this, folder)) entries.add(new Entry(folder, f, null));
        }
        adapter.notifyDataSetChanged();
    }

    private void onClick(Entry e) {
        try {
            if (e.isHeader()) {
                List<Scenario> all = new ArrayList<>();
                if ("importate".equals(e.folder)) all.addAll(IMPORTED);
                else for (String f : ReplayEngine.assetFiles(this, e.folder)) all.add(ReplayEngine.loadAsset(this, f));
                ReplayEngine.playAll(this, all);
                Toast.makeText(this, "Rulez " + all.size() + " scenarii (câte unul la ~20 s)", Toast.LENGTH_LONG).show();
            } else {
                Scenario s = e.scenario != null ? e.scenario : ReplayEngine.loadAsset(this, e.file);
                ReplayEngine.play(this, s, 1500);
                Toast.makeText(this, "Rulez " + s.id + " – mesajele vin ca notificări", Toast.LENGTH_SHORT).show();
            }
            finish(); // back to the chat list (or press Home to watch the notifications)
        } catch (Exception ex) {
            Toast.makeText(this, "Fișier invalid: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onPicked(List<Uri> uris) {
        int ok = 0;
        for (Uri u : uris) {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                IMPORTED.add(Scenario.parse(ReplayEngine.readAll(in)));
                ok++;
            } catch (Exception e) {
                Toast.makeText(this, "Nu pot citi un fișier: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        if (ok > 0) Toast.makeText(this, ok + " scenarii importate", Toast.LENGTH_SHORT).show();
        rebuild();
    }

    private class EntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int pos) {
            return entries.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            TextView t = v instanceof TextView ? (TextView) v : new TextView(ScenariosActivity.this);
            Entry e = entries.get(pos);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            t.setPadding(pad, pad, pad, pad);
            if (e.isHeader()) {
                t.setText("▶  " + e.folder + "  –  " + getString(R.string.play_folder));
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextSize(16);
                t.setBackgroundColor(0xFFE8EAF6);
                t.setTextColor(ContextCompat.getColor(ScenariosActivity.this, R.color.brand_dark));
            } else {
                String label;
                if (e.scenario != null) {
                    label = e.scenario.id + "   " + e.scenario.displayTitle() + " · " + e.scenario.messages.size() + " mesaje";
                } else {
                    label = e.file.substring(e.file.indexOf('/') + 1).replace(".json", "");
                }
                t.setText("     " + label);
                t.setTypeface(Typeface.DEFAULT);
                t.setTextSize(17);
                t.setBackgroundColor(0x00000000);
                t.setTextColor(ContextCompat.getColor(ScenariosActivity.this, R.color.ink));
            }
            return t;
        }
    }
}
