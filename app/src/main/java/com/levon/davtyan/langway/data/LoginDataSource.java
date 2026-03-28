package com.levon.davtyan.langway.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.levon.davtyan.langway.data.model.LoggedInUser;

public class LoginDataSource {

    private static final String TAG = "LangwayAuth";

    public interface AuthCallback {
        void onSuccess(LoggedInUser user);
        void onError(String errorMessage);
    }

    // ── Sign-in ───────────────────────────────────────────────────────────────
    public void signIn(String email, String password, AuthCallback callback) {
        Log.d(TAG, "signIn() called for: " + email);
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser fbUser = result.getUser();
                    if (fbUser == null) { callback.onError("Sign-in failed."); return; }
                    String name = fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty()
                            ? fbUser.getDisplayName() : email.split("@")[0];
                    Log.d(TAG, "Sign-in succeeded, displayName: " + name);
                    callback.onSuccess(new LoggedInUser(fbUser.getUid(), name));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "signIn failed: " + e.getMessage());
                    callback.onError(friendlyError(e.getMessage()));
                });
    }

    // ── Sign-out ──────────────────────────────────────────────────────────────
    public void logout() {
        FirebaseAuth.getInstance().signOut();
    }

    // Kept so LoginRepository still compiles — not used in live flows
    public Result<LoggedInUser> login(String username, String password) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            String name = u.getDisplayName() != null ? u.getDisplayName() : username;
            return new Result.Success<>(new LoggedInUser(u.getUid(), name));
        }
        return new Result.Error(new Exception("Not signed in"));
    }

    private String friendlyError(String raw) {
        if (raw == null) return "Unknown error";
        if (raw.contains("email address is already in use"))
            return "That email is already registered. Try signing in instead.";
        if (raw.contains("badly formatted"))  return "Invalid email address format.";
        if (raw.contains("weak-password") || raw.contains("at least 6"))
            return "Password must be at least 6 characters.";
        if (raw.contains("network") || raw.contains("NETWORK"))
            return "Network error — check your connection.";
        if (raw.contains("no user record") || raw.contains("wrong-password"))
            return "Incorrect email or password.";
        return raw;
    }
}