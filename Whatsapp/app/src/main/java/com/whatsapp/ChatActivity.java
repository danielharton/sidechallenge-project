package com.whatsapp;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** One chat. Incoming on the left, the user's messages on the right; ⏳ while offline, ✓ when sent. */
public class ChatActivity extends AppCompatActivity implements ChatStore.Listener, ConnectionMonitor.Listener {
    public static final String EXTRA_KEY = "conversation_key";
    private static String openKey; // chat currently on screen: no notification for it

    private ChatStore.Conversation conv;
    private BaseAdapter adapter;
    private TextView connection;

    public static boolean isOpen(String key) {
        return key.equals(openKey);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chat_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        conv = ChatStore.get(getIntent().getStringExtra(EXTRA_KEY));
        if (conv == null) {
            finish(); // process was restarted: chats live only in memory
            return;
        }
        ((TextView) findViewById(R.id.chat_title)).setText(conv.title);
        ((TextView) findViewById(R.id.chat_subtitle)).setText(conv.knownContact ? "în agendă"
                : (conv.phone != null ? "număr necunoscut" : "contact necunoscut"));
        connection = findViewById(R.id.chat_connection);

        ListView list = findViewById(R.id.messages);
        adapter = new MessageAdapter();
        list.setAdapter(adapter);

        EditText input = findViewById(R.id.input);
        findViewById(R.id.send).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            ChatStore.add(conv, text, true, !ConnectionMonitor.isOnline());
            input.setText("");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (conv == null) return;
        openKey = conv.key;
        conv.unread = 0;
        MessageNotifier.cancel(this, conv);
        ChatStore.addListener(this);
        ConnectionMonitor.addListener(this);
        onConnectionChanged(ConnectionMonitor.isOnline());
        ChatStore.changed();
    }

    @Override
    protected void onPause() {
        openKey = null;
        ChatStore.removeListener(this);
        ConnectionMonitor.removeListener(this);
        super.onPause();
    }

    @Override
    public void onChanged() {
        if (conv != null) conv.unread = 0;
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onConnectionChanged(boolean online) {
        connection.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    private class MessageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return conv.messages.size();
        }

        @Override
        public Object getItem(int pos) {
            return conv.messages.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_message, parent, false);
            ChatStore.Message m = conv.messages.get(pos);
            LinearLayout row = v.findViewById(R.id.row);
            row.setGravity(m.outgoing ? Gravity.END : Gravity.START);
            v.findViewById(R.id.bubble).setBackgroundResource(m.outgoing ? R.drawable.bubble_out : R.drawable.bubble_in);
            ((TextView) v.findViewById(R.id.text)).setText(m.text);
            String time = DateFormat.format("HH:mm", m.time).toString();
            ((TextView) v.findViewById(R.id.meta)).setText(m.outgoing ? time + (m.pending ? "  ⏳" : "  ✓") : time);
            return v;
        }
    }
}
