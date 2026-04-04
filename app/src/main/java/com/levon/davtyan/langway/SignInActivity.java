package com.levon.davtyan.langway;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.levon.davtyan.langway.data.LoginDataSource;

public class SignInActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton signInBtn, backBtn;
    private TextView forgotPasswordLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);

        tilEmail          = findViewById(R.id.signin_til_email);
        tilPassword       = findViewById(R.id.signin_til_password);
        etEmail           = findViewById(R.id.signin_email);
        etPassword        = findViewById(R.id.signin_password);
        signInBtn         = findViewById(R.id.signin_btn);
        backBtn           = findViewById(R.id.signin_back_btn);
        forgotPasswordLink = findViewById(R.id.signin_forgot_password);

        LinearLayout bottomBar = findViewById(R.id.signin_bottom_bar);
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        animateIn(findViewById(R.id.signin_header_title), 0);
        animateIn(tilEmail, 100);
        animateIn(tilPassword, 180);
        animateIn(forgotPasswordLink, 240);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) { updateButtonState(); }
        };
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);

        signInBtn.setOnClickListener(v -> {
            String email    = text(etEmail);
            String password = text(etPassword);

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter a valid email address");
                return;
            }
            if (password.length() < 6) {
                tilPassword.setError("Password must be at least 6 characters");
                return;
            }
            tilEmail.setError(null);
            tilPassword.setError(null);

            signInBtn.setEnabled(false);
            signInBtn.setText("Signing in…");

            new LoginDataSource().signIn(email, password, new LoginDataSource.AuthCallback() {
                @Override
                public void onSuccess(com.levon.davtyan.langway.data.model.LoggedInUser user) {
                    Intent intent = new Intent(SignInActivity.this, HomeActivity.class);
                    intent.putExtra(HomeActivity.EXTRA_NAME, user.getDisplayName());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        signInBtn.setEnabled(true);
                        signInBtn.setText("Sign In");
                        updateButtonState();
                        Toast.makeText(SignInActivity.this,
                                "Sign-in failed: " + errorMessage, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        forgotPasswordLink.setOnClickListener(v -> {
            String email = text(etEmail);
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter your email address first");
                return;
            }
            tilEmail.setError(null);
            FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this,
                                    "Password reset email sent to " + email, Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed to send reset email: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        backBtn.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
    }

    private void updateButtonState() {
        boolean ok = Patterns.EMAIL_ADDRESS.matcher(text(etEmail)).matches()
                && text(etPassword).length() >= 6;
        signInBtn.setEnabled(ok);
        signInBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ok ? getColor(R.color.brand_green) : Color.parseColor("#C5C5C5")));
        signInBtn.setTextColor(ok ? Color.WHITE : Color.parseColor("#888888"));
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void animateIn(android.view.View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(20f);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(360).setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}