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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.levon.davtyan.langway.ChatActivity;
import com.levon.davtyan.langway.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscoverFragment extends Fragment {

    private static final int CHIP_GREEN_BG   = 0xFFDFFAF3;
    private static final int CHIP_GREEN_TEXT = 0xFF00A87A;
    private static final int CHIP_SEL_BG    = 0xFF00C896;
    private static final int CHIP_SEL_TEXT  = 0xFFFFFFFF;
    private static final int CHIP_UNSEL_BG  = 0xFFFFFFFF;
    private static final int CHIP_UNSEL_TEXT = 0xFF0D2626;
    private static final int CHIP_STROKE    = 0xFFB2DDD6;

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English","🇬🇧"); put("Spanish","🇪🇸"); put("Russian","🇷🇺");
        put("Armenian","🇦🇲"); put("German","🇩🇪"); put("French","🇫🇷");
        put("Mandarin","🇨🇳"); put("Japanese","🇯🇵"); put("Italian","🇮🇹");
        put("Portuguese","🇵🇹"); put("Arabic","🇸🇦"); put("Korean","🇰🇷");
        put("Dutch","🇳🇱"); put("Swedish","🇸🇪"); put("Turkish","🇹🇷");
    }};

    private static final int[][] AVATAR_GRADIENTS = {
            {0xFF00C896, 0xFF2979FF}, {0xFFFF6B6B, 0xFFFFD93D},
            {0xFF6C63FF, 0xFF3EC6E0}, {0xFFFF8C42, 0xFFFF3CAC},
            {0xFF43E97B, 0xFF38F9D7}, {0xFF667EEA, 0xFF764BA2},
    };

    private ChipGroup    filterChips;
    private LinearLayout cardsContainer;
    private LinearLayout emptyState;

    private final List<Map<String, Object>> allMatches = new ArrayList<>();
    private String activeFilter = null;
    private AuthStateListener authStateListener;

    private String       myUid       = "";
    private String       myName      = "";
    private String       myPhoto     = "";
    private String       myLangStr   = "";
    private Set<String>  myLangKeys  = new HashSet<>();
    private Set<String>  myHobbyKeys = new HashSet<>();
    private List<String> myLangPairs = new ArrayList<>();
    private List<String> myHobbies   = new ArrayList<>();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        filterChips    = v.findViewById(R.id.discover_filter_chips);
        cardsContainer = v.findViewById(R.id.discover_cards_container);
        emptyState     = v.findViewById(R.id.discover_empty);

        authStateListener = firebaseAuth -> {
            if (!isAdded()) return;
            if (firebaseAuth.getCurrentUser() == null) return;

            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
            myUid = firebaseAuth.getCurrentUser().getUid();

            // Stamp our own lastSeen immediately so others see us as active
            FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(myUid)
                    .child("lastSeen")
                    .setValue(ServerValue.TIMESTAMP);

            FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(myUid)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!isAdded()) return;
                        if (snapshot.exists()) {
                            myName  = strSnap(snapshot, "displayName", "");
                            myPhoto = strSnap(snapshot, "photo", "");

                            for (DataSnapshot entry : snapshot.child("languages").getChildren()) {
                                String lang  = entry.getKey();
                                String level = entry.getValue(String.class);
                                if (lang == null) continue;
                                myLangKeys.add(lang);
                                myLangPairs.add(lang + "|" + level);
                            }
                            for (DataSnapshot h : snapshot.child("hobbies").getChildren()) {
                                String hobbyVal = h.getValue(String.class);
                                if (hobbyVal == null) continue;
                                myHobbies.add(hobbyVal);
                                String key = hobbyVal.contains(" ")
                                        ? hobbyVal.substring(hobbyVal.indexOf(" ") + 1)
                                        : hobbyVal;
                                myHobbyKeys.add(key.toLowerCase());
                            }
                        }
                        loadMatchingUsers();
                    })
                    .addOnFailureListener(e -> loadMatchingUsers());
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    // ── Step 1: load all users (force fresh read — bypass local cache) ─────────
    private void loadMatchingUsers() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                allMatches.clear();

                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String uid = userSnap.getKey();
                    if (myUid.equals(uid)) continue;

                    Map<String, Object> data = new HashMap<>();
                    data.put("_uid",        uid);
                    data.put("displayName", strSnap(userSnap, "displayName", ""));
                    data.put("photo",       strSnap(userSnap, "photo", ""));
                    data.put("bio",         strSnap(userSnap, "bio", ""));

                    Long lastSeen = userSnap.child("lastSeen").getValue(Long.class);
                    data.put("lastSeen", lastSeen);

                    Map<String, String> langsMap = new HashMap<>();
                    for (DataSnapshot e : userSnap.child("languages").getChildren()) {
                        if (e.getKey() != null)
                            langsMap.put(e.getKey(), e.getValue(String.class));
                    }
                    data.put("languages", langsMap);

                    List<String> hobbiesList = new ArrayList<>();
                    for (DataSnapshot h : userSnap.child("hobbies").getChildren()) {
                        String val = h.getValue(String.class);
                        if (val != null) hobbiesList.add(val);
                    }
                    data.put("hobbies", hobbiesList);

                    // Only include users whose email is present in DB
                    // (deleted Auth accounts still have a DB node but email won't
                    //  be re-written, so this acts as a liveness check)
                    String userEmail = strSnap(userSnap, "email", "");
                    if (!userEmail.isEmpty() && hasMatch(data)) allMatches.add(data);
                }

                // Step 2: fill in lastSeen from chat history for accounts that lack it
                enrichLastSeenFromChats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) showEmpty();
            }
        });
    }

    // ── Step 2: for every user without lastSeen, scan chats for their most
    //            recent message timestamp and use that as a proxy ──────────────
    private void enrichLastSeenFromChats() {
        // Collect UIDs that still need a lastSeen value
        List<String> needsEnrich = new ArrayList<>();
        for (Map<String, Object> user : allMatches) {
            Object ls = user.get("lastSeen");
            if (!(ls instanceof Long) || (Long) ls == 0) {
                needsEnrich.add(str(user, "_uid", ""));
            }
        }

        if (needsEnrich.isEmpty()) {
            // Everyone already has lastSeen — go straight to rendering
            buildFilters();
            renderCards(allMatches);
            return;
        }

        // Load the full chats node once, then scan per-user
        FirebaseDatabase.getInstance()
                .getReference("chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot chatsSnapshot) {
                        if (!isAdded()) return;

                        // Build a map: uid → max(lastTimestamp across all their chats)
                        Map<String, Long> bestTs = new HashMap<>();

                        for (DataSnapshot chat : chatsSnapshot.getChildren()) {
                            Long ts = chat.child("lastTimestamp").getValue(Long.class);
                            if (ts == null || ts == 0) continue;

                            for (DataSnapshot p : chat.child("participants").getChildren()) {
                                String uid = p.getValue(String.class);
                                if (uid == null) continue;
                                Long current = bestTs.get(uid);
                                if (current == null || ts > current) bestTs.put(uid, ts);
                            }
                        }

                        // Patch allMatches entries that had no lastSeen
                        for (Map<String, Object> user : allMatches) {
                            Object ls = user.get("lastSeen");
                            if (!(ls instanceof Long) || (Long) ls == 0) {
                                String uid = str(user, "_uid", "");
                                Long derived = bestTs.get(uid);
                                if (derived != null) {
                                    user.put("lastSeen", derived);
                                    // Write back so next load is instant
                                    FirebaseDatabase.getInstance()
                                            .getReference("users")
                                            .child(uid)
                                            .child("lastSeen")
                                            .setValue(derived);
                                }
                            }
                        }

                        buildFilters();
                        renderCards(allMatches);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Chats read failed — render without enrichment
                        if (isAdded()) {
                            buildFilters();
                            renderCards(allMatches);
                        }
                    }
                });
    }

    private boolean hasMatch(Map<String, Object> user) {
        Object langsObj = user.get("languages");
        if (langsObj instanceof Map) {
            for (Object key : ((Map<?, ?>) langsObj).keySet()) {
                if (myLangKeys.contains(key.toString())) return true;
            }
        }
        Object hobbiesObj = user.get("hobbies");
        if (hobbiesObj instanceof List) {
            for (Object h : (List<?>) hobbiesObj) {
                String key = h.toString();
                if (key.contains(" ")) key = key.substring(key.indexOf(" ") + 1);
                if (myHobbyKeys.contains(key.toLowerCase())) return true;
            }
        }
        return false;
    }

    private void buildFilters() {
        if (!isAdded()) return;
        filterChips.removeAllViews();
        addFilterChip("All", true);
        for (String pair : myLangPairs) {
            String lang = pair.split("\\|")[0];
            addFilterChip(FLAG_MAP.getOrDefault(lang, "🌐") + " " + lang, false);
        }
        for (String hobby : myHobbies) {
            String label = hobby.contains(" ") ? hobby.substring(hobby.indexOf(" ") + 1) : hobby;
            addFilterChip(label, false);
        }
    }

    private void addFilterChip(String label, boolean selected) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(selected);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                selected ? CHIP_SEL_BG : CHIP_UNSEL_BG));
        chip.setTextColor(selected ? CHIP_SEL_TEXT : CHIP_UNSEL_TEXT);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(CHIP_STROKE));
        chip.setChipStrokeWidth(1f);
        chip.setCheckedIconVisible(false);
        chip.setTextSize(13f);
        chip.setOnClickListener(v -> {
            for (int i = 0; i < filterChips.getChildCount(); i++) {
                Chip c = (Chip) filterChips.getChildAt(i);
                c.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_UNSEL_BG));
                c.setTextColor(CHIP_UNSEL_TEXT);
            }
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_SEL_BG));
            chip.setTextColor(CHIP_SEL_TEXT);
            activeFilter = label.equals("All") ? null : label;
            applyFilter();
        });
        filterChips.addView(chip);
    }

    private void applyFilter() {
        if (activeFilter == null) { renderCards(allMatches); return; }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> user : allMatches) {
            if (matchesChip(user, activeFilter)) filtered.add(user);
        }
        renderCards(filtered);
    }

    private boolean matchesChip(Map<String, Object> user, String filter) {
        Object hobbiesObj = user.get("hobbies");
        if (hobbiesObj instanceof List) {
            for (Object h : (List<?>) hobbiesObj) {
                String label = h.toString();
                if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
                if (label.equalsIgnoreCase(filter)) return true;
            }
        }
        Object langsObj = user.get("languages");
        if (langsObj instanceof Map) {
            for (Object key : ((Map<?, ?>) langsObj).keySet()) {
                String flag = FLAG_MAP.getOrDefault(key.toString(), "🌐");
                if ((flag + " " + key).equalsIgnoreCase(filter)
                        || key.toString().equalsIgnoreCase(filter)) return true;
            }
        }
        return false;
    }

    private void renderCards(List<Map<String, Object>> users) {
        if (!isAdded()) return;
        cardsContainer.removeAllViews();
        if (users.isEmpty()) { showEmpty(); return; }
        emptyState.setVisibility(View.GONE);
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (int i = 0; i < users.size(); i++) buildCard(inf, users.get(i), i);
    }

    private void showEmpty() {
        emptyState.setVisibility(View.VISIBLE);
        cardsContainer.removeAllViews();
    }

    private void buildCard(LayoutInflater inflater, Map<String, Object> user, int index) {
        View card = inflater.inflate(R.layout.item_person_card, cardsContainer, false);

        String otherUid  = str(user, "_uid", "");
        String name      = str(user, "displayName", "Anonymous");
        String photoB64  = str(user, "photo", "");
        String storedBio = str(user, "bio", "");

        String[] parts  = name.trim().split("\\s+");
        String initials = parts.length >= 2
                ? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
                : name.substring(0, Math.min(2, name.length())).toUpperCase();

        // Avatar gradient background
        int[] grad = AVATAR_GRADIENTS[index % AVATAR_GRADIENTS.length];
        View panel = card.findViewById(R.id.person_avatar_panel);
        android.graphics.drawable.GradientDrawable gd =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{grad[0], grad[1]});
        panel.setBackground(gd);

        ((TextView) card.findViewById(R.id.person_initials)).setText(initials);

        if (!photoB64.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(photoB64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ImageView pv = card.findViewById(R.id.person_photo);
                pv.setImageBitmap(bmp);
                pv.setVisibility(View.VISIBLE);
                card.findViewById(R.id.person_initials).setVisibility(View.GONE);
            } catch (Exception ignored) {}
        }

        card.findViewById(R.id.person_online_dot).setVisibility(View.GONE);

        // ── Languages (all) ───────────────────────────────────────────────────
        Object langsObj = user.get("languages");
        String langLine = "";
        if (langsObj instanceof Map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) langsObj).entrySet()) {
                String flag = FLAG_MAP.getOrDefault(e.getKey().toString(), "🌐");
                entries.add(flag + " " + e.getKey() + " · " + e.getValue());
            }
            langLine = android.text.TextUtils.join("  ", entries);
        }
        ((TextView) card.findViewById(R.id.person_languages)).setText(langLine);
        ((TextView) card.findViewById(R.id.person_name)).setText(name);

        // ── Bio ───────────────────────────────────────────────────────────────
        Object hobbiesObj = user.get("hobbies");
        List<?> hobbies = hobbiesObj instanceof List ? (List<?>) hobbiesObj : null;
        String bio = storedBio.isEmpty() ? buildBio(hobbies) : storedBio;
        ((TextView) card.findViewById(R.id.person_bio)).setText(bio);

        // ── Hobbies (all, tighter chips) ──────────────────────────────────────
        ChipGroup chipGroup = card.findViewById(R.id.person_hobby_chips);
        if (hobbies != null) {
            for (Object h : hobbies) {
                String label = h.toString();
                if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
                chipGroup.addView(makeTinyChip(label));
            }
        }

        final String fOtherUid   = otherUid;
        final String fOtherName  = name;
        final String fOtherPhoto = photoB64;
        final String fLangLine   = langLine;
        card.findViewById(R.id.person_chat_btn).setOnClickListener(v ->
                startOrOpenChat(fOtherUid, fOtherName, fOtherPhoto, fLangLine)
        );

        card.setAlpha(0f); card.setTranslationY(20f);
        card.animate().alpha(1f).translationY(0f)
                .setDuration(320).setStartDelay(60L * index)
                .setInterpolator(new DecelerateInterpolator()).start();

        cardsContainer.addView(card);
    }

    private void startOrOpenChat(String otherUid, String otherName,
                                 String otherPhoto, String otherLangs) {
        String[] uids = {myUid, otherUid};
        Arrays.sort(uids);
        String chatId = uids[0] + "_" + uids[1];

        FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(chatId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Map<String, Object> names  = new HashMap<>();
                        Map<String, Object> photos = new HashMap<>();
                        names.put(myUid,    myName);
                        names.put(otherUid, otherName);
                        if (!myPhoto.isEmpty())    photos.put(myUid,    myPhoto);
                        if (!otherPhoto.isEmpty()) photos.put(otherUid, otherPhoto);

                        List<String> participants = Arrays.asList(myUid, otherUid);

                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("participants",         participants);
                        chatData.put("participantNames",     names);
                        chatData.put("participantPhotos",    photos);
                        chatData.put("otherLangs_" + myUid, otherLangs);
                        chatData.put("lastMessage",          "");
                        chatData.put("lastTimestamp",        ServerValue.TIMESTAMP);

                        FirebaseDatabase.getInstance()
                                .getReference("chats")
                                .child(chatId)
                                .setValue(chatData);
                    }

                    if (!isAdded()) return;
                    Intent intent = new Intent(requireContext(), ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_ID,     chatId);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_UID,   otherUid);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_NAME,  otherName);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_LANGS, otherLangs);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_PHOTO, otherPhoto);
                    startActivity(intent);
                });
    }

    private String buildBio(List<?> hobbies) {
        if (hobbies == null || hobbies.isEmpty())
            return "Language learner looking to connect and practice.";
        List<String> labels = new ArrayList<>();
        for (Object h : hobbies) {
            String label = h.toString();
            if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
            labels.add(label.toLowerCase());
            if (labels.size() >= 2) break;
        }
        return "Into " + android.text.TextUtils.join(" and ", labels)
                + ". Looking for language exchange partners!";
    }

    private Chip makeTinyChip(String text) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(false);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_GREEN_BG));
        chip.setTextColor(CHIP_GREEN_TEXT);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(CHIP_STROKE));
        chip.setChipStrokeWidth(1f);
        chip.setTextSize(10f);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(0f);
        chip.setChipStartPadding(6f);
        chip.setChipEndPadding(6f);
        chip.setTextStartPadding(0f);
        chip.setTextEndPadding(0f);
        return chip;
    }

    private String str(Map<?, ?> map, String key, String fallback) {
        if (map == null) return fallback;
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }

    private String strSnap(DataSnapshot snap, String key, String fallback) {
        String v = snap.child(key).getValue(String.class);
        return v != null ? v : fallback;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }
}