package com.levon.davtyan.langway.home;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.levon.davtyan.langway.ChatActivity;
import com.levon.davtyan.langway.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_UID         = "uid";
    public static final String EXTRA_NAME        = "name";
    public static final String EXTRA_PHOTO       = "photo";
    public static final String EXTRA_BIO         = "bio";
    public static final String EXTRA_LANG_LINE   = "lang_line";  // pre-built display string
    public static final String EXTRA_MY_NAME     = "my_name";
    public static final String EXTRA_MY_PHOTO    = "my_photo";
    public static final String EXTRA_MY_UID      = "my_uid";

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English","🇬🇧"); put("Spanish","🇪🇸"); put("Russian","🇷🇺");
        put("Armenian","🇦🇲"); put("German","🇩🇪"); put("French","🇫🇷");
        put("Mandarin","🇨🇳"); put("Japanese","🇯🇵"); put("Italian","🇮🇹");
        put("Portuguese","🇵🇹"); put("Arabic","🇸🇦"); put("Korean","🇰🇷");
        put("Dutch","🇳🇱"); put("Swedish","🇸🇪"); put("Turkish","🇹🇷");
    }};

    private static final int CHIP_GREEN_BG   = 0xFFDFFAF3;
    private static final int CHIP_GREEN_TEXT = 0xFF00A87A;
    private static final int CHIP_STROKE     = 0xFFB2DDD6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        String otherUid  = getIntent().getStringExtra(EXTRA_UID);
        String name      = getIntent().getStringExtra(EXTRA_NAME);
        String photoB64  = getIntent().getStringExtra(EXTRA_PHOTO);
        String bio       = getIntent().getStringExtra(EXTRA_BIO);
        String langLine  = getIntent().getStringExtra(EXTRA_LANG_LINE);
        String myUid     = getIntent().getStringExtra(EXTRA_MY_UID);
        String myName    = getIntent().getStringExtra(EXTRA_MY_NAME);
        String myPhoto   = getIntent().getStringExtra(EXTRA_MY_PHOTO);

        ImageButton backBtn          = findViewById(R.id.user_profile_back_btn);
        TextView    nameView         = findViewById(R.id.user_profile_name);
        TextView    bioView          = findViewById(R.id.user_profile_bio);
        TextView    initialsView     = findViewById(R.id.user_profile_initials);
        ImageView   photoView        = findViewById(R.id.user_profile_photo);
        LinearLayout langsContainer  = findViewById(R.id.user_profile_languages_container);
        ChipGroup   hobbyChips       = findViewById(R.id.user_profile_hobby_chips);
        MaterialButton chatBtn       = findViewById(R.id.user_profile_chat_btn);

        backBtn.setOnClickListener(v -> finish());

        // Name
        if (name != null && !name.isEmpty()) {
            nameView.setText(name);
            initialsView.setText(getInitials(name));
        }

        // Photo
        if (photoB64 != null && !photoB64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(photoB64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                photoView.setImageBitmap(bmp);
                photoView.setVisibility(View.VISIBLE);
                initialsView.setVisibility(View.GONE);
            } catch (Exception ignored) {}
        }

        // Bio
        if (bio != null && !bio.isEmpty()) {
            bioView.setText(bio);
            bioView.setVisibility(View.VISIBLE);
        }

        // Load full profile from Firebase for languages + hobbies
        if (otherUid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(otherUid).get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.exists()) return;

                        // Update bio/name if richer data available
                        String dbBio = snapshot.child("bio").getValue(String.class);
                        if (dbBio != null && !dbBio.isEmpty() && bioView.getVisibility() != View.VISIBLE) {
                            bioView.setText(dbBio);
                            bioView.setVisibility(View.VISIBLE);
                        }

                        // Languages section
                        boolean hasLangs = false;
                        for (DataSnapshot e : snapshot.child("languages").getChildren()) {
                            String lang  = e.getKey();
                            String level = e.getValue(String.class);
                            if (lang == null) continue;
                            hasLangs = true;

                            View row = getLayoutInflater().inflate(
                                    R.layout.item_language_row, langsContainer, false);
                            ((TextView) row.findViewById(R.id.lang_row_flag))
                                    .setText(FLAG_MAP.getOrDefault(lang, "🌐"));
                            ((TextView) row.findViewById(R.id.lang_row_name)).setText(lang);
                            ((TextView) row.findViewById(R.id.lang_row_level))
                                    .setText(level != null ? level : "");
                            langsContainer.addView(row);
                        }
                        if (hasLangs)
                            findViewById(R.id.user_profile_languages_card).setVisibility(View.VISIBLE);

                        // Hobbies section
                        boolean hasHobbies = false;
                        for (DataSnapshot h : snapshot.child("hobbies").getChildren()) {
                            String val = h.getValue(String.class);
                            if (val == null) continue;
                            hasHobbies = true;
                            String label = val.contains(" ") ? val.substring(val.indexOf(" ") + 1) : val;
                            hobbyChips.addView(makeChip(label));
                        }
                        if (hasHobbies)
                            findViewById(R.id.user_profile_hobbies_card).setVisibility(View.VISIBLE);
                    });
        }

        // Chat button
        final String fMyUid    = myUid;
        final String fMyName   = myName;
        final String fMyPhoto  = myPhoto;
        final String fOtherUid = otherUid;
        final String fOtherName = name;
        final String fOtherPhoto = photoB64;
        final String fLangLine  = langLine;

        chatBtn.setOnClickListener(v ->
                startOrOpenChat(fMyUid, fMyName, fMyPhoto,
                        fOtherUid, fOtherName, fOtherPhoto, fLangLine));
    }

    private void startOrOpenChat(String myUid, String myName, String myPhoto,
                                 String otherUid, String otherName,
                                 String otherPhoto, String otherLangs) {
        if (myUid == null || otherUid == null) return;
        String[] uids = {myUid, otherUid};
        Arrays.sort(uids);
        String chatId = uids[0] + "_" + uids[1];

        FirebaseDatabase.getInstance().getReference("chats").child(chatId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Map<String, Object> names  = new HashMap<>();
                        Map<String, Object> photos = new HashMap<>();
                        names.put(myUid, myName != null ? myName : "");
                        names.put(otherUid, otherName != null ? otherName : "");
                        if (myPhoto != null && !myPhoto.isEmpty())    photos.put(myUid, myPhoto);
                        if (otherPhoto != null && !otherPhoto.isEmpty()) photos.put(otherUid, otherPhoto);

                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("participants",         Arrays.asList(myUid, otherUid));
                        chatData.put("participantNames",     names);
                        chatData.put("participantPhotos",    photos);
                        chatData.put("otherLangs_" + myUid, otherLangs != null ? otherLangs : "");
                        chatData.put("lastMessage",          "");
                        chatData.put("lastTimestamp",        ServerValue.TIMESTAMP);

                        FirebaseDatabase.getInstance().getReference("chats")
                                .child(chatId).setValue(chatData);
                    }
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_ID,     chatId);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_UID,   otherUid);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_NAME,  otherName);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_LANGS, otherLangs);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_PHOTO, otherPhoto);
                    startActivity(intent);
                });
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private Chip makeChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_GREEN_BG));
        chip.setTextColor(CHIP_GREEN_TEXT);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(CHIP_STROKE));
        chip.setChipStrokeWidth(1f);
        chip.setTextSize(12f);
        return chip;
    }
}