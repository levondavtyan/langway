package com.levon.davtyan.langway;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathActivity extends AppCompatActivity {

    public static final String EXTRA_LANGUAGES  = "extra_languages";   // ArrayList<String> "Lang|Level"
    public static final String EXTRA_HOBBIES    = "extra_hobbies";     // ArrayList<String>
    public static final String EXTRA_NAME       = "extra_name";        // String

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English",    "🇬🇧"); put("Spanish",   "🇪🇸"); put("Russian",   "🇷🇺");
        put("Armenian",   "🇦🇲"); put("German",    "🇩🇪"); put("French",    "🇫🇷");
        put("Mandarin",   "🇨🇳"); put("Japanese",  "🇯🇵"); put("Italian",   "🇮🇹");
        put("Portuguese", "🇵🇹"); put("Arabic",    "🇸🇦"); put("Korean",    "🇰🇷");
        put("Dutch",      "🇳🇱"); put("Swedish",   "🇸🇪"); put("Turkish",   "🇹🇷");
    }};

    private static final Map<String, List<String>> HOBBY_PATH_MAP = new HashMap<String, List<String>>() {{
        put("Gaming",      Arrays.asList("Gaming & Esports",   "Online Communities", "Tech Slang"));
        put("Technology",  Arrays.asList("Technical English",  "Tech Jargon",        "STEM Communication"));
        put("Music",       Arrays.asList("Music & Lyrics",     "Cultural Exchange",  "Entertainment Media"));
        put("Travel",      Arrays.asList("Travel & Tourism",   "Everyday Phrases",   "Cultural Immersion"));
        put("Cooking",     Arrays.asList("Food & Culinary",    "Everyday Phrases",   "Cultural Exchange"));
        put("Art",         Arrays.asList("Creative Writing",   "Cultural Exchange",  "Art & Design"));
        put("Sports",      Arrays.asList("Sports Commentary",  "Teamwork Language",  "Broadcasting"));
        put("Movies",      Arrays.asList("Entertainment Media","Film & Script",      "Pop Culture"));
        put("Photography", Arrays.asList("Creative Expression","Social Media",       "Art & Design"));
        put("Nature",      Arrays.asList("Environmental",      "Scientific English", "Travel & Outdoors"));
        put("Wellness",    Arrays.asList("Health & Wellness",  "Mindfulness",        "Medical Basics"));
        put("Theatre",     Arrays.asList("Dramatic Arts",      "Literature",         "Performance"));
        put("Instruments", Arrays.asList("Music Theory",       "Cultural Exchange",  "Arts Performance"));
        put("Fitness",     Arrays.asList("Sports & Fitness",   "Health Coaching",    "Motivational"));
        put("Puzzles",     Arrays.asList("Academic",           "Logic & Reasoning",  "STEM Communication"));
        put("Animals",     Arrays.asList("Environmental",      "Scientific English", "Veterinary Basics"));
        put("Writing",     Arrays.asList("Academic Writing",   "Creative Fiction",   "Journalism"));
        put("Languages",   Arrays.asList("Linguistics",        "Academic",           "Translation"));
        put("Board Games", Arrays.asList("Strategy & Logic",   "Social Language",    "Gaming & Esports"));
        put("Reading",     Arrays.asList("Academic",           "Literary Analysis",  "Creative Writing"));
    }};

    private static final Map<String, Set<String>> TRENDING_MAP = new HashMap<String, Set<String>>() {{
        put("Technology",  new HashSet<>(Arrays.asList("English", "Mandarin", "German")));
        put("Gaming",      new HashSet<>(Arrays.asList("English", "Korean", "Japanese")));
        put("Music",       new HashSet<>(Arrays.asList("English", "Spanish", "French")));
        put("Movies",      new HashSet<>(Arrays.asList("English", "Spanish", "Korean")));
        put("Travel",      new HashSet<>(Arrays.asList("Spanish", "French", "Japanese", "Italian")));
        put("Cooking",     new HashSet<>(Arrays.asList("Italian", "French", "Japanese")));
        put("Sports",      new HashSet<>(Arrays.asList("English", "Spanish", "Portuguese")));
        put("Art",         new HashSet<>(Arrays.asList("French", "Italian", "German")));
        put("Writing",     new HashSet<>(Arrays.asList("English", "French", "Spanish")));
    }};

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_path);

        com.google.android.material.button.MaterialButton startBtn =
                findViewById(R.id.path_start_btn);
        ViewCompat.setOnApplyWindowInsetsListener(startBtn, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = insets.bottom + (int)(20 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(lp);
            return windowInsets;
        });

        final ArrayList<String> langPairs = getIntent().getStringArrayListExtra(EXTRA_LANGUAGES) != null
                ? getIntent().getStringArrayListExtra(EXTRA_LANGUAGES) : new ArrayList<>();
        final ArrayList<String> hobbies = getIntent().getStringArrayListExtra(EXTRA_HOBBIES) != null
                ? getIntent().getStringArrayListExtra(EXTRA_HOBBIES) : new ArrayList<>();
        String name                  = getIntent().getStringExtra(EXTRA_NAME);

        TextView greetingView = findViewById(R.id.path_greeting);
        TextView titleView    = findViewById(R.id.path_title);
        String firstName = (name != null && name.contains(" "))
                ? name.split(" ")[0] : (name != null ? name : "");
        greetingView.setText("YOUR LEARNING PATH" + (firstName.isEmpty() ? "" : ", " + firstName.toUpperCase()));
        titleView.setText(hobbies.isEmpty() ? "Tailored just for you"
                : "Built around your interests");

        ChipGroup hobbyChipGroup = findViewById(R.id.path_hobby_chips);
        for (String hobby : hobbies) {
            String label = hobby.contains(" ") ? hobby.substring(hobby.indexOf(" ") + 1) : hobby;
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.brand_green_light);
            chip.setChipStrokeColorResource(R.color.outline);
            chip.setChipStrokeWidth(1f);
            chip.setTextColor(getColor(R.color.brand_green_dark));
            chip.setTextSize(11f);
            hobbyChipGroup.addView(chip);
        }

        LinearLayout container = findViewById(R.id.path_cards_container);
        LayoutInflater inflater = LayoutInflater.from(this);

        List<String> allPathTypes = new ArrayList<>();
        Set<String> trendingLangs = new HashSet<>();
        for (String hobby : hobbies) {
            String key = extractHobbyKey(hobby);
            List<String> types = HOBBY_PATH_MAP.get(key);
            if (types != null) allPathTypes.addAll(types);
            Set<String> trending = TRENDING_MAP.get(key);
            if (trending != null) trendingLangs.addAll(trending);
        }

        List<String> dedupedTypes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String t : allPathTypes) {
            if (seen.add(t)) dedupedTypes.add(t);
            if (dedupedTypes.size() >= 3) break;
        }

        if (dedupedTypes.isEmpty()) {
            dedupedTypes.add("Conversational");
            dedupedTypes.add("Everyday Phrases");
            dedupedTypes.add("Cultural Exchange");
        }

        int cardIndex = 0;
        for (String pair : langPairs) {
            String[] parts = pair.split("\\|");
            if (parts.length < 2) continue;
            String lang  = parts[0];
            String level = parts[1];

            View card = inflater.inflate(R.layout.item_path_card, container, false);

            ((TextView) card.findViewById(R.id.path_card_icon))
                    .setText(FLAG_MAP.getOrDefault(lang, "🌐"));
            ((TextView) card.findViewById(R.id.path_card_language)).setText(lang);
            ((TextView) card.findViewById(R.id.path_card_proficiency)).setText(level);

            TextView trendBadge = card.findViewById(R.id.path_card_trend_badge);
            if (trendingLangs.contains(lang)) {
                trendBadge.setText("🔥 Trending");
                trendBadge.setVisibility(View.VISIBLE);
            }

            ChipGroup chipGroup = card.findViewById(R.id.path_card_types);
            List<String> cardTypes = buildCardTypes(lang, level, dedupedTypes);
            for (String type : cardTypes) {
                Chip chip = new Chip(this);
                chip.setText(type);
                chip.setCheckable(false);
                chip.setChipBackgroundColorResource(R.color.brand_blue_light);
                chip.setChipStrokeColorResource(R.color.outline);
                chip.setChipStrokeWidth(1f);
                chip.setTextColor(getColor(R.color.brand_blue_dark));
                chip.setTextSize(11f);
                chipGroup.addView(chip);
            }

            ((TextView) card.findViewById(R.id.path_card_reason))
                    .setText(buildReason(lang, level, hobbies));

            card.setAlpha(0f);
            card.setTranslationY(24f);
            final long delay = 200 + cardIndex * 100L;
            card.animate().alpha(1f).translationY(0f)
                    .setDuration(380).setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator()).start();

            container.addView(card);
            cardIndex++;
        }

        findViewById(R.id.path_start_btn).setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra(HomeActivity.EXTRA_NAME, name);
            intent.putStringArrayListExtra(HomeActivity.EXTRA_LANGUAGES, langPairs);
            intent.putStringArrayListExtra(HomeActivity.EXTRA_HOBBIES, hobbies);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        View header = findViewById(R.id.path_header);
        header.setAlpha(0f);
        header.animate().alpha(1f).setDuration(500).start();
    }

    private List<String> buildCardTypes(String lang, String level, List<String> basePaths) {
        List<String> result = new ArrayList<>();
        String qualifier;
        if (level.startsWith("A")) qualifier = "Practical ";
        else if (level.startsWith("B")) qualifier = "Conversational ";
        else qualifier = "Professional ";

        for (String base : basePaths) {
            result.add(qualifier + lang + " – " + base);
            if (result.size() >= 2) break;
        }
        return result;
    }

    private String buildReason(String lang, String level, List<String> hobbies) {
        String hobbySnippet = "";
        if (!hobbies.isEmpty()) {
            List<String> clean = new ArrayList<>();
            for (String h : hobbies) {
                String label = h.contains(" ") ? h.substring(h.indexOf(" ") + 1) : h;
                clean.add(label);
                if (clean.size() >= 2) break;
            }
            hobbySnippet = " — great for " + android.text.TextUtils.join(" and ", clean);
        }
        String levelDesc;
        if (level.startsWith("A1") || level.startsWith("A2")) levelDesc = "beginners";
        else if (level.startsWith("B")) levelDesc = "intermediate learners";
        else levelDesc = "advanced speakers";

        return lang + " is one of the most in-demand languages globally" + hobbySnippet
                + ". This path is paced for " + levelDesc
                + " and focuses on real-world fluency you can use straight away.";
    }

    private String extractHobbyKey(String hobby) {
        if (hobby.contains(" ")) {
            return hobby.substring(hobby.indexOf(" ") + 1).trim();
        }
        return hobby.trim();
    }
}