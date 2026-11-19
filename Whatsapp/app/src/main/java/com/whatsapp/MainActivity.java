package com.whatsapp;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

/** Chat list. Also the adb entry point: -\-es play "folder/file.json" or -\-es play_folder "folder". */
public class MainActivity extends AppCompatActivity
        implements ChatStore.Listener, ConnectionMonitor.Listener {

    private final List<ChatStore.Conversation> items = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView empty, connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        MessageNotifier.createChannel(this);
        ConnectionMonitor.start(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        empty = findViewById(R.id.empty);
        connection = findViewById(R.id.connection);
        ListView list = findViewById(R.id.list);
        adapter = new ConversationAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> startActivity(
                new Intent(this, ChatActivity.class).putExtra(ChatActivity.EXTRA_KEY, items.get(pos).key)));

        findViewById(R.id.btn_scenarios).setOnClickListener(v -> startActivity(new Intent(this, ScenariosActivity.class)));
        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            ReplayEngine.stopAll();
            ChatStore.clear();
            MessageNotifier.cancelAll(this);
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        String file = i.getStringExtra("play");
        String folder = i.getStringExtra("play_folder");
        if (file != null || folder != null) {
            ReplayEngine.playFromIntent(this, file, folder);
            i.removeExtra("play");
            i.removeExtra("play_folder");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ChatStore.addListener(this);
        ConnectionMonitor.addListener(this);
        onChanged();
        onConnectionChanged(ConnectionMonitor.isOnline());
    }

    @Override
    protected void onStop() {
        ChatStore.removeListener(this);
        ConnectionMonitor.removeListener(this);
        super.onStop();
    }

    @Override
    public void onChanged() {
        items.clear();
        items.addAll(ChatStore.all());
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConnectionChanged(boolean online) {
        connection.setText(online ? R.string.online : R.string.offline);
        connection.setBackgroundColor(ContextCompat.getColor(this, online ? R.color.brand_dark : R.color.offline));
    }

    private class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int pos) {
            return items.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_conversation, parent, false);
            ChatStore.Conversation c = items.get(pos);
            ChatStore.Message last = c.last();
            String initial = c.title.isEmpty() ? "?" : c.title.substring(0, 1).toUpperCase();
            ((TextView) v.findViewById(R.id.avatar)).setText(Character.isDigit(initial.charAt(0)) || initial.equals("+") ? "👤" : initial);
            ((TextView) v.findViewById(R.id.title)).setText(c.title);
            ((TextView) v.findViewById(R.id.preview)).setText(last == null ? "" : (last.outgoing ? "Eu: " : "") + last.text);
            ((TextView) v.findViewById(R.id.time)).setText(last == null ? "" : DateFormat.format("HH:mm", last.time));
            TextView unread = v.findViewById(R.id.unread);
            unread.setText(String.valueOf(c.unread));
            unread.setVisibility(c.unread > 0 ? View.VISIBLE : View.INVISIBLE);
            return v;
        }
    }
}
