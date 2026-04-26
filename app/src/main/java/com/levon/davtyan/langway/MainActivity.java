package com.levon.davtyan.langway;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;
import com.levon.davtyan.langway.ui.login.LoginFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LangwayAuth";

    private MaterialButton backBtn, nextBtn;
    private TextView headerTitle;

    private static final String[] STEP_TITLES = {
            "Who are you?", "Your languages", "Your hobbies", "Your profile"
    };
    private static final String[] STEP_ERRORS = {
            "Please fill in your name, a valid email and a password (6+ chars) before continuing.",
            "Please add at least one language before continuing.",
            "",
            ""
    };

    private final Fragment[] fragments = {
            new LoginFragment(), new LanguageFragment(), new HobbiesFragment(),
            new ProfileSetupFragment()
    };
    private int currentIndex = 0;
    private String pendingUid = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            goToHome(currentUser.getDisplayName(), null, null);
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        backBtn     = findViewById(R.id.back_button);
        nextBtn     = findViewById(R.id.next_button);
        headerTitle = findViewById(R.id.header_title);

        LinearLayout toolBar = findViewById(R.id.tool_bar);
        ViewCompat.setOnApplyWindowInsetsListener(toolBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        headerTitle.setAlpha(0f);
        headerTitle.setTranslationY(20f);
        headerTitle.animate().alpha(1f).translationY(0f)
                .setDuration(500).setStartDelay(200)
                .setInterpolator(new DecelerateInterpolator()).start();

        if (savedInstanceState == null) showFragment(0, true, true);

        backBtn.setOnClickListener(v -> {
            if (currentIndex > 0) { currentIndex--; showFragment(currentIndex, false, false); }
        });

        nextBtn.setOnClickListener(v -> {
            if (!currentStepIsValid()) {
                Toast.makeText(this, STEP_ERRORS[currentIndex], Toast.LENGTH_LONG).show();
                return;
            }
            if (currentIndex == fragments.length - 1) {
                onFinish();
            } else if (currentIndex == 0) {
                registerAtStepOne();
            } else {
                currentIndex++;
                showFragment(currentIndex, true, false);
            }
        });
    }

    private void registerAtStepOne() {
        LoginFragment loginFrag = (LoginFragment) fragments[0];
        final String email    = loginFrag.getEmail();
        final String password = loginFrag.getPassword();
        final String name     = loginFrag.getFullName();

        nextBtn.setEnabled(false);
        nextBtn.setText("Checking…");

        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    nextBtn.setEnabled(true);
                    nextBtn.setText(getString(R.string.next_button_text));

                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            showAlreadyExistsDialog();
                        } else {
                            String msg = e != null ? e.getMessage() : "Unknown error";
                            Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
                        }
                        return;
                    }

                    pendingUid = task.getResult().getUser().getUid();
                    task.getResult().getUser()
                            .updateProfile(new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name).build());

                    currentIndex++;
                    showFragment(currentIndex, true, false);
                });
    }

    private void onFinish() {
        Log.d(TAG, "onFinish called, pendingUid=" + pendingUid);
        LoginFragment        loginFrag   = (LoginFragment)        fragments[0];
        LanguageFragment     langFrag    = (LanguageFragment)     fragments[1];
        HobbiesFragment      hobbiesFrag = (HobbiesFragment)      fragments[2];
        ProfileSetupFragment setupFrag   = (ProfileSetupFragment) fragments[3];

        final String name     = loginFrag.getFullName();
        final String email    = loginFrag.getEmail();
        final String bio      = setupFrag.getBio();
        final String photoB64 = setupFrag.getPhotoBase64();
        final LinkedHashMap<String, String> langs = langFrag.getSelectedLanguages();
        final ArrayList<String> hobbies = new ArrayList<>(hobbiesFrag.getSelectedHobbies());

        final ArrayList<String> langPairs = new ArrayList<>();
        for (String lang : langs.keySet()) langPairs.add(lang + "|" + langs.get(lang));

        nextBtn.setEnabled(false);
        nextBtn.setText("Creating account…");

        final String uid = pendingUid != null ? pendingUid
                : (FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null);

        if (uid == null) {
            nextBtn.setEnabled(true);
            nextBtn.setText(getString(R.string.finish_button_text));
            Toast.makeText(this, "Session error. Please start over.", Toast.LENGTH_LONG).show();
            return;
        }

        Map<String, Object> userNode = new HashMap<>();
        userNode.put("uid",         uid);
        userNode.put("displayName", name);
        userNode.put("email",       email);
        userNode.put("hobbies",     hobbies);
        userNode.put("createdAt",   System.currentTimeMillis());
        if (bio != null && !bio.isEmpty())    userNode.put("bio",   bio);
        if (photoB64 != null)                  userNode.put("photo", photoB64);

        Map<String, Object> langsNode = new HashMap<>();
        for (Map.Entry<String, String> entry : langs.entrySet()) {
            langsNode.put(entry.getKey(), entry.getValue());
        }
        userNode.put("languages", langsNode);

        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .setValue(userNode)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Realtime DB write OK");
                    Intent intent = new Intent(this, PathActivity.class);
                    intent.putExtra(PathActivity.EXTRA_NAME, name);
                    intent.putStringArrayListExtra(PathActivity.EXTRA_LANGUAGES, langPairs);
                    intent.putStringArrayListExtra(PathActivity.EXTRA_HOBBIES, hobbies);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                })
                .addOnFailureListener(ex -> {
                    Log.e(TAG, "Realtime DB write failed: " + ex.getMessage());
                    nextBtn.setEnabled(true);
                    nextBtn.setText(getString(R.string.finish_button_text));
                    Toast.makeText(this, "Failed to save profile: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showAlreadyExistsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Account already exists")
                .setMessage("An account with this email already exists. Please sign in instead.")
                .setPositiveButton("Sign In", (dialog, which) -> {
                    Intent intent = new Intent(this, SignInActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToHome(String name, ArrayList<String> languages, ArrayList<String> hobbies) {
        Intent intent = new Intent(this, HomeActivity.class);
        if (name      != null) intent.putExtra(HomeActivity.EXTRA_NAME, name);
        if (languages != null) intent.putStringArrayListExtra(HomeActivity.EXTRA_LANGUAGES, languages);
        if (hobbies   != null) intent.putStringArrayListExtra(HomeActivity.EXTRA_HOBBIES,   hobbies);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private boolean currentStepIsValid() {
        Fragment f = fragments[currentIndex];
        if (f instanceof LoginFragment)        return ((LoginFragment)        f).isValid();
        if (f instanceof LanguageFragment)     return ((LanguageFragment)     f).isValid();
        if (f instanceof HobbiesFragment)      return ((HobbiesFragment)      f).isValid();
        if (f instanceof ProfileSetupFragment) return ((ProfileSetupFragment) f).isValid();
        return true;
    }

    private void showFragment(int index, boolean forward, boolean initial) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (!initial) ft.setCustomAnimations(
                forward ? R.anim.slide_in_right : R.anim.slide_in_left,
                forward ? R.anim.slide_out_left : R.anim.slide_out_right);
        ft.replace(R.id.frame, fragments[index]).commit();
        animateHeaderTitle(STEP_TITLES[index]);
        updateButtons(index);
    }

    private void animateHeaderTitle(String newTitle) {
        headerTitle.animate().alpha(0f).translationY(-8f).setDuration(160)
                .withEndAction(() -> {
                    headerTitle.setText(newTitle);
                    headerTitle.setTranslationY(10f);
                    headerTitle.animate().alpha(1f).translationY(0f).setDuration(220)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    private void updateButtons(int index) {
        applyButtonState(backBtn, index > 0);
        applyButtonState(nextBtn, true);
        String newLabel = (index == fragments.length - 1)
                ? getString(R.string.finish_button_text)
                : getString(R.string.next_button_text);
        if (!nextBtn.getText().toString().equals(newLabel)) {
            nextBtn.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                nextBtn.setText(newLabel);
                nextBtn.animate().alpha(1f).setDuration(160).start();
            }).start();
        }
    }

    private void applyButtonState(MaterialButton btn, boolean enabled) {
        boolean wasDisabled = !btn.isEnabled();
        btn.setEnabled(enabled);
        if (enabled) {
            if (btn.getId() == R.id.back_button) {
                btn.setTextColor(ContextCompat.getColor(this, R.color.brand_green_dark));
                btn.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_green)));
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.brand_green)));
                btn.setTextColor(Color.WHITE);
            }
            if (wasDisabled) {
                btn.setScaleX(0.90f); btn.setScaleY(0.90f);
                btn.animate().scaleX(1f).scaleY(1f).setDuration(280)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
            }
        } else {
            if (btn.getId() == R.id.back_button) {
                btn.setTextColor(Color.parseColor("#888888"));
                btn.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#C5C5C5")));
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C5C5C5")));
                btn.setTextColor(Color.parseColor("#888888"));
            }
        }
    }
}