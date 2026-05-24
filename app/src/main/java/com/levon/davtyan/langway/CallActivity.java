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
import com.google.firebase.database.ValueEventListener;

/**
 * CallActivity — signalling screen only (no media).
 *
 * MODE_OUTGOING: writes call to Firebase, shows "Calling…", waits for response.
 * MODE_INCOMING: shows Accept / Decline buttons.
 *
 * When the call is accepted both sides launch HMSCallActivity which handles
 * all audio/video via the 100ms SDK.
 */
public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_MODE         = "mode";
    public static final String EXTRA_CALL_ID      = "call_id";
    public static final String EXTRA_CALLER_NAME  = "caller_name";
    public static final String EXTRA_CALLER_UID   = "caller_uid";
    public static final String EXTRA_CALLER_PHOTO = "caller_photo";
    public static final String EXTRA_IS_VIDEO     = "is_video";
    public static final String EXTRA_MY_NAME      = "my_name";
    public static final String EXTRA_CHAT_ID      = "chat_id";
    /** 100ms auth token — fetched by the caller and passed via Firebase signal */
    public static final String EXTRA_HMS_TOKEN    = "hms_token";

    public static final String MODE_OUTGOING = "outgoing";
    public static final String MODE_INCOMING = "incoming";

    private static final int CALL_TIMEOUT_MS = 45_000;

    private String mode;
    private String callId;
    private String chatId;
    private boolean isVideo;
    private boolean callStarted = false;

    private DatabaseReference callRef;
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

        TextView nameView    = findViewById(R.id.call_caller_name);
        TextView statusView  = findViewById(R.id.call_status);
        TextView initialsView= findViewById(R.id.call_initials);
        ImageView photoView  = findViewById(R.id.call_photo);
        ImageButton acceptBtn  = findViewById(R.id.call_accept_btn);
        ImageButton declineBtn = findViewById(R.id.call_decline_btn);

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
            timeoutHandler.postDelayed(this::cancelCall, CALL_TIMEOUT_MS);
            listenForCallState(myName);
        } else {
            statusView.setText(isVideo ? "Incoming video call" : "Incoming voice call");
            acceptBtn.setVisibility(View.VISIBLE);
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
                        launchHMSCall(myDisplayName, null);
                    } else if ("declined".equals(state) || "cancelled".equals(state)) {
                        Toast.makeText(CallActivity.this,
                                "declined".equals(state) ? "Call declined" : "Call cancelled",
                                Toast.LENGTH_SHORT).show();
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
        launchHMSCall(myDisplayName, null);
    }

    private void declineCall() {
        timeoutHandler.removeCallbacksAndMessages(null);
        callRef.child("state").setValue("declined");
        cleanupAndFinish();
    }

    private void cancelCall() {
        timeoutHandler.removeCallbacksAndMessages(null);
        callRef.child("state").setValue("cancelled");
        cleanupAndFinish();
    }

    private void launchHMSCall(String myDisplayName, String hmsToken) {
        if (callStarted) return;
        callStarted = true;

        Intent intent = new Intent(this, WebRTCCallActivity.class);
        intent.putExtra(WebRTCCallActivity.EXTRA_CALL_ID, callId);
        intent.putExtra(WebRTCCallActivity.EXTRA_USER_NAME, myDisplayName);
        intent.putExtra(WebRTCCallActivity.EXTRA_IS_VIDEO, isVideo);
        intent.putExtra(WebRTCCallActivity.EXTRA_IS_CALLER,
                MODE_OUTGOING.equals(mode));
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