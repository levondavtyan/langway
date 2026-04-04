package com.levon.davtyan.langway;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileSetupFragment extends Fragment {

    private ImageView avatarImage;
    private TextView  avatarInitials;
    private TextInputEditText bioField;

    // Persisted across view destroy/recreate
    private String cachedBio       = "";
    private String cachedPhotoB64  = null; // Base64-encoded JPEG, or null if not chosen
    private Bitmap cachedBitmap    = null;

    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register gallery picker — must be done in onCreate, before onStart
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK
                            || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    try {
                        InputStream is = requireContext().getContentResolver().openInputStream(uri);
                        Bitmap raw = BitmapFactory.decodeStream(is);
                        // Scale down to max 512px on the longest side to keep Firestore doc small
                        cachedBitmap = scaleBitmap(raw, 512);
                        // Encode to Base64 JPEG
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        cachedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                        cachedPhotoB64 = Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT);
                        // Show in UI
                        applyAvatarImage();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_setup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        avatarImage    = view.findViewById(R.id.setup_avatar_image);
        avatarInitials = view.findViewById(R.id.setup_avatar_initials);
        bioField       = view.findViewById(R.id.setup_bio);

        // Restore cached state
        if (!cachedBio.isEmpty()) bioField.setText(cachedBio);
        if (cachedBitmap != null) applyAvatarImage();

        // Cache bio on every keystroke
        bioField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                cachedBio = s.toString().trim();
            }
        });

        // Tap avatar circle → open gallery
        View avatarContainer = view.findViewById(R.id.setup_avatar_container);
        avatarContainer.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pick.setType("image/*");
            galleryLauncher.launch(pick);
        });

        // Entrance animations
        animateIn(view.findViewById(R.id.setup_subtitle), 0);
        animateIn(avatarContainer, 80);
        animateIn(view.findViewById(R.id.setup_bio_card), 180);
    }

    // ── Accessors for MainActivity ────────────────────────────────────────────

    /** Bio is optional — always valid. */
    public boolean isValid() { return true; }

    public String getBio() { return cachedBio; }

    /** Returns Base64-encoded JPEG string, or null if no photo was chosen. */
    public String getPhotoBase64() { return cachedPhotoB64; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyAvatarImage() {
        if (avatarImage == null) return;
        if (cachedBitmap != null) {
            avatarImage.setImageBitmap(cachedBitmap);
            avatarImage.setVisibility(View.VISIBLE);
            avatarInitials.setVisibility(View.GONE);
        }
    }

    private Bitmap scaleBitmap(Bitmap src, int maxPx) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxPx && h <= maxPx) return src;
        float scale = maxPx / (float) Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w * scale), (int)(h * scale), true);
    }

    private void animateIn(View v, long delay) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(20f);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(360).setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cache bio before view is destroyed
        if (bioField != null)
            cachedBio = bioField.getText() != null ? bioField.getText().toString().trim() : "";
        avatarImage    = null;
        avatarInitials = null;
        bioField       = null;
    }
}