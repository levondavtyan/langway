package com.levon.davtyan.langway.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.levon.davtyan.langway.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoverFragment extends Fragment {

    // Hard colors — immune to dark-mode theming
    private static final int CHIP_GREEN_BG    = 0xFFDFFAF3;
    private static final int CHIP_GREEN_TEXT  = 0xFF00A87A;
    private static final int CHIP_BLUE_BG     = 0xFFE3ECFF;
    private static final int CHIP_BLUE_TEXT   = 0xFF1A56CC;
    private static final int CHIP_SEL_BG      = 0xFF00C896;
    private static final int CHIP_SEL_TEXT    = 0xFFFFFFFF;
    private static final int CHIP_UNSEL_BG    = 0xFFFFFFFF;
    private static final int CHIP_UNSEL_TEXT  = 0xFF0D2626;
    private static final int CHIP_STROKE      = 0xFFB2DDD6;

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English","🇬🇧"); put("Spanish","🇪🇸"); put("Russian","🇷🇺");
        put("Armenian","🇦🇲"); put("German","🇩🇪"); put("French","🇫🇷");
        put("Mandarin","🇨🇳"); put("Japanese","🇯🇵"); put("Italian","🇮🇹");
        put("Portuguese","🇵🇹"); put("Arabic","🇸🇦"); put("Korean","🇰🇷");
        put("Dutch","🇳🇱"); put("Swedish","🇸🇪"); put("Turkish","🇹🇷");
    }};

    // Avatar background gradients — cycles through a palette per user
    private static final int[][] AVATAR_GRADIENTS = {
            {0xFF00C896, 0xFF2979FF},
            {0xFFFF6B6B, 0xFFFFD93D},
            {0xFF6C63FF, 0xFF3EC6E0},
            {0xFFFF8C42, 0xFFFF3CAC},
            {0xFF43E97B, 0xFF38F9D7},
            {0xFF667EEA, 0xFF764BA2},
    };

    private ChipGroup filterChips;
    private LinearLayout cardsContainer;
    private LinearLayout emptyState;

    // All loaded users from Firestore
    private final List<Map<String, Object>> allUsers = new ArrayList<>();
    // Currently active filter label (null = show all)
    private String activeFilter = null;

    // Current user's data for building filters
    private List<String> myHobbies   = new ArrayList<>();
    private List<String> myLanguages = new ArrayList<>(); // "Lang|Level"

    @Nullable
    @Override
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

        // Read current user's profile to build filters
        Bundle args = getArguments();
        if (args != null) {
            ArrayList<String> h = args.getStringArrayList("hobbies");
            ArrayList<String> l = args.getStringArrayList("languages");
            if (h != null) myHobbies.addAll(h);
            if (l != null) myLanguages.addAll(l);
        }

        // Also load from Firestore in case args are absent (sign-in path)
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded() || doc == null || !doc.exists()) {
                            buildFiltersAndLoad();
                            return;
                        }
                        if (myHobbies.isEmpty()) {
                            List<?> h = (List<?>) doc.get("hobbies");
                            if (h != null) for (Object o : h) myHobbies.add(o.toString());
                        }
                        if (myLanguages.isEmpty()) {
                            Object langsObj = doc.get("languages");
                            if (langsObj instanceof Map) {
                                for (Map.Entry<?, ?> e : ((Map<?, ?>) langsObj).entrySet())
                                    myLanguages.add(e.getKey() + "|" + e.getValue());
                            }
                        }
                        buildFiltersAndLoad();
                    })
                    .addOnFailureListener(e -> buildFiltersAndLoad());
        } else {
            buildFiltersAndLoad();
        }
    }

    // ── Build filter chips from user's hobbies + languages, then load users ─
    private void buildFiltersAndLoad() {
        if (!isAdded()) return;

        filterChips.removeAllViews();

        // "All" chip first
        addFilterChip("All", true);

        // Language filters — use the language name only
        for (String pair : myLanguages) {
            String lang = pair.split("\\|")[0];
            String flag = FLAG_MAP.getOrDefault(lang, "🌐");
            addFilterChip(flag + " " + lang, false);
        }

        // Hobby filters — strip emoji prefix
        for (String hobby : myHobbies) {
            String label = hobby.contains(" ") ? hobby.substring(hobby.indexOf(" ") + 1) : hobby;
            addFilterChip(label, false);
        }

        loadUsersFromFirestore();
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
            // Deselect all chips visually
            for (int i = 0; i < filterChips.getChildCount(); i++) {
                Chip c = (Chip) filterChips.getChildAt(i);
                c.setChecked(false);
                c.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_UNSEL_BG));
                c.setTextColor(CHIP_UNSEL_TEXT);
            }
            // Select tapped chip
            chip.setChecked(true);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(CHIP_SEL_BG));
            chip.setTextColor(CHIP_SEL_TEXT);

            activeFilter = label.equals("All") ? null : label;
            renderCards();
        });

        filterChips.addView(chip);
    }

    // ── Load all other users from Firestore ──────────────────────────────────
    private void loadUsersFromFirestore() {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    allUsers.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        if (doc.getId().equals(myUid)) continue; // skip self
                        allUsers.add(doc.getData());
                    }
                    renderCards();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    // Show placeholder cards if Firestore fails
                    renderPlaceholders();
                });
    }

    // ── Render cards filtered by activeFilter ────────────────────────────────
    private void renderCards() {
        if (!isAdded()) return;
        cardsContainer.removeAllViews();

        List<Map<String, Object>> toShow = new ArrayList<>();
        for (Map<String, Object> user : allUsers) {
            if (matchesFilter(user)) toShow.add(user);
        }

        emptyState.setVisibility(toShow.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < toShow.size(); i++) {
            View card = buildCard(inflater, toShow.get(i), i);
            cardsContainer.addView(card);
        }
    }

    private boolean matchesFilter(Map<String, Object> user) {
        if (activeFilter == null) return true;

        // Check if filter matches a hobby
        List<?> hobbies = (List<?>) user.get("hobbies");
        if (hobbies != null) {
            for (Object h : hobbies) {
                String label = h.toString();
                if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
                if (label.equalsIgnoreCase(activeFilter)) return true;
            }
        }

        // Check if filter matches a language
        Object langsObj = user.get("languages");
        if (langsObj instanceof Map) {
            for (Object key : ((Map<?, ?>) langsObj).keySet()) {
                String flag = FLAG_MAP.getOrDefault(key.toString(), "🌐");
                String full = flag + " " + key.toString();
                if (full.equalsIgnoreCase(activeFilter) || key.toString().equalsIgnoreCase(activeFilter))
                    return true;
            }
        }
        return false;
    }

    // ── Build a single person card view ──────────────────────────────────────
    private View buildCard(LayoutInflater inflater, Map<String, Object> user, int index) {
        View card = inflater.inflate(R.layout.item_person_card, cardsContainer, false);

        String name  = getStr(user, "displayName", "Anonymous");
        String email = getStr(user, "email", "");

        // Initials
        String initials = "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) initials = "" + parts[0].charAt(0) + parts[1].charAt(0);
        else if (name.length() > 0) initials = name.substring(0, Math.min(2, name.length()));
        initials = initials.toUpperCase();

        // Avatar panel background — cycle through gradient palette
        int[] grad = AVATAR_GRADIENTS[index % AVATAR_GRADIENTS.length];
        View panel = card.findViewById(R.id.person_avatar_panel);
        panel.setBackground(buildGradientDrawable(grad[0], grad[1]));

        ((TextView) card.findViewById(R.id.person_initials)).setText(initials);

        // Flag — first language in their profile
        Object langsObj = user.get("languages");
        String firstFlag = "🌐";
        String langLine  = "";
        if (langsObj instanceof Map) {
            Map<?, ?> langsMap = (Map<?, ?>) langsObj;
            List<String> langEntries = new ArrayList<>();
            for (Map.Entry<?, ?> e : langsMap.entrySet()) {
                String flag = FLAG_MAP.getOrDefault(e.getKey().toString(), "🌐");
                if (firstFlag.equals("🌐")) firstFlag = flag;
                langEntries.add(flag + " " + e.getKey() + " · " + e.getValue());
            }
            langLine = android.text.TextUtils.join("  ", langEntries);
        }
        ((TextView) card.findViewById(R.id.person_flag)).setText(firstFlag);
        ((TextView) card.findViewById(R.id.person_languages)).setText(langLine);

        // Name
        ((TextView) card.findViewById(R.id.person_name)).setText(name);

        // Bio — generate from hobbies if no bio field
        List<?> hobbies = (List<?>) user.get("hobbies");
        String bio = buildBio(name, hobbies);
        ((TextView) card.findViewById(R.id.person_bio)).setText(bio);

        // Online dot — show for ~30% of users (index mod 3 == 0) as a UI hint
        if (index % 3 == 0) card.findViewById(R.id.person_online_dot).setVisibility(View.VISIBLE);

        // Hobby chips — max 3
        ChipGroup chipGroup = card.findViewById(R.id.person_hobby_chips);
        if (hobbies != null) {
            int count = 0;
            for (Object h : hobbies) {
                if (count >= 3) break;
                String label = h.toString();
                if (label.contains(" ")) label = label.substring(label.indexOf(" ") + 1);
                chipGroup.addView(makeTinyChip(label));
                count++;
            }
        }

        // Start chatting button
        card.findViewById(R.id.person_chat_btn).setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "Starting chat with " + name + "…", Toast.LENGTH_SHORT).show()
        );

        // Staggered entrance animation
        card.setAlpha(0f);
        card.setTranslationY(20f);
        card.animate().alpha(1f).translationY(0f)
                .setDuration(320).setStartDelay(60L * index)
                .setInterpolator(new DecelerateInterpolator()).start();

        return card;
    }

    // ── Placeholder cards shown when Firestore fails ─────────────────────────
    private void renderPlaceholders() {
        if (!isAdded()) return;
        cardsContainer.removeAllViews();

        String[][] placeholders = {
                {"Maria G.", "🇪🇸", "Loves travel and cooking. Looking for English practice partners."},
                {"Kenji T.", "🇯🇵", "Anime fan and guitarist. Happy to teach Japanese in exchange!"},
                {"Lena K.", "🇩🇪", "Tech enthusiast and avid reader. Let's swap languages!"},
        };

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < placeholders.length; i++) {
            Map<String, Object> fake = new HashMap<>();
            fake.put("displayName", placeholders[i][0]);
            fake.put("email", "");
            View card = buildCard(inflater, fake, i);
            cardsContainer.addView(card);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildBio(String name, List<?> hobbies) {
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

    private android.graphics.drawable.GradientDrawable buildGradientDrawable(int start, int end) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        return gd;
    }

    private String getStr(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v != null ? v.toString() : fallback;
    }
}