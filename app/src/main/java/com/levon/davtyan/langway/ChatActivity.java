package com.levon.davtyan.langway;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.firebase.database.ValueEventListener;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CHAT_ID     = "chat_id";
    public static final String EXTRA_OTHER_UID   = "other_uid";
    public static final String EXTRA_OTHER_NAME  = "other_name";
    public static final String EXTRA_OTHER_LANGS = "other_langs";
    public static final String EXTRA_OTHER_PHOTO = "other_photo";

    private static final int MY_BUBBLE_BG      = 0xFF00C896;
    private static final int THEIR_BUBBLE_BG   = 0xFFF0FBF8;
    private static final int MY_BUBBLE_TEXT    = 0xFFFFFFFF;
    private static final int THEIR_BUBBLE_TEXT = 0xFF0D2626;

    private String myUid;
    private String chatId;
    private LinearLayout messagesContainer;
    private NestedScrollView scrollView;
    private TextInputEditText inputField;
    private ChildEventListener messageListener;
    private DatabaseReference messagesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        myUid  = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        String otherName  = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        String otherLangs = getIntent().getStringExtra(EXTRA_OTHER_LANGS);
        String otherPhoto = getIntent().getStringExtra(EXTRA_OTHER_PHOTO);

        ((TextView) findViewById(R.id.chat_other_name)).setText(otherName);
        // Show online status in the subtitle; falls back to langs if lastSeen unavailable
        TextView statusView = findViewById(R.id.chat_other_langs);
        String otherUidFinal = getIntent().getStringExtra(EXTRA_OTHER_UID);
        statusView.setText(otherLangs != null ? otherLangs : "");
        fetchAndShowOnlineStatus(otherUidFinal, statusView);

        TextView initialsView = findViewById(R.id.chat_other_initials);
        ImageView photoView   = findViewById(R.id.chat_other_photo);
        String initials = "?";
        if (otherName != null) {
            String[] p = otherName.trim().split("\\s+");
            initials = p.length >= 2
                    ? "" + p[0].charAt(0) + p[1].charAt(0)
                    : otherName.substring(0, Math.min(2, otherName.length()));
        }
        initialsView.setText(initials.toUpperCase());
        if (otherPhoto != null && !otherPhoto.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(otherPhoto, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                photoView.setImageBitmap(bmp);
                photoView.setVisibility(View.VISIBLE);
                initialsView.setVisibility(View.GONE);
            } catch (Exception ignored) {}
        }

        messagesContainer = findViewById(R.id.chat_messages_container);
        scrollView        = findViewById(R.id.chat_scroll);
        inputField        = findViewById(R.id.chat_input);

        // ── Keyboard push-up fix ──────────────────────────────────────────────
        // Listen on the root view for both system bar AND IME (keyboard) insets.
        // When the keyboard appears the IME inset grows; we apply it to the
        // bottom of the input bar so it rises above the keyboard.
        View root = findViewById(R.id.chat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets sysBar = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime    = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            // Top bar padding: status bar height
            View topBar = findViewById(R.id.chat_top_bar);
            topBar.setPadding(topBar.getPaddingLeft(), sysBar.top + 16,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());

            // Input bar padding: keyboard height (or nav bar if keyboard hidden)
            View inputBar = findViewById(R.id.chat_input_bar);
            int bottomInset = ime.bottom > 0 ? ime.bottom : sysBar.bottom;
            inputBar.setPadding(
                    inputBar.getPaddingLeft(),
                    8,
                    inputBar.getPaddingRight(),
                    bottomInset > 0 ? bottomInset : 8
            );

            return WindowInsetsCompat.CONSUMED;
        });

        ((ImageButton) findViewById(R.id.chat_back_btn)).setOnClickListener(v -> finish());
        findViewById(R.id.chat_send_btn).setOnClickListener(v -> sendMessage());

        messagesRef = FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(chatId)
                .child("messages");

        listenForMessages();
    }

    private void sendMessage() {
        String text = inputField.getText() != null
                ? inputField.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        inputField.setText("");

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderUid", myUid);
        msg.put("text",      text);
        msg.put("timestamp", ServerValue.TIMESTAMP);

        messagesRef.push().setValue(msg);

        Map<String, Object> update = new HashMap<>();
        update.put("lastMessage",   text);
        update.put("lastTimestamp", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(chatId)
                .updateChildren(update);
    }

    private void listenForMessages() {
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String sender = snapshot.child("senderUid").getValue(String.class);
                String text   = snapshot.child("text").getValue(String.class);
                Long   ts     = snapshot.child("timestamp").getValue(Long.class);
                boolean isMe  = myUid.equals(sender);
                addBubble(text != null ? text : "", isMe, ts);
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }

            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError error) {}
        };

        messagesRef.orderByChild("timestamp").addChildEventListener(messageListener);
    }

    private void addBubble(String text, boolean isMe, Long timestampMillis) {
        // Bubble
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14f);
        bubble.setTextColor(isMe ? MY_BUBBLE_TEXT : THEIR_BUBBLE_TEXT);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setLineSpacing(0, 1.3f);
        bubble.setMaxWidth(dp(260));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isMe ? MY_BUBBLE_BG : THEIR_BUBBLE_BG);
        bg.setCornerRadii(isMe
                ? new float[]{18,18, 18,18, 4,4, 18,18}
                : new float[]{18,18, 18,18, 18,18, 4,4});
        bubble.setBackground(bg);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(dp(8), dp(2), dp(8), dp(1));

        // Timestamp
        String timeLabel = (timestampMillis != null && timestampMillis > 0)
                ? formatMessageTime(timestampMillis) : "";
        TextView tsView = new TextView(this);
        tsView.setText(timeLabel);
        tsView.setTextSize(10f);
        tsView.setTextColor(Color.parseColor("#88000000"));

        LinearLayout.LayoutParams tsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tsParams.setMargins(dp(12), 0, dp(12), dp(2));
        tsParams.gravity = isMe ? Gravity.END : Gravity.START;

        // Column: bubble on top, timestamp below, aligned to same side
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(isMe ? Gravity.END : Gravity.START);
        col.addView(bubble, bubbleParams);
        col.addView(tsView, tsParams);

        // Row: stretches full width so col can be pushed to left or right
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(2), 0, dp(2));
        row.setLayoutParams(rowParams);
        row.setGravity(isMe ? Gravity.END : Gravity.START);

        row.addView(col, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setAlpha(0f);
        row.setTranslationY(8f);
        row.animate().alpha(1f).translationY(0f)
                .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();

        messagesContainer.addView(row);
    }


    /**
     * Fetches the other user's lastSeen from Firebase.
     * Falls back to scanning chats for the most recent shared message timestamp
     * so it works for old accounts that never wrote lastSeen directly.
     */
    private void fetchAndShowOnlineStatus(String otherUid, TextView target) {
        if (otherUid == null || otherUid.isEmpty()) return;
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(otherUid)
                .child("lastSeen")
                .get()
                .addOnSuccessListener(snap -> {
                    Long lastSeen = snap.getValue(Long.class);
                    if (lastSeen != null && lastSeen > 0) {
                        target.setText(formatOnlineStatus(lastSeen));
                    } else {
                        // No lastSeen — derive it from the shared chat's lastTimestamp
                        if (chatId != null) {
                            FirebaseDatabase.getInstance()
                                    .getReference("chats")
                                    .child(chatId)
                                    .child("lastTimestamp")
                                    .get()
                                    .addOnSuccessListener(tsSnap -> {
                                        Long ts = tsSnap.getValue(Long.class);
                                        if (ts != null && ts > 0) {
                                            target.setText(formatOnlineStatus(ts));
                                            // Backfill so future loads are instant
                                            FirebaseDatabase.getInstance()
                                                    .getReference("users")
                                                    .child(otherUid)
                                                    .child("lastSeen")
                                                    .setValue(ts);
                                        }
                                        // else: leave the langs text as-is
                                    });
                        }
                    }
                });
    }

    private String formatOnlineStatus(long lastSeenMillis) {
        long diff = System.currentTimeMillis() - lastSeenMillis;
        if (diff < 60_000)      return "online now";
        if (diff < 3_600_000)   return (diff / 60_000) + "m ago";
        if (diff < 86_400_000)  return (diff / 3_600_000) + "h ago";
        long days = diff / 86_400_000;
        if (days == 1)          return "yesterday";
        if (days < 7)           return days + "d ago";
        return "a while ago";
    }

    private String formatMessageTime(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000) return "just now";
        Date d = new Date(millis);
        long diffDays = diff / 86_400_000;
        if (diffDays == 0)
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        if (diffDays == 1)
            return "Yesterday " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(d);
    }

    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) messagesRef.removeEventListener(messageListener);
    }
}