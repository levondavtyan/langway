package com.levon.davtyan.langway.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.levon.davtyan.langway.SignInActivity;
import com.levon.davtyan.langway.databinding.FragmentLoginBinding;

public class LoginFragment extends Fragment {

    private LoginViewModel loginViewModel;
    private FragmentLoginBinding binding;

    // ── Cached values survive onDestroyView (back navigation between steps) ──
    private String  cachedFullName = "";
    private String  cachedEmail    = "";
    private String  cachedPassword = "";
    private String  cachedConfirm  = "";
    private boolean cachedFormValid = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loginViewModel = new ViewModelProvider(this, new LoginViewModelFactory())
                .get(LoginViewModel.class);

        // Restore cached values when returning from a later step
        if (!cachedFullName.isEmpty()) binding.fullName.setText(cachedFullName);
        if (!cachedEmail.isEmpty())    binding.email.setText(cachedEmail);
        if (!cachedPassword.isEmpty()) binding.password.setText(cachedPassword);
        if (!cachedConfirm.isEmpty())  binding.confirmPassword.setText(cachedConfirm);

        animateIn(binding.tilFullName,       0);
        animateIn(binding.tilEmail,          80);
        animateIn(binding.tilPassword,       160);
        animateIn(binding.tilConfirmPassword,220);
        animateIn(binding.signInLink,        290);

        // Cache every keystroke so values survive view destruction
        TextWatcher cacher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                cachedFullName = safeText(binding.fullName);
                cachedEmail    = safeText(binding.email);
                cachedPassword = safeText(binding.password);
                cachedConfirm  = safeText(binding.confirmPassword);
                loginViewModel.loginDataChanged(cachedEmail, cachedPassword);
                validateConfirm();
            }
        };
        binding.fullName.addTextChangedListener(cacher);
        binding.email.addTextChangedListener(cacher);
        binding.password.addTextChangedListener(cacher);
        binding.confirmPassword.addTextChangedListener(cacher);

        loginViewModel.getLoginFormState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            cachedFormValid = state.isDataValid();
            binding.tilEmail.setError(
                    state.getUsernameError() != null ? getString(state.getUsernameError()) : null);
            binding.tilPassword.setError(
                    state.getPasswordError() != null ? getString(state.getPasswordError()) : null);
        });

        binding.signInLink.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), SignInActivity.class));
            requireActivity().overridePendingTransition(
                    com.levon.davtyan.langway.R.anim.slide_in_right,
                    com.levon.davtyan.langway.R.anim.slide_out_left);
        });
    }

    // ── Called live as user types confirm field ───────────────────────────────
    private void validateConfirm() {
        if (binding == null) return;
        String pw  = safeText(binding.password);
        String cfm = safeText(binding.confirmPassword);
        if (!cfm.isEmpty() && !cfm.equals(pw)) {
            binding.tilConfirmPassword.setError("Passwords don't match");
        } else {
            binding.tilConfirmPassword.setError(null);
        }
    }

    // ── Accessors used by MainActivity ───────────────────────────────────────

    public boolean isValid() {
        if (!cachedFullName.isEmpty() && !cachedEmail.isEmpty()
                && !cachedPassword.isEmpty() && !cachedConfirm.isEmpty()
                && cachedPassword.equals(cachedConfirm) && cachedFormValid) {
            return true;
        }
        // Show confirm error if passwords don't match when Next is tapped
        if (binding != null && !cachedPassword.equals(cachedConfirm)) {
            binding.tilConfirmPassword.setError("Passwords don't match");
        }
        return false;
    }

    public String getFullName() { return cachedFullName; }
    public String getEmail()    { return cachedEmail; }
    public String getPassword() { return cachedPassword; }

    // ─────────────────────────────────────────────────────────────────────────

    private String safeText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void animateIn(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(24f);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(380).setStartDelay(delayMs)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        // Cached values are intentionally NOT cleared here
    }
}