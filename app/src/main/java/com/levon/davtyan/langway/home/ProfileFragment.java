package com.levon.davtyan.langway.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.levon.davtyan.langway.MainActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.chip.ChipGroup;
import com.levon.davtyan.langway.R;

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

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private static final String GROK_API_KEY = "YOUR_GROK_API_KEY";

    // Hard colors — not resource refs — so dark mode can't override
    private static final int CHIP_BLUE_BG   = 0xFFE3ECFF;
    private static final int CHIP_BLUE_TEXT = 0xFF1A56CC;
    private static final int CHIP_GREEN_BG  = 0xFFDFFAF3;
    private static final int CHIP_GREEN_TEXT= 0xFF00A87A;

    private static final Map<String, String> FLAG_MAP = new HashMap<String, String>() {{
        put("English","🇬🇧"); put("Spanish","🇪🇸"); put("Russian","🇷🇺");
        put("Armenian","🇦🇲"); put("German","🇩🇪"); put("French","🇫🇷");
        put("Mandarin","🇨🇳"); put("Japanese","🇯🇵"); put("Italian","🇮🇹");
        put("Portuguese","🇵🇹"); put("Arabic","🇸🇦"); put("Korean","🇰🇷");
        put("Dutch","🇳🇱"); put("Swedish","🇸🇪"); put("Turkish","🇹🇷");
    }};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        TextView     nameView    = v.findViewById(R.id.profile_name);
        TextView     emailView   = v.findViewById(R.id.profile_email);
        LinearLayout pathCards   = v.findViewById(R.id.profile_path_cards);
        CardView     hobbiesCard = v.findViewById(R.id.profile_hobbies_card);
        ChipGroup    hobbyChips  = v.findViewById(R.id.profile_hobby_chips);
        MaterialButton signOutBtn = v.findViewById(R.id.profile_sign_out_btn);

        // ── Populate from args immediately (fast, no network) ────────────────
        Bundle args = getArguments();
        String firstName = "";
        List<String> langPairsArg = new ArrayList<>();
        List<String> hobbiesArg   = new ArrayList<>();

        if (args != null) {
            String name = args.getString("name", "");
            if (!name.isEmpty()) {
                nameView.setText(name);
                firstName = name.contains(" ") ? name.split(" ")[0] : name;
            }
            ArrayList<String> langs    = args.getStringArrayList("languages");
            ArrayList<String> hobbies  = args.getStringArrayList("hobbies");
            if (langs   != null) langPairsArg.addAll(langs);
            if (hobbies != null) {
                hobbiesArg.addAll(hobbies);
                showHobbyChips(hobbyChips, hobbiesCard, hobbiesArg);
            }
        }

        // ── Then load from Firestore (handles sign-in path where args are empty) ─
        final String fFirstName = firstName;
        final List<String> fLangPairs = langPairsArg;
        final List<String> fHobbies   = hobbiesArg;

        com.google.firebase.auth.FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            if (fbUser.getEmail() != null) emailView.setText(fbUser.getEmail());

            FirebaseFirestore.getInstance()
                    .collection("users").document(fbUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded() || doc == null || !doc.exists()) {
                            buildPathCards(pathCards, fLangPairs, fHobbies, fFirstName);
                            return;
                        }

                        String displayName = doc.getString("displayName");
                        if (displayName != null && !displayName.isEmpty())
                            nameView.setText(displayName);

                        // Build lang pairs list from Firestore Map
                        List<String> langPairs = new ArrayList<>();
                        Object langsObj = doc.get("languages");
                        if (langsObj instanceof Map) {
                            for (Map.Entry<?, ?> entry : ((Map<?, ?>) langsObj).entrySet()) {
                                langPairs.add(entry.getKey() + "|" + entry.getValue());
                            }
                        }

                        List<String> hobbies = new ArrayList<>();
                        List<?> hobbiesList = (List<?>) doc.get("hobbies");
                        if (hobbiesList != null) {
                            for (Object h : hobbiesList) hobbies.add(h.toString());
                            showHobbyChips(hobbyChips, hobbiesCard, hobbies);
                        }

                        String fn = displayName != null && displayName.contains(" ")
                                ? displayName.split(" ")[0]
                                : (displayName != null ? displayName : fFirstName);

                        buildPathCards(pathCards, langPairs, hobbies, fn);
                    })
                    .addOnFailureListener(e -> buildPathCards(pathCards, fLangPairs, fHobbies, fFirstName));
        } else {
            buildPathCards(pathCards, fLangPairs, fHobbies, fFirstName);
        }

        // ── Sign out ──────────────────────────────────────────────────────────
        signOutBtn.setOnClickListener(btn -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    /** Inflate item_path_card.xml for each language, fire AI for content */
    private void buildPathCards(LinearLayout container, List<String> langPairs,
                                List<String> hobbies, String firstName) {
        if (!isAdded()) return;
        container.removeAllViews();

        if (langPairs.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        final List<String> fHobbies = hobbies;
        final String fFirst = firstName;

        for (String pair : langPairs) {
            String[] parts = pair.split("\\|");
            if (parts.length < 2) continue;
            String lang  = parts[0];
            String level = parts[1];

            View card = inflater.inflate(R.layout.item_path_card, container, false);
            ((TextView) card.findViewById(R.id.path_card_icon)).setText(FLAG_MAP.getOrDefault(lang, "🌐"));
            ((TextView) card.findViewById(R.id.path_card_language)).setText(lang);
            ((TextView) card.findViewById(R.id.path_card_proficiency)).setText(level);
            card.findViewById(R.id.path_card_trend_badge).setVisibility(View.GONE);

            ChipGroup chipGroup = card.findViewById(R.id.path_card_types);
            TextView  reasonView = card.findViewById(R.id.path_card_reason);
            reasonView.setText("Loading your path…");

            container.addView(card);

            final String fLang = lang, fLevel = level;
            new Thread(() -> {
                String[] result = fetchPathData(fLang, fLevel, fHobbies, fFirst);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (result == null) {
                        reasonView.setText(buildFallback(fLang, fLevel, fHobbies));
                        chipGroup.addView(makeChip((fLevel.startsWith("A") ? "Practical " : fLevel.startsWith("B") ? "Conversational " : "Professional ") + fLang, CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                        chipGroup.addView(makeChip("Cultural Exchange", CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                        return;
                    }
                    reasonView.setText(result[0]);
                    // result[0]=reason, result[1]=trending ("true"/"false"), result[2..]=labels
                    if ("true".equals(result[1])) {
                        TextView badge = card.findViewById(R.id.path_card_trend_badge);
                        badge.setText("🔥 Trending");
                        badge.setVisibility(View.VISIBLE);
                    }
                    for (int i = 2; i < result.length; i++) {
                        chipGroup.addView(makeChip(result[i], CHIP_BLUE_BG, CHIP_BLUE_TEXT));
                    }
                });
            }).start();
        }
    }

    private String[] fetchPathData(String lang, String level, List<String> hobbies, String firstName) {
        try {
            String hobbyList = hobbies.isEmpty() ? "general interests"
                    : android.text.TextUtils.join(", ", hobbies).replaceAll("[^a-zA-Z,\\s]", "").trim();

            String prompt =
                    "You are an expert language learning advisor with current 2025 knowledge of job markets " +
                            "and cultural trends. A user" + (firstName.isEmpty() ? "" : " named " + firstName) +
                            " is learning " + lang + " at " + level + " level with interests: " + hobbyList + ". " +
                            "Based on real 2025 demand, provide:\n" +
                            "1. A personalised 2-sentence reason mentioning their actual hobbies and current demand for " + lang + ".\n" +
                            "2. Two to three short path labels (max 4 words each) trending for " + lang + " in their fields.\n" +
                            "3. Whether " + lang + " is currently trending globally (true/false).\n" +
                            "Respond ONLY with valid JSON, no markdown:\n" +
                            "{\"reason\":\"...\",\"trending\":true,\"path1\":\"...\",\"path2\":\"...\",\"path3\":\"...\"}";

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

            if (conn.getResponseCode() != 200) return null;

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name());
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) sb.append(scanner.nextLine());
            scanner.close();

            JSONObject response = new JSONObject(sb.toString());
            String text = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    .replaceAll("```json|```", "").trim();
            JSONObject parsed = new JSONObject(text);

            List<String> out = new ArrayList<>();
            out.add(parsed.getString("reason"));
            out.add(String.valueOf(parsed.optBoolean("trending", false)));
            out.add(parsed.getString("path1"));
            out.add(parsed.getString("path2"));
            if (parsed.has("path3") && !parsed.getString("path3").isEmpty())
                out.add(parsed.getString("path3"));
            return out.toArray(new String[0]);

        } catch (Exception e) {
            Log.e(TAG, "API call failed: " + e.getMessage());
            return null;
        }
    }

    private String buildFallback(String lang, String level, List<String> hobbies) {
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
                + ". Paced for " + levelDesc + " with a focus on real-world fluency.";
    }

    private void showHobbyChips(ChipGroup group, CardView card, List<String> hobbies) {
        if (!isAdded() || hobbies == null || hobbies.isEmpty()) return;
        group.removeAllViews();
        for (String h : hobbies) {
            String label = h.contains(" ") ? h.substring(h.indexOf(" ") + 1) : h;
            group.addView(makeChip(label, CHIP_GREEN_BG, CHIP_GREEN_TEXT));
        }
        card.setVisibility(View.VISIBLE);
    }

    private Chip makeChip(String text, int bgColor, int textColor) {
        Chip chip = new Chip(requireContext());
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