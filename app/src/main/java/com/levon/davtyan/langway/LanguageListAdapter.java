package com.levon.davtyan.langway;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Custom adapter for the language list in LanguageFragment.
 * Displays language name + proficiency, and a styled Remove button.
 */
public class LanguageListAdapter extends ArrayAdapter<String> {

    private final LinkedHashMap<String, String> proficiencyMap;
    private final RemoveCallback onRemove;

    public interface RemoveCallback {
        void remove(String language);
    }

    public LanguageListAdapter(@NonNull Context context,
                               @NonNull List<String> languages,
                               LinkedHashMap<String, String> proficiencyMap,
                               RemoveCallback onRemove) {
        super(context, 0, languages);
        this.proficiencyMap = proficiencyMap;
        this.onRemove = onRemove;
    }

    public void update(List<String> newList) {
        clear();
        addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.language_item_layout, parent, false);
            holder = new ViewHolder();
            holder.name        = convertView.findViewById(R.id.language_name);
            holder.proficiency = convertView.findViewById(R.id.language_proficiency);
            holder.removeBtn   = convertView.findViewById(R.id.remove_button);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String lang  = getItem(position);
        String level = lang != null ? proficiencyMap.get(lang) : "";

        holder.name.setText(lang);
        holder.proficiency.setText(level);
        holder.removeBtn.setOnClickListener(v -> {
            if (lang != null) onRemove.remove(lang);
        });

        // Staggered fade-in for newly added items
        convertView.setAlpha(0f);
        convertView.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(position * 40L)
                .start();

        return convertView;
    }

    private static class ViewHolder {
        TextView name, proficiency;
        MaterialButton removeBtn;
    }
}