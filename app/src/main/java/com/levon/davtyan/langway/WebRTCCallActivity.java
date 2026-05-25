package com.levon.davtyan.langway;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebRTCCallActivity extends AppCompatActivity {

    public static final String EXTRA_CALL_ID   = "call_id";
    public static final String EXTRA_CHAT_ID   = "chat_id";
    public static final String EXTRA_USER_NAME = "user_name";
    public static final String EXTRA_IS_VIDEO  = "is_video";
    public static final String EXTRA_IS_CALLER = "is_caller";

    private static final int REQUEST_PERMS = 404;

    // ── WebRTC ────────────────────────────────────────────────────────────────
    private EglBase               eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection        peerConnection;
    private VideoTrack            localVideoTrack;
    private AudioTrack            localAudioTrack;
    private VideoCapturer         videoCapturer;
    private VideoSource           videoSource;
    private SurfaceViewRenderer   localRenderer;
    private SurfaceViewRenderer   remoteRenderer;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isVideo;
    private boolean isCaller;
    private String  callId;
    private String  chatId;
    private boolean micMuted    = false;
    private boolean cameraMuted = false;
    private long    callStartTime;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference  signalRef;
    private ValueEventListener answerListener;
    private ValueEventListener candidateListener;

    private static final List<PeerConnection.IceServer> ICE_SERVERS = new ArrayList<>();
    static {
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        ICE_SERVERS.add(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtc_call);

        callId   = getIntent().getStringExtra(EXTRA_CALL_ID);
        chatId   = getIntent().getStringExtra(EXTRA_CHAT_ID);
        isVideo  = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);
        isCaller = getIntent().getBooleanExtra(EXTRA_IS_CALLER, true);
        callStartTime = System.currentTimeMillis();

        localRenderer  = findViewById(R.id.webrtc_local_video);
        remoteRenderer = findViewById(R.id.webrtc_remote_video);
        TextView    statusText = findViewById(R.id.webrtc_status);
        ImageButton btnMic    = findViewById(R.id.webrtc_btn_mic);
        ImageButton btnCamera = findViewById(R.id.webrtc_btn_camera);
        ImageButton btnEnd    = findViewById(R.id.webrtc_btn_end);

        if (!isVideo) {
            localRenderer.setVisibility(View.GONE);
            remoteRenderer.setVisibility(View.GONE);
            btnCamera.setVisibility(View.GONE);
        }

        btnMic.setOnClickListener(v -> toggleMic(btnMic));
        btnCamera.setOnClickListener(v -> toggleCamera(btnCamera));
        btnEnd.setOnClickListener(v -> hangUp());

        signalRef = FirebaseDatabase.getInstance()
                .getReference("webrtc_signals").child(callId);

        String[] perms = isVideo
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                : new String[]{Manifest.permission.RECORD_AUDIO};

        boolean allGranted = true;
        for (String p : perms)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false; break;
            }

        if (allGranted) startWebRTC();
        else ActivityCompat.requestPermissions(this, perms, REQUEST_PERMS);
    }

    // ── WebRTC init ───────────────────────────────────────────────────────────

    private void startWebRTC() {
        eglBase = EglBase.create();
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(true);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(getApplicationContext())
                        .createInitializationOptions());

        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(ICE_SERVERS);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("sdpMid", candidate.sdpMid);
                    json.put("sdpMLineIndex", candidate.sdpMLineIndex);
                    json.put("sdp", candidate.sdp);
                    String side = isCaller ? "caller_candidates" : "callee_candidates";
                    signalRef.child(side).push().setValue(json.toString());
                } catch (JSONException e) { e.printStackTrace(); }
            }

            public void onTrack(RtpReceiver receiver, MediaStream[] streams) {
                if (receiver.track() instanceof VideoTrack) {
                    VideoTrack remoteVideo = (VideoTrack) receiver.track();
                    runOnUiThread(() -> remoteVideo.addSink(remoteRenderer));
                }
            }
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                runOnUiThread(() -> {
                    if (s == PeerConnection.IceConnectionState.DISCONNECTED ||
                            s == PeerConnection.IceConnectionState.FAILED) {
                        Toast.makeText(WebRTCCallActivity.this, "Connection lost", Toast.LENGTH_SHORT).show();
                        hangUp();
                    }
                });
            }
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] c) {}
            @Override public void onAddStream(MediaStream s) {}
            @Override public void onRemoveStream(MediaStream s) {}
            @Override public void onDataChannel(DataChannel d) {}
            @Override public void onRenegotiationNeeded() {}
        });

        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("audio0", audioSource);
        peerConnection.addTrack(localAudioTrack);

        if (isVideo) {
            SurfaceTextureHelper surfaceHelper =
                    SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
            videoCapturer = createCameraCapturer();
            videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceHelper, getApplicationContext(), videoSource.getCapturerObserver());
            videoCapturer.startCapture(1280, 720, 30);
            localVideoTrack = factory.createVideoTrack("video0", videoSource);
            localVideoTrack.addSink(localRenderer);
            peerConnection.addTrack(localVideoTrack);
        }

        if (isCaller) createOffer();
        else listenForOffer();
        listenForRemoteCandidates();
    }

    // ── Signalling ────────────────────────────────────────────────────────────

    private void createOffer() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideo) c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", sdp.type.canonicalForm());
                    json.put("sdp", sdp.description);
                    signalRef.child("offer").setValue(json.toString());
                } catch (JSONException e) { e.printStackTrace(); }
                listenForAnswer();
            }
        }, c);
    }

    private void listenForOffer() {
        signalRef.child("offer").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String raw = snapshot.getValue(String.class);
                if (raw == null) return;
                try {
                    JSONObject json = new JSONObject(raw);
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(),
                            new SessionDescription(
                                    SessionDescription.Type.fromCanonicalForm(json.getString("type")),
                                    json.getString("sdp")));
                    createAnswer();
                } catch (JSONException e) { e.printStackTrace(); }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void createAnswer() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        if (isVideo) c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", sdp.type.canonicalForm());
                    json.put("sdp", sdp.description);
                    signalRef.child("answer").setValue(json.toString());
                } catch (JSONException e) { e.printStackTrace(); }
            }
        }, c);
    }

    private void listenForAnswer() {
        answerListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String raw = snapshot.getValue(String.class);
                if (raw == null) return;
                try {
                    JSONObject json = new JSONObject(raw);
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(),
                            new SessionDescription(
                                    SessionDescription.Type.fromCanonicalForm(json.getString("type")),
                                    json.getString("sdp")));
                } catch (JSONException e) { e.printStackTrace(); }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        signalRef.child("answer").addValueEventListener(answerListener);
    }

    private void listenForRemoteCandidates() {
        String remoteSide = isCaller ? "callee_candidates" : "caller_candidates";
        candidateListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String raw = child.getValue(String.class);
                    if (raw == null) continue;
                    try {
                        JSONObject json = new JSONObject(raw);
                        peerConnection.addIceCandidate(new IceCandidate(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("sdp")));
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        signalRef.child(remoteSide).addValueEventListener(candidateListener);
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    private void toggleMic(ImageButton btn) {
        micMuted = !micMuted;
        if (localAudioTrack != null) localAudioTrack.setEnabled(!micMuted);
        btn.setImageResource(micMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
    }

    private void toggleCamera(ImageButton btn) {
        cameraMuted = !cameraMuted;
        if (localVideoTrack != null) localVideoTrack.setEnabled(!cameraMuted);
        btn.setImageResource(cameraMuted ? R.drawable.ic_camera_off : R.drawable.ic_camera_on);
    }

    private void hangUp() {
        long endTime    = System.currentTimeMillis();
        long durationSec = (endTime - callStartTime) / 1000;

        signalRef.child("state").setValue("ended");

        // Write call log message into the chat so both sides see it
        if (chatId != null && !chatId.isEmpty()) {
            Map<String, Object> logMsg = new HashMap<>();
            logMsg.put("type",        "call_log");
            logMsg.put("callType",    isVideo ? "video" : "voice");
            logMsg.put("startTime",   callStartTime);
            logMsg.put("endTime",     endTime);
            logMsg.put("durationSec", durationSec);
            logMsg.put("timestamp",   endTime);
            FirebaseDatabase.getInstance()
                    .getReference("chats").child(chatId)
                    .child("messages").push().setValue(logMsg);

            // Update chat preview
            Map<String, Object> preview = new HashMap<>();
            preview.put("lastMessage",   isVideo ? "📹 Video call" : "📞 Voice call");
            preview.put("lastTimestamp", ServerValue.TIMESTAMP);
            FirebaseDatabase.getInstance()
                    .getReference("chats").child(chatId).updateChildren(preview);
        }

        cleanup();
        finish();
    }

    private void cleanup() {
        if (answerListener != null)
            signalRef.child("answer").removeEventListener(answerListener);
        if (candidateListener != null) {
            String remoteSide = isCaller ? "callee_candidates" : "caller_candidates";
            signalRef.child(remoteSide).removeEventListener(candidateListener);
        }
        if (videoCapturer != null) {
            try { videoCapturer.stopCapture(); } catch (InterruptedException ignored) {}
            videoCapturer.dispose();
        }
        if (localRenderer  != null) localRenderer.release();
        if (remoteRenderer != null) remoteRenderer.release();
        if (peerConnection != null) { peerConnection.close(); peerConnection = null; }
        if (factory        != null) { factory.dispose(); factory = null; }
        if (eglBase        != null) { eglBase.release(); eglBase = null; }
    }

    private VideoCapturer createCameraCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(this);
        for (String name : enumerator.getDeviceNames())
            if (enumerator.isFrontFacing(name)) return enumerator.createCapturer(name, null);
        for (String name : enumerator.getDeviceNames())
            return enumerator.createCapturer(name, null);
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMS) {
            boolean ok = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            if (ok) startWebRTC();
            else { Toast.makeText(this, "Permission required for calls", Toast.LENGTH_LONG).show(); finish(); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private static class SimpleSdpObserver implements org.webrtc.SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}