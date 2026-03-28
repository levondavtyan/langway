package com.levon.davtyan.langway;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.levon.davtyan.langway.databinding.FragmentLanguageBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class LanguageFragment extends Fragment {

    private FragmentLanguageBinding binding;

    // Survives view destroy/recreate (back navigation) because it's an instance field,
    // not recreated in onCreateView. This is why items reappear correctly.
    private final LinkedHashMap<String, String> selectedLanguages = new LinkedHashMap<>();

    private static final String[] LANGUAGES = {
            "English", "Spanish", "Russian", "Armenian", "German",
            "French", "Mandarin", "Japanese", "Italian", "Portuguese",
            "Arabic", "Korean", "Dutch", "Swedish", "Turkish"
    };

    private static final String[] PROFICIENCY_LEVELS = {
            "A1 – Beginner",
            "A2 – Elementary",
            "B1 – Intermediate",
            "B2 – Upper Intermediate",
            "C1 – Advanced",
            "C2 – Mastery / Native"
    };

    public LanguageFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentLanguageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Spinners
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, LANGUAGES);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.languageSpinner.setAdapter(langAdapter);

        ArrayAdapter<String> profAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, PROFICIENCY_LEVELS);
        profAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.proficiencySpinner.setAdapter(profAdapter);

        // ── Add / Update button ──────────────────────────────────────────────
        binding.addButton.setOnClickListener(v -> {
            String lang  = binding.languageSpinner.getSelectedItem().toString();
            String level = binding.proficiencySpinner.getSelectedItem().toString();

            if (selectedLanguages.containsKey(lang)) {
                String existing = selectedLanguages.get(lang);
                if (level.equals(existing)) {
                    // Same language, same proficiency — nothing to do
                    Toast.makeText(requireContext(),
                            lang + " is already added with this level.",
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Same language, different proficiency — update it
                    selectedLanguages.put(lang, level);
                    rebuildList();
                    Toast.makeText(requireContext(),
                            lang + " updated to " + level + ".",
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }

            selectedLanguages.put(lang, level);
            rebuildList();
            animateAddButton();
        });

        // Entrance animation for the picker card
        binding.constraintLayout.setAlpha(0f);
        binding.constraintLayout.setTranslationY(30f);
        binding.constraintLayout.animate()
                .alpha(1f).translationY(0f)
                .setDuration(400).setStartDelay(80)
                .setInterpolator(new DecelerateInterpolator()).start();

        // ── FIX: rebuild the list immediately so items show after back-navigation ──
        // The map is preserved (instance field), but the View was destroyed and
        // recreated, so we must re-inflate all item rows from the existing data.
        rebuildList();
    }

    /** Called by MainActivity before allowing forward navigation. */
    public boolean isValid() {
        return !selectedLanguages.isEmpty();
    }

    /** Returns the full language→proficiency map for use by PathActivity. */
    public LinkedHashMap<String, String> getSelectedLanguages() {
        return selectedLanguages;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void rebuildList() {
        if (binding == null) return;

        ViewGroup container = binding.languagesListContainer;
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int i = 0;
        for (String lang : new ArrayList<>(selectedLanguages.keySet())) {
            View item = inflater.inflate(R.layout.language_item_layout, container, false);

            ((TextView) item.findViewById(R.id.language_name)).setText(lang);
            ((TextView) item.findViewById(R.id.language_proficiency))
                    .setText(selectedLanguages.get(lang));

            MaterialButton removeBtn = item.findViewById(R.id.remove_button);
            removeBtn.setOnClickListener(v -> {
                selectedLanguages.remove(lang);
                rebuildList();
            });

            // Staggered fade-in only for newly built lists (not instant rebuild)
            item.setAlpha(0f);
            item.animate().alpha(1f)
                    .setDuration(220).setStartDelay(i * 40L)
                    .setInterpolator(new DecelerateInterpolator()).start();

            container.addView(item);
            i++;
        }

        // Show/hide "Added languages" label
        if (selectedLanguages.isEmpty()) {
            binding.addedLabel.animate().alpha(0f).setDuration(200).start();
        } else {
            binding.addedLabel.animate().alpha(1f).setDuration(300).start();
        }
    }

    private void animateAddButton() {
        binding.addButton.setScaleX(0.9f);
        binding.addButton.setScaleY(0.9f);
        binding.addButton.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(2f)).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}