package com.levon.davtyan.langway;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_MODE         = "mode";
    public static final String EXTRA_CALL_ID      = "call_id";
    public static final String EXTRA_CALLER_NAME  = "caller_name";
    public static final String EXTRA_CALLER_UID   = "caller_uid";
    public static final String EXTRA_CALLER_PHOTO = "caller_photo";
    public static final String EXTRA_IS_VIDEO     = "is_video";
    public static final String EXTRA_MY_NAME      = "my_name";
    public static final String EXTRA_CHAT_ID      = "chat_id";
    public static final String EXTRA_HMS_TOKEN    = "hms_token";

    public static final String MODE_OUTGOING = "outgoing";
    public static final String MODE_INCOMING = "incoming";

    private static final int CALL_TIMEOUT_MS = 45_000;

    private String  mode;
    private String  callId;
    private String  chatId;
    private boolean isVideo;
    private boolean callStarted  = false;
    private boolean logWritten   = false;  // guard: write missed-call log only once

    private DatabaseReference  callRef;
    private ValueEventListener callStateListener;

    private final android.os.Handler timeoutHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        mode    = getIntent().getStringExtra(EXTRA_MODE);
        callId  = getIntent().getStringExtra(EXTRA_CALL_ID);
        chatId  = getIntent().getStringExtra(EXTRA_CHAT_ID);
        isVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);
        String callerName  = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        String callerPhoto = getIntent().getStringExtra(EXTRA_CALLER_PHOTO);
        String myName      = getIntent().getStringExtra(EXTRA_MY_NAME);

        TextView    nameView     = findViewById(R.id.call_caller_name);
        TextView    statusView   = findViewById(R.id.call_status);
        TextView    initialsView = findViewById(R.id.call_initials);
        ImageView   photoView    = findViewById(R.id.call_photo);
        ImageButton acceptBtn    = findViewById(R.id.call_accept_btn);
        ImageButton declineBtn   = findViewById(R.id.call_decline_btn);

        nameView.setText(callerName != null ? callerName : "Unknown");

        if (callerPhoto != null && !callerPhoto.isEmpty()) {
            try {
                byte[] b = Base64.decode(callerPhoto, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                photoView.setImageBitmap(bmp);
                photoView.setVisibility(View.VISIBLE);
                initialsView.setVisibility(View.GONE);
            } catch (Exception ignored) {}
        } else if (callerName != null && !callerName.isEmpty()) {
            String[] parts = callerName.trim().split("\\s+");
            String initials = parts.length >= 2
                    ? "" + parts[0].charAt(0) + parts[1].charAt(0)
                    : callerName.substring(0, Math.min(2, callerName.length()));
            initialsView.setText(initials.toUpperCase());
        }

        callRef = FirebaseDatabase.getInstance()
                .getReference("calls").child(callId);

        if (MODE_OUTGOING.equals(mode)) {
            statusView.setText(isVideo ? "Video calling…" : "Voice calling…");
            acceptBtn.setVisibility(View.GONE);
            declineBtn.setImageResource(R.drawable.ic_call_end);
            declineBtn.setOnClickListener(v -> cancelCall());
            timeoutHandler.postDelayed(this::onCallTimedOut, CALL_TIMEOUT_MS);
            listenForCallState(myName);
        } else {
            statusView.setText(isVideo ? "Incoming video call" : "Incoming voice call");
            acceptBtn.setVisibility(View.VISIBLE);
            findViewById(R.id.call_accept_label).setVisibility(View.VISIBLE);
            acceptBtn.setOnClickListener(v -> acceptCall(myName));
            declineBtn.setOnClickListener(v -> declineCall());
            listenForCallState(myName);
            timeoutHandler.postDelayed(this::declineCall, CALL_TIMEOUT_MS);
        }
    }

    private void listenForCallState(String myDisplayName) {
        callStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) { finish(); return; }
                String state = snapshot.child("state").getValue(String.class);
                if (state == null) return;

                if (MODE_OUTGOING.equals(mode)) {
                    if ("accepted".equals(state) && !callStarted) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        launchWebRTCCall(myDisplayName);
                    } else if ("declined".equals(state)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        // Recipient explicitly declined — log as missed call
                        writeMissedCallLog();
                        Toast.makeText(CallActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                        cleanupAndFinish();
                    } else if ("cancelled".equals(state)) {
                        cleanupAndFinish();
                    }
                } else {
                    if ("cancelled".equals(state)) {
                        Toast.makeText(CallActivity.this, "Caller cancelled",
                                Toast.LENGTH_SHORT).show();
                        cleanupAndFinish();
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        callRef.addValueEventListener(callStateListener);
    }

    private void acceptCall(String myDisplayName) {
        timeoutHandler.removeCallbacksAndMessages(null);
        callRef.child("state").setValue("accepted");
        launchWebRTCCall(myDisplayName);
    }

    private void declineCall() {
        timeoutHandler.removeCallbacksAndMessages(null);
        callRef.child("state").setValue("declined");
        cleanupAndFinish();
        // Recipient declined — don't write log here; caller's side writes it on "declined" state
    }

    private void cancelCall() {
        timeoutHandler.removeCallbacksAndMessages(null);
        // Caller hung up before answer — log as missed call, then signal
        writeMissedCallLog();
        callRef.child("state").setValue("cancelled");
        cleanupAndFinish();
    }

    private void onCallTimedOut() {
        // Rang for 45s with no answer — log as missed, signal cancelled
        writeMissedCallLog();
        callRef.child("state").setValue("cancelled");
        cleanupAndFinish();
    }

    /**
     * Writes a "missed call" entry into the chat. Only the caller writes this,
     * and only once (guarded by logWritten flag).
     */
    private void writeMissedCallLog() {
        if (logWritten) return;
        if (chatId == null || chatId.isEmpty()) return;
        if (!MODE_OUTGOING.equals(mode)) return; // only caller writes the log
        logWritten = true;

        long now = System.currentTimeMillis();
        Map<String, Object> logMsg = new HashMap<>();
        logMsg.put("type",        "call_log");
        logMsg.put("callType",    isVideo ? "video" : "voice");
        logMsg.put("missed",      true);
        logMsg.put("startTime",   now);
        logMsg.put("endTime",     now);
        logMsg.put("durationSec", 0L);
        logMsg.put("timestamp",   now);

        FirebaseDatabase.getInstance()
                .getReference("chats").child(chatId)
                .child("messages").push().setValue(logMsg);

        Map<String, Object> preview = new HashMap<>();
        preview.put("lastMessage",   isVideo ? "📹 Missed video call" : "📞 Missed call");
        preview.put("lastTimestamp", ServerValue.TIMESTAMP);
        FirebaseDatabase.getInstance()
                .getReference("chats").child(chatId).updateChildren(preview);
    }

    private void launchWebRTCCall(String myDisplayName) {
        if (callStarted) return;
        callStarted = true;

        Intent intent = new Intent(this, WebRTCCallActivity.class);
        intent.putExtra(WebRTCCallActivity.EXTRA_CALL_ID,   callId);
        intent.putExtra(WebRTCCallActivity.EXTRA_CHAT_ID,   chatId);
        intent.putExtra(WebRTCCallActivity.EXTRA_USER_NAME, myDisplayName);
        intent.putExtra(WebRTCCallActivity.EXTRA_IS_VIDEO,  isVideo);
        intent.putExtra(WebRTCCallActivity.EXTRA_IS_CALLER, MODE_OUTGOING.equals(mode));
        startActivity(intent);

        callRef.child("state").setValue("active");
        finish();
    }

    private void cleanupAndFinish() {
        if (callStateListener != null) callRef.removeEventListener(callStateListener);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
        if (callStateListener != null) callRef.removeEventListener(callStateListener);
    }
}