package com.levon.davtyan.langway;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.levon.davtyan.langway.databinding.FragmentHobbiesBinding;

import java.util.LinkedHashSet;
import java.util.Set;

public class HobbiesFragment extends Fragment {

    private FragmentHobbiesBinding binding;

    private final Set<String> selectedHobbies = new LinkedHashSet<>();

    private static final String[] HOBBIES = {
            "🎵 Music",       "🎮 Gaming",      "📚 Reading",
            "✈️ Travel",      "🍳 Cooking",     "🎨 Art",
            "🏃 Sports",      "🎬 Movies",      "📷 Photography",
            "🌿 Nature",      "💻 Technology",  "🧘 Wellness",
            "🎭 Theatre",     "🎸 Instruments", "🏋️ Fitness",
            "🧩 Puzzles",     "🐾 Animals",     "📝 Writing",
            "🌍 Languages",   "🎲 Board Games"
    };

    public HobbiesFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHobbiesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        buildChips();

        binding.hobbiesSubtitle.setAlpha(0f);
        binding.hobbiesSubtitle.setTranslationY(16f);
        binding.hobbiesSubtitle.animate()
                .alpha(1f).translationY(0f)
                .setDuration(360).setStartDelay(60)
                .setInterpolator(new DecelerateInterpolator()).start();

        binding.hobbiesCard.setAlpha(0f);
        binding.hobbiesCard.setTranslationY(30f);
        binding.hobbiesCard.animate()
                .alpha(1f).translationY(0f)
                .setDuration(400).setStartDelay(120)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    public boolean isValid() {
        return true;
    }

    public Set<String> getSelectedHobbies() {
        return selectedHobbies;
    }


    private void buildChips() {
        binding.chipGroup.removeAllViews();

        for (int i = 0; i < HOBBIES.length; i++) {
            String hobby = HOBBIES[i];
            Chip chip = new Chip(requireContext());
            chip.setText(hobby);
            chip.setCheckable(true);
            chip.setChecked(selectedHobbies.contains(hobby));

            chip.setChipBackgroundColorResource(
                    chip.isChecked() ? R.color.brand_green_light : android.R.color.transparent);
            chip.setChipStrokeColorResource(R.color.outline);
            chip.setChipStrokeWidth(1.5f);
            chip.setTextColor(requireContext().getColor(R.color.text_primary));
            chip.setCheckedIconVisible(true);
            chip.setCheckedIconTintResource(R.color.brand_green_dark);
            chip.setRippleColorResource(R.color.brand_green_light);

            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    selectedHobbies.add(hobby);
                    btn.setBackgroundResource(R.color.brand_green_light);
                    btn.setScaleX(0.88f);
                    btn.setScaleY(0.88f);
                    btn.animate().scaleX(1f).scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start();
                } else {
                    selectedHobbies.remove(hobby);
                    btn.setBackgroundResource(android.R.color.transparent);
                }
                updateSelectionLabel();
            });

            chip.setAlpha(0f);
            chip.animate().alpha(1f)
                    .setDuration(200).setStartDelay(140 + i * 30L)
                    .setInterpolator(new DecelerateInterpolator()).start();

            binding.chipGroup.addView(chip);
        }

        updateSelectionLabel();
    }

    private void updateSelectionLabel() {
        if (binding == null) return;
        int count = selectedHobbies.size();
        String text = count == 0
                ? "Nothing selected yet"
                : count == 1 ? "1 hobby selected" : count + " hobbies selected";
        binding.selectionLabel.setText(text);
        binding.selectionLabel.animate().alpha(1f).setDuration(250).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}