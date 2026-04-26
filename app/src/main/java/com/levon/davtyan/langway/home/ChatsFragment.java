package com.levon.davtyan.langway.home;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.levon.davtyan.langway.ChatActivity;
import com.levon.davtyan.langway.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatsFragment extends Fragment {

    private LinearLayout listContainer;
    private LinearLayout emptyState;
    private ValueEventListener chatsListener;
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
        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                listContainer.removeAllViews();

                List<DataSnapshot> chatList = new ArrayList<>();
                for (DataSnapshot chatSnap : snapshot.getChildren()) {
                    DataSnapshot participantsSnap = chatSnap.child("participants");
                    for (DataSnapshot p : participantsSnap.getChildren()) {
                        if (myUid.equals(p.getValue(String.class))) {
                            chatList.add(chatSnap);
                            break;
                        }
                    }
                }

                if (chatList.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                emptyState.setVisibility(View.GONE);

                chatList.sort((a, b) -> {
                    Long ta = a.child("lastTimestamp").getValue(Long.class);
                    Long tb = b.child("lastTimestamp").getValue(Long.class);
                    if (ta == null) ta = 0L;
                    if (tb == null) tb = 0L;
                    return Long.compare(tb, ta);
                });

                LayoutInflater inflater = LayoutInflater.from(requireContext());
                int idx = 0;
                for (DataSnapshot chatSnap : chatList) {
                    String chatId  = chatSnap.getKey();
                    String lastMsg = chatSnap.child("lastMessage").getValue(String.class);
                    if (lastMsg == null) lastMsg = "No messages yet";
                    Long tsLong = chatSnap.child("lastTimestamp").getValue(Long.class);
                    String timeStr = formatTimestamp(tsLong);

                    String otherUid = "";
                    for (DataSnapshot p : chatSnap.child("participants").getChildren()) {
                        String uid = p.getValue(String.class);
                        if (uid != null && !uid.equals(myUid)) { otherUid = uid; break; }
                    }

                    String otherName  = chatSnap.child("participantNames").child(otherUid).getValue(String.class);
                    String otherPhoto = chatSnap.child("participantPhotos").child(otherUid).getValue(String.class);
                    String otherLangs = chatSnap.child("otherLangs_" + otherUid).getValue(String.class);
                    if (otherName  == null) otherName  = "Unknown";
                    if (otherPhoto == null) otherPhoto = "";
                    if (otherLangs == null) otherLangs = "";

                    View row = buildChatRow(inflater, chatId, otherName, otherPhoto,
                            otherLangs, lastMsg, timeStr, otherUid, idx);
                    listContainer.addView(row);
                    idx++;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        FirebaseDatabase.getInstance()
                .getReference("chats")
                .addValueEventListener(chatsListener);
    }

    private View buildChatRow(LayoutInflater inflater, String chatId, String otherName,
                              String otherPhoto, String otherLangs, String lastMsg,
                              String timeStr, String otherUid, int index) {
        View row = inflater.inflate(R.layout.item_chat_row, listContainer, false);

        ((TextView) row.findViewById(R.id.chat_row_name)).setText(otherName);
        ((TextView) row.findViewById(R.id.chat_row_last_msg)).setText(lastMsg);
        ((TextView) row.findViewById(R.id.chat_row_time)).setText(timeStr);

        TextView initialsView = row.findViewById(R.id.chat_row_initials);
        String[] parts = otherName.trim().split("\\s+");
        String initials = parts.length >= 2
                ? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
                : otherName.substring(0, Math.min(2, otherName.length())).toUpperCase();
        initialsView.setText(initials);

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

        row.setAlpha(0f);
        row.animate().alpha(1f).setDuration(250).setStartDelay(50L * index)
                .setInterpolator(new DecelerateInterpolator()).start();

        return row;
    }

    private String formatTimestamp(Long tsMillis) {
        if (tsMillis == null || tsMillis == 0) return "";
        Date d = new Date(tsMillis);
        long diff = System.currentTimeMillis() - tsMillis;
        if (diff < 60_000)      return "now";
        if (diff < 3_600_000)  return (diff / 60_000) + "m";
        if (diff < 86_400_000) return (diff / 3_600_000) + "h";
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatsListener != null) {
            FirebaseDatabase.getInstance().getReference("chats")
                    .removeEventListener(chatsListener);
        }
        listContainer = null;
        emptyState    = null;
    }
}