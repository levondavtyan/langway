package com.levon.davtyan.langway.home;

import android.content.Intent;
import android.util.Log;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuth.AuthStateListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.levon.davtyan.langway.ChatActivity;
import com.levon.davtyan.langway.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            Log.d("LangwayDiscover", "AuthState ready, myUid=" + myUid);

            FirebaseFirestore.getInstance().collection("users").document(myUid).get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded()) return;
                        if (doc != null && doc.exists()) {
                            myName  = str(doc.getData(), "displayName", "");
                            myPhoto = str(doc.getData(), "photo", "");

                            Object langsObj = doc.get("languages");
                            if (langsObj instanceof Map) {
                                List<String> entries = new ArrayList<>();
                                for (Map.Entry<?, ?> e : ((Map<?, ?>) langsObj).entrySet()) {
                                    myLangKeys.add(e.getKey().toString());
                                    myLangPairs.add(e.getKey() + "|" + e.getValue());
                                    entries.add(FLAG_MAP.getOrDefault(e.getKey().toString(), "🌐")
                                            + " " + e.getKey() + " · " + e.getValue());
                                }
                                myLangStr = android.text.TextUtils.join("  ", entries);
                            }

                            List<?> hobbiesList = (List<?>) doc.get("hobbies");
                            if (hobbiesList != null) {
                                for (Object h : hobbiesList) {
                                    myHobbies.add(h.toString());
                                    String key = h.toString();
                                    if (key.contains(" ")) key = key.substring(key.indexOf(" ") + 1);
                                    myHobbyKeys.add(key.toLowerCase());
                                }
                            }
                        }
                        loadMatchingUsers();
                    })
                    .addOnFailureListener(e -> loadMatchingUsers());
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    private void loadMatchingUsers() {
        Log.d("LangwayDiscover", "loadMatchingUsers start, myLangKeys=" + myLangKeys + " myHobbyKeys=" + myHobbyKeys);
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    allMatches.clear();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (doc.getId().equals(myUid)) continue;

                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("_uid", doc.getId());

                        boolean matched = hasMatch(data);
                        Log.d("LangwayDiscover", "User " + doc.getId() + " name=" + str(data,"displayName","?") + " langs=" + data.get("languages") + " hobbies=" + data.get("hobbies") + " → match=" + matched);
                        if (matched) allMatches.add(data);
                    }

                    Log.d("LangwayDiscover", "Total matches: " + allMatches.size());
                    buildFilters();
                    renderCards(allMatches);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) showEmpty();
                });
    }

    private boolean hasMatch(Map<String, Object> user) {
        // Language overlap
        Object langsObj = user.get("languages");
        if (langsObj instanceof Map) {
            for (Object key : ((Map<?, ?>) langsObj).keySet()) {
                if (myLangKeys.contains(key.toString())) return true;
            }
        }

        List<?> hobbies = (List<?>) user.get("hobbies");
        if (hobbies != null) {
            for (Object h : hobbies) {
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
        List<?> hobbies = (List<?>) user.get("hobbies");
        if (hobbies != null) {
            for (Object h : hobbies) {
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

        Object langsObj = user.get("languages");
        String firstFlag = "🌐", langLine = "";
        if (langsObj instanceof Map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) langsObj).entrySet()) {
                String flag = FLAG_MAP.getOrDefault(e.getKey().toString(), "🌐");
                if (firstFlag.equals("🌐")) firstFlag = flag;
                entries.add(flag + " " + e.getKey() + " · " + e.getValue());
            }
            langLine = android.text.TextUtils.join("  ", entries);
        }
        ((TextView) card.findViewById(R.id.person_flag)).setText(firstFlag);
        ((TextView) card.findViewById(R.id.person_languages)).setText(langLine);
        ((TextView) card.findViewById(R.id.person_name)).setText(name);

        List<?> hobbies = (List<?>) user.get("hobbies");
        String bio = storedBio.isEmpty() ? buildBio(hobbies) : storedBio;
        ((TextView) card.findViewById(R.id.person_bio)).setText(bio);

        ChipGroup chipGroup = card.findViewById(R.id.person_hobby_chips);
        if (hobbies != null) {
            int count = 0;
            for (Object h : hobbies) {
                if (count++ >= 3) break;
                String label = h.toString();
                if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
                chipGroup.addView(makeTinyChip(label));
            }
        }

        if (index % 3 == 0) card.findViewById(R.id.person_online_dot).setVisibility(View.VISIBLE);

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

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("chats").document(chatId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Map<String, Object> names  = new HashMap<>();
                        Map<String, Object> photos = new HashMap<>();
                        names.put(myUid,    myName);
                        names.put(otherUid, otherName);
                        if (!myPhoto.isEmpty())    photos.put(myUid,    myPhoto);
                        if (!otherPhoto.isEmpty()) photos.put(otherUid, otherPhoto);

                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("participants",                   Arrays.asList(myUid, otherUid));
                        chatData.put("participantNames",               names);
                        chatData.put("participantPhotos",              photos);
                        chatData.put("otherLangs_" + myUid,           otherLangs);
                        chatData.put("lastMessage",                    "");
                        chatData.put("lastTimestamp",                  Timestamp.now());
                        db.collection("chats").document(chatId).set(chatData);
                    }

                    if (!isAdded()) return;
                    Intent intent = new Intent(requireContext(), ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_ID,    chatId);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_UID,  otherUid);
                    intent.putExtra(ChatActivity.EXTRA_OTHER_NAME, otherName);
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
        chip.setChipMinHeight(28f);
        return chip;
    }

    private String str(Map<?, ?> map, String key, String fallback) {
        if (map == null) return fallback;
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }
}