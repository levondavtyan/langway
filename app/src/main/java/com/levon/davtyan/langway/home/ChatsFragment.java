package com.levon.davtyan.langway.home;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.levon.davtyan.langway.ChatActivity;
import com.levon.davtyan.langway.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ChatsFragment extends Fragment {

    private LinearLayout listContainer;
    private LinearLayout emptyState;
    private ListenerRegistration chatsListener;
    private String myUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        listContainer = v.findViewById(R.id.chats_list_container);
        emptyState    = v.findViewById(R.id.chats_empty);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        listenForChats();
    }

    private void listenForChats() {
        // Listen to all chats where myUid is a participant, ordered by last message time
        chatsListener = FirebaseFirestore.getInstance()
                .collection("chats")
                .whereArrayContains("participants", myUid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (!isAdded() || snapshots == null) return;
                    listContainer.removeAllViews();

                    if (snapshots.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        return;
                    }
                    emptyState.setVisibility(View.GONE);

                    LayoutInflater inflater = LayoutInflater.from(requireContext());
                    int idx = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Map<String, Object> data = doc.getData();
                        String chatId      = doc.getId();
                        String lastMsg     = str(data, "lastMessage", "No messages yet");
                        Object ts          = data.get("lastTimestamp");
                        String timeStr     = formatTimestamp(ts);

                        // Find the other participant's UID and info
                        Object partsObj = data.get("participants");
                        String otherUid = "";
                        if (partsObj instanceof java.util.List) {
                            for (Object p : (java.util.List<?>) partsObj) {
                                if (!p.toString().equals(myUid)) { otherUid = p.toString(); break; }
                            }
                        }

                        // Names/photos stored in the chat doc for quick display
                        Map<?, ?> names  = (Map<?, ?>) data.get("participantNames");
                        Map<?, ?> photos = (Map<?, ?>) data.get("participantPhotos");
                        String otherName  = names  != null ? str(names,  otherUid, "Unknown") : "Unknown";
                        String otherPhoto = photos != null ? str(photos, otherUid, "")        : "";
                        String otherLangs = str(data, "otherLangs_" + otherUid, "");

                        View row = buildChatRow(inflater, chatId, otherName, otherPhoto,
                                otherLangs, lastMsg, timeStr, otherUid, idx);
                        listContainer.addView(row);
                        idx++;
                    }
                });
    }

    private View buildChatRow(LayoutInflater inflater, String chatId, String otherName,
                              String otherPhoto, String otherLangs, String lastMsg,
                              String timeStr, String otherUid, int index) {
        View row = inflater.inflate(R.layout.item_chat_row, listContainer, false);

        ((TextView) row.findViewById(R.id.chat_row_name)).setText(otherName);
        ((TextView) row.findViewById(R.id.chat_row_last_msg)).setText(lastMsg);
        ((TextView) row.findViewById(R.id.chat_row_time)).setText(timeStr);

        // Initials
        TextView initialsView = row.findViewById(R.id.chat_row_initials);
        String[] parts = otherName.trim().split("\\s+");
        String initials = parts.length >= 2
                ? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
                : otherName.substring(0, Math.min(2, otherName.length())).toUpperCase();
        initialsView.setText(initials);

        // Photo
        if (!otherPhoto.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(otherPhoto, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ImageView photoView = row.findViewById(R.id.chat_row_photo);
                photoView.setImageBitmap(bmp);
                photoView.setVisibility(View.VISIBLE);
                initialsView.setVisibility(View.GONE);
            } catch (Exception ignored) {}
        }

        // Open ChatActivity on tap
        final String fOtherPhoto = otherPhoto;
        final String fOtherLangs = otherLangs;
        final String fOtherName  = otherName;
        row.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID,    chatId);
            intent.putExtra(ChatActivity.EXTRA_OTHER_UID,  otherUid);
            intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, fOtherName);
            intent.putExtra(ChatActivity.EXTRA_OTHER_LANGS, fOtherLangs);
            intent.putExtra(ChatActivity.EXTRA_OTHER_PHOTO, fOtherPhoto);
            startActivity(intent);
        });

        // Staggered entrance
        row.setAlpha(0f);
        row.animate().alpha(1f).setDuration(250).setStartDelay(50L * index)
                .setInterpolator(new DecelerateInterpolator()).start();

        return row;
    }

    private String formatTimestamp(Object ts) {
        if (ts instanceof com.google.firebase.Timestamp) {
            Date d = ((com.google.firebase.Timestamp) ts).toDate();
            long diff = System.currentTimeMillis() - d.getTime();
            if (diff < 60_000)        return "now";
            if (diff < 3_600_000)    return (diff / 60_000) + "m";
            if (diff < 86_400_000)   return (diff / 3_600_000) + "h";
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);
        }
        return "";
    }

    private String str(Map<?, ?> map, String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatsListener != null) chatsListener.remove();
        listContainer = null;
        emptyState    = null;
    }
}