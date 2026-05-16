package com.levon.davtyan.langway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.File;
import java.io.FileInputStream;
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

    private static final int REQUEST_RECORD_AUDIO = 101;

    private static final int MY_BUBBLE_BG      = 0xFF00C896;
    private static final int THEIR_BUBBLE_BG   = 0xFFF0FBF8;
    private static final int MY_BUBBLE_TEXT    = 0xFFFFFFFF;
    private static final int THEIR_BUBBLE_TEXT = 0xFF0D2626;
    private static final int AUDIO_MY_BG       = 0xFF00A87A;
    private static final int AUDIO_THEIR_BG    = 0xFFE0F5F0;

    private String myUid;
    private String chatId;
    private LinearLayout  messagesContainer;
    private NestedScrollView scrollView;
    private TextInputEditText inputField;
    private MaterialButton sendBtn, micBtn;
    private LinearLayout recordingBar;
    private TextView recordingTimer;
    private ChildEventListener messageListener;
    private DatabaseReference messagesRef;

    // Recording state
    private MediaRecorder mediaRecorder;
    private File          audioFile;
    private boolean       isRecording = false;
    private long          recordingStartMs;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable      timerRunnable;

    // Playback
    private MediaPlayer   currentPlayer;
    private ImageView     currentPlayBtn;

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
        sendBtn           = findViewById(R.id.chat_send_btn);
        micBtn            = findViewById(R.id.chat_mic_btn);
        recordingBar      = findViewById(R.id.chat_recording_bar);
        recordingTimer    = findViewById(R.id.chat_recording_timer);

        // Keyboard inset fix
        View root = findViewById(R.id.chat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets sysBar = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime    = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            View topBar   = findViewById(R.id.chat_top_bar);
            topBar.setPadding(topBar.getPaddingLeft(), sysBar.top + 16,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            View inputBar = findViewById(R.id.chat_input_bar);
            int bottomInset = ime.bottom > 0 ? ime.bottom : sysBar.bottom;
            inputBar.setPadding(inputBar.getPaddingLeft(), 8,
                    inputBar.getPaddingRight(), bottomInset > 0 ? bottomInset : 8);
            return WindowInsetsCompat.CONSUMED;
        });

        ((ImageButton) findViewById(R.id.chat_back_btn)).setOnClickListener(v -> finish());
        sendBtn.setOnClickListener(v -> sendMessage());
        setupMicButton();

        // Toggle send/mic based on text content
        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                boolean hasText = s.length() > 0;
                sendBtn.setVisibility(hasText ? View.VISIBLE : View.GONE);
                micBtn.setVisibility(hasText ? View.GONE : View.VISIBLE);
            }
        });

        messagesRef = FirebaseDatabase.getInstance()
                .getReference("chats").child(chatId).child("messages");

        listenForMessages();
    }

    // ── Send text ─────────────────────────────────────────────────────────────
    private void sendMessage() {
        String text = inputField.getText() != null
                ? inputField.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        inputField.setText("");

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderUid", myUid);
        msg.put("text",      text);
        msg.put("type",      "text");
        msg.put("timestamp", ServerValue.TIMESTAMP);
        messagesRef.push().setValue(msg);

        Map<String, Object> update = new HashMap<>();
        update.put("lastMessage",   "📝 " + text);
        update.put("lastTimestamp", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance().getReference("chats")
                .child(chatId).updateChildren(update);
    }

    // ── Mic button: hold to record ────────────────────────────────────────────
    private void setupMicButton() {
        micBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    checkPermissionAndStartRecording();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isRecording) stopAndSendRecording();
                    return true;
            }
            return false;
        });
    }

    private void checkPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "Microphone permission required for voice messages",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        try {
            audioFile = new File(getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            recordingStartMs = System.currentTimeMillis();

            // Show recording bar with pulsing dot
            recordingBar.setVisibility(View.VISIBLE);
            micBtn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#FFFF3B30")));

            // Update timer every second
            timerRunnable = new Runnable() {
                @Override public void run() {
                    if (!isRecording) return;
                    long secs = (System.currentTimeMillis() - recordingStartMs) / 1000;
                    recordingTimer.setText(String.format(Locale.getDefault(),
                            "Recording… %d:%02d", secs / 60, secs % 60));
                    timerHandler.postDelayed(this, 500);
                }
            };
            timerHandler.post(timerRunnable);

        } catch (Exception e) {
            Toast.makeText(this, "Could not start recording", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAndSendRecording() {
        if (!isRecording || mediaRecorder == null) return;
        isRecording = false;
        timerHandler.removeCallbacks(timerRunnable);
        recordingBar.setVisibility(View.GONE);
        micBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_green)));

        long durationMs = System.currentTimeMillis() - recordingStartMs;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        } catch (Exception e) {
            audioFile = null;
            return;
        }

        // Minimum 1 second to avoid accidental taps
        if (durationMs < 1000) {
            Toast.makeText(this, "Hold to record a voice message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Encode audio to base64 and upload
        try {
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] bytes = new byte[(int) audioFile.length()];
            fis.read(bytes);
            fis.close();
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            Map<String, Object> msg = new HashMap<>();
            msg.put("senderUid",  myUid);
            msg.put("type",       "audio");
            msg.put("audioData",  b64);
            msg.put("durationMs", durationMs);
            msg.put("timestamp",  ServerValue.TIMESTAMP);
            messagesRef.push().setValue(msg);

            Map<String, Object> update = new HashMap<>();
            update.put("lastMessage",   "🎤 Voice message");
            update.put("lastTimestamp", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance().getReference("chats")
                    .child(chatId).updateChildren(update);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to send voice message", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Listen for all messages ───────────────────────────────────────────────
    private void listenForMessages() {
        messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String sender = snapshot.child("senderUid").getValue(String.class);
                String type   = snapshot.child("type").getValue(String.class);
                Long   ts     = snapshot.child("timestamp").getValue(Long.class);
                boolean isMe  = myUid.equals(sender);

                if ("audio".equals(type)) {
                    String audioData  = snapshot.child("audioData").getValue(String.class);
                    Long   durationMs = snapshot.child("durationMs").getValue(Long.class);
                    addAudioBubble(audioData, durationMs != null ? durationMs : 0, isMe, ts);
                } else {
                    String text = snapshot.child("text").getValue(String.class);
                    addTextBubble(text != null ? text : "", isMe, ts);
                }
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }

            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError error) {}
        };
        messagesRef.orderByChild("timestamp").addChildEventListener(messageListener);
    }

    // ── Text bubble ──────────────────────────────────────────────────────────
    private void addTextBubble(String text, boolean isMe, Long timestampMillis) {
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

        addToRow(bubble, isMe, timestampMillis);
    }

    // ── Audio bubble ─────────────────────────────────────────────────────────
    private void addAudioBubble(String audioDataB64, long durationMs, boolean isMe, Long timestampMillis) {
        // Container
        LinearLayout audioBubble = new LinearLayout(this);
        audioBubble.setOrientation(LinearLayout.HORIZONTAL);
        audioBubble.setGravity(Gravity.CENTER_VERTICAL);
        audioBubble.setPadding(dp(10), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(isMe ? AUDIO_MY_BG : AUDIO_THEIR_BG);
        bg.setCornerRadii(isMe
                ? new float[]{18,18, 18,18, 4,4, 18,18}
                : new float[]{18,18, 18,18, 18,18, 4,4});
        audioBubble.setBackground(bg);

        // Play/Pause button
        ImageView playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_play);
        playBtn.setColorFilter(isMe ? Color.WHITE : Color.parseColor("#00C896"));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        playParams.setMarginEnd(dp(8));
        audioBubble.addView(playBtn, playParams);

        // Waveform bars (static decorative bars — real amplitude not available from b64)
        LinearLayout waveform = new LinearLayout(this);
        waveform.setOrientation(LinearLayout.HORIZONTAL);
        waveform.setGravity(Gravity.CENTER_VERTICAL);
        int[] barHeights = {10, 16, 22, 14, 28, 18, 24, 12, 20, 26, 14, 18, 22, 10, 16};
        int barColor = isMe ? Color.argb(180, 255, 255, 255) : Color.parseColor("#00C896");
        for (int h : barHeights) {
            View bar = new View(this);
            bar.setBackgroundColor(barColor);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(3), dp(h));
            bp.setMargins(dp(1), 0, dp(1), 0);
            waveform.addView(bar, bp);
        }
        LinearLayout.LayoutParams wfParams = new LinearLayout.LayoutParams(0, dp(32));
        wfParams.weight = 1;
        audioBubble.addView(waveform, wfParams);

        // Duration label
        TextView durView = new TextView(this);
        durView.setText(formatDuration(durationMs));
        durView.setTextSize(11f);
        durView.setTextColor(isMe ? Color.argb(200, 255, 255, 255) : Color.parseColor("#009970"));
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        durParams.setMarginStart(dp(8));
        audioBubble.addView(durView, durParams);

        // Playback logic
        playBtn.setOnClickListener(v -> {
            if (currentPlayer != null && currentPlayer.isPlaying() && currentPlayBtn == playBtn) {
                currentPlayer.pause();
                playBtn.setImageResource(R.drawable.ic_play);
                playBtn.setColorFilter(isMe ? Color.WHITE : Color.parseColor("#00C896"));
                return;
            }
            if (currentPlayer != null) {
                currentPlayer.stop();
                currentPlayer.release();
                currentPlayer = null;
                if (currentPlayBtn != null) {
                    currentPlayBtn.setImageResource(R.drawable.ic_play);
                    currentPlayBtn.setColorFilter(isMe ? Color.WHITE : Color.parseColor("#00C896"));
                }
            }
            if (audioDataB64 == null || audioDataB64.isEmpty()) return;
            try {
                byte[] bytes = Base64.decode(audioDataB64, Base64.DEFAULT);
                File tmp = new File(getCacheDir(), "play_" + System.currentTimeMillis() + ".m4a");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp);
                fos.write(bytes); fos.close();

                currentPlayer = new MediaPlayer();
                currentPlayer.setDataSource(tmp.getAbsolutePath());
                currentPlayer.prepare();
                currentPlayer.start();
                currentPlayBtn = playBtn;
                playBtn.setImageResource(R.drawable.ic_pause);
                playBtn.setColorFilter(isMe ? Color.WHITE : Color.parseColor("#00C896"));

                currentPlayer.setOnCompletionListener(mp -> {
                    playBtn.setImageResource(R.drawable.ic_play);
                    playBtn.setColorFilter(isMe ? Color.WHITE : Color.parseColor("#00C896"));
                    mp.release();
                    currentPlayer = null;
                    currentPlayBtn = null;
                });
            } catch (Exception e) {
                Toast.makeText(this, "Could not play audio", Toast.LENGTH_SHORT).show();
            }
        });

        addToRow(audioBubble, isMe, timestampMillis);
    }

    // ── Shared: wrap any bubble view into a timestamped, aligned row ──────────
    private void addToRow(View bubbleView, boolean isMe, Long timestampMillis) {
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(dp(8), dp(2), dp(8), dp(1));

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

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(isMe ? Gravity.END : Gravity.START);
        col.addView(bubbleView, bubbleParams);
        col.addView(tsView, tsParams);

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

        row.setAlpha(0f); row.setTranslationY(8f);
        row.animate().alpha(1f).translationY(0f)
                .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();

        messagesContainer.addView(row);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String formatDuration(long ms) {
        long secs = ms / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", secs / 60, secs % 60);
    }

    private void fetchAndShowOnlineStatus(String otherUid, TextView target) {
        if (otherUid == null || otherUid.isEmpty()) return;
        FirebaseDatabase.getInstance().getReference("users")
                .child(otherUid).child("lastSeen").get()
                .addOnSuccessListener(snap -> {
                    Long lastSeen = snap.getValue(Long.class);
                    if (lastSeen != null && lastSeen > 0) {
                        target.setText(formatOnlineStatus(lastSeen));
                    } else if (chatId != null) {
                        FirebaseDatabase.getInstance().getReference("chats")
                                .child(chatId).child("lastTimestamp").get()
                                .addOnSuccessListener(tsSnap -> {
                                    Long ts = tsSnap.getValue(Long.class);
                                    if (ts != null && ts > 0) target.setText(formatOnlineStatus(ts));
                                });
                    }
                });
    }

    private String formatOnlineStatus(long ms) {
        long diff = System.currentTimeMillis() - ms;
        if (diff < 60_000)     return "online now";
        if (diff < 3_600_000)  return (diff / 60_000) + "m ago";
        if (diff < 86_400_000) return (diff / 3_600_000) + "h ago";
        long days = diff / 86_400_000;
        if (days == 1)         return "yesterday";
        if (days < 7)          return days + "d ago";
        return "a while ago";
    }

    private String formatMessageTime(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < 60_000) return "just now";
        Date d = new Date(millis);
        long diffDays = diff / 86_400_000;
        if (diffDays == 0) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        if (diffDays == 1) return "Yesterday " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        return new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(d);
    }

    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) messagesRef.removeEventListener(messageListener);
        if (mediaRecorder != null) { try { mediaRecorder.stop(); } catch (Exception ignored) {} mediaRecorder.release(); }
        if (currentPlayer != null) { currentPlayer.release(); }
        timerHandler.removeCallbacksAndMessages(null);
    }
}