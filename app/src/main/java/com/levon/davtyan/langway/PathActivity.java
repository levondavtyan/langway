package com.levon.davtyan.langway;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class PathActivity extends AppCompatActivity {

    private static final String TAG = "PathActivity";
    private static final String GROK_API_KEY = "YOUR_GROK_API_KEY";

    public static final String EXTRA_LANGUAGES = "extra_languages";
    public static final String EXTRA_HOBBIES   = "extra_hobbies";
    public static final String EXTRA_NAME      = "extra_name";

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English","🇬🇧"); put("Spanish","🇪🇸"); put("Russian","🇷🇺");
        put("Armenian","🇦🇲"); put("German","🇩🇪"); put("French","🇫🇷");
        put("Mandarin","🇨🇳"); put("Japanese","🇯🇵"); put("Italian","🇮🇹");
        put("Portuguese","🇵🇹"); put("Arabic","🇸🇦"); put("Korean","🇰🇷");
        put("Dutch","🇳🇱"); put("Swedish","🇸🇪"); put("Turkish","🇹🇷");
    }};

    // Hard colors so dark-mode system can't override chip backgrounds
    private static final int CHIP_BLUE_BG   = 0xFFE3ECFF;
    private static final int CHIP_BLUE_TEXT = 0xFF1A56CC;
    private static final int CHIP_GREEN_BG  = 0xFFDFFAF3;
    private static final int CHIP_GREEN_TEXT= 0xFF00A87A;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_path);

        com.google.android.material.button.MaterialButton startBtn = findViewById(R.id.path_start_btn);
        ViewCompat.setOnApplyWindowInsetsListener(startBtn, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = insets.bottom + (int)(20 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(lp);
            return windowInsets;
        });

        ArrayList<String> langPairs = getIntent().getStringArrayListExtra(EXTRA_LANGUAGES);
        ArrayList<String> hobbies   = getIntent().getStringArrayListExtra(EXTRA_HOBBIES);
        String name                 = getIntent().getStringExtra(EXTRA_NAME);
        if (langPairs == null) langPairs = new ArrayList<>();
        if (hobbies   == null) hobbies   = new ArrayList<>();

        String firstName = (name != null && name.contains(" "))
                ? name.split(" ")[0] : (name != null ? name : "");
        ((TextView)findViewById(R.id.path_greeting)).setText(
                "YOUR LEARNING PATH" + (firstName.isEmpty() ? "" : ", " + firstName.toUpperCase()));
        ((TextView)findViewById(R.id.path_title)).setText(
                hobbies.isEmpty() ? "Tailored just for you" : "Built around your interests");

        // Hobby chips — force explicit colors
        ChipGroup hobbyChipGroup = findViewById(R.id.path_hobby_chips);
        for (String hobby : hobbies) {
            String label = hobby.contains(" ") ? hobby.substring(hobby.indexOf(" ") + 1) : hobby;
            hobbyChipGroup.addView(makeChip(label, CHIP_GREEN_BG, CHIP_GREEN_TEXT));
        }

        LinearLayout container = findViewById(R.id.path_cards_container);
        LayoutInflater inflater = LayoutInflater.from(this);
        final ArrayList<String> finalHobbies  = hobbies;
        final ArrayList<String> finalLangPairs = langPairs;
        final String fFirstName = firstName;

        int cardIndex = 0;
        for (String pair : langPairs) {
            String[] parts = pair.split("\\|");
            if (parts.length < 2) continue;
            String lang  = parts[0];
            String level = parts[1];

            View card = inflater.inflate(R.layout.item_path_card, container, false);
            ((TextView)card.findViewById(R.id.path_card_icon)).setText(FLAG_MAP.getOrDefault(lang, "🌐"));
            ((TextView)card.findViewById(R.id.path_card_language)).setText(lang);
            ((TextView)card.findViewById(R.id.path_card_proficiency)).setText(level);
            card.findViewById(R.id.path_card_trend_badge).setVisibility(View.GONE);

            ChipGroup chipGroup  = card.findViewById(R.id.path_card_types);
            TextView reasonView  = card.findViewById(R.id.path_card_reason);
            reasonView.setText("Generating your personalised path…");

            card.setAlpha(0f); card.setTranslationY(24f);
            final long delay = 200 + cardIndex * 100L;
            card.animate().alpha(1f).translationY(0f).setDuration(380).setStartDelay(delay)
                    .setInterpolator(new DecelerateInterpolator()).start();
            container.addView(card);

            final String fLang = lang, fLevel = level;
            new Thread(() -> {
                PathCardData data = fetchCardData(fLang, fLevel, finalHobbies, fFirstName);
                runOnUiThread(() -> {
                    if (data == null) {
                        reasonView.setText(buildFallbackReason(fLang, fLevel, finalHobbies));
                        chipGroup.addView(makeChip((fLevel.startsWith("A") ? "Practical " : fLevel.startsWith("B") ? "Conversational " : "Professional ") + fLang, CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                        chipGroup.addView(makeChip("Cultural Exchange", CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                        return;
                    }
                    reasonView.setText(data.reason);
                    // Show trending badge if AI says it's trending
                    if (data.isTrending) {
                        TextView badge = card.findViewById(R.id.path_card_trend_badge);
                        badge.setText("🔥 Trending");
                        badge.setVisibility(View.VISIBLE);
                    }
                    for (String label : data.pathLabels) {
                        chipGroup.addView(makeChip(label, CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                    }
                });
            }).start();

            cardIndex++;
        }

        final String finalName = name;
        startBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.putExtra(HomeActivity.EXTRA_NAME, finalName);
            intent.putStringArrayListExtra(HomeActivity.EXTRA_LANGUAGES, finalLangPairs);
            intent.putStringArrayListExtra(HomeActivity.EXTRA_HOBBIES, finalHobbies);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        View header = findViewById(R.id.path_header);
        header.setAlpha(0f);
        header.animate().alpha(1f).setDuration(500).start();
    }

    // ── Data class returned from Claude ──────────────────────────────────────
    static class PathCardData {
        String reason;
        List<String> pathLabels;
        boolean isTrending;
        PathCardData(String reason, List<String> pathLabels, boolean isTrending) {
            this.reason = reason; this.pathLabels = pathLabels; this.isTrending = isTrending;
        }
    }

    /**
     * Calls Claude API to generate:
     * - A personalised reason sentence mentioning their specific hobbies and level
     * - 2-3 currently trending path type labels for this specific language
     * - Whether this language is trending globally right now
     */
    private PathCardData fetchCardData(String lang, String level, List<String> hobbies, String firstName) {
        try {
            String hobbyList = hobbies.isEmpty() ? "general interests"
                    : android.text.TextUtils.join(", ", hobbies).replaceAll("[^a-zA-Z,\\s]", "").trim();

            String prompt =
                    "You are an expert language learning advisor with up-to-date knowledge of job markets, " +
                            "cultural trends, and global demand for languages in 2025. " +
                            "A user" + (firstName.isEmpty() ? "" : " named " + firstName) +
                            " is learning " + lang + " at " + level + " level. " +
                            "Their interests are: " + hobbyList + ". " +
                            "Based on current 2025 trends in tech, entertainment, business and culture, generate:\n" +
                            "1. A personalised 2-sentence reason why " + lang + " is valuable for someone with their " +
                            "specific interests — be concrete, mention their actual hobbies, and reference real current demand.\n" +
                            "2. Two to three short path type labels (max 4 words each) that are currently in high demand " +
                            "for " + lang + " speakers in fields related to their interests.\n" +
                            "3. Whether " + lang + " is currently trending (high global demand) — true or false.\n\n" +
                            "Respond ONLY with a valid JSON object, no markdown, no extra text:\n" +
                            "{\"reason\":\"...\",\"path1\":\"...\",\"path2\":\"...\",\"path3\":\"...\",\"trending\":true}";

            URL url = new URL("https://api.x.ai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + GROK_API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);

            JSONObject body = new JSONObject();
            body.put("model", "grok-3-mini");
            body.put("max_tokens", 400);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            body.put("messages", messages);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(input); }

            int status = conn.getResponseCode();
            if (status != 200) {
                Log.e(TAG, "Claude API status: " + status);
                return null;
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name());
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) sb.append(scanner.nextLine());
            scanner.close();

            JSONObject response = new JSONObject(sb.toString());
            String text = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();

            // Strip any accidental markdown fences
            text = text.replaceAll("```json|```", "").trim();
            JSONObject parsed = new JSONObject(text);

            String reason   = parsed.getString("reason");
            boolean trending = parsed.optBoolean("trending", false);
            List<String> labels = new ArrayList<>();
            labels.add(parsed.getString("path1"));
            labels.add(parsed.getString("path2"));
            if (parsed.has("path3") && !parsed.getString("path3").isEmpty())
                labels.add(parsed.getString("path3"));

            return new PathCardData(reason, labels, trending);

        } catch (Exception e) {
            Log.e(TAG, "Claude API failed: " + e.getMessage());
            return null;
        }
    }

    private String buildFallbackReason(String lang, String level, List<String> hobbies) {
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
        String levelDesc = level.startsWith("A") ? "beginners"
                : level.startsWith("B") ? "intermediate learners" : "advanced speakers";
        return lang + " is one of the most in-demand languages globally" + hobbySnippet
                + ". This path is paced for " + levelDesc + " and focuses on real-world fluency.";
    }

    private Chip makeChip(String text, int bgColor, int textColor) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
        chip.setTextColor(textColor);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFB2DDD6));
        chip.setChipStrokeWidth(1f);
        chip.setTextSize(11f);
        return chip;
    }
}