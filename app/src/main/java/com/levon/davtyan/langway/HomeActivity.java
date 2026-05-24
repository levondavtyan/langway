package com.levon.davtyan.langway;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.levon.davtyan.langway.home.ChatsFragment;
import com.levon.davtyan.langway.home.DiscoverFragment;
import com.levon.davtyan.langway.home.ProfileFragment;

import android.content.Intent;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_NAME      = "extra_name";
    public static final String EXTRA_LANGUAGES = "extra_languages";
    public static final String EXTRA_HOBBIES   = "extra_hobbies";

    private LinearLayout navDiscover, navChats, navProfile;
    private TextView labelDiscover, labelChats, labelProfile;

    private DiscoverFragment discoverFrag;
    private ChatsFragment    chatsFrag;
    private ProfileFragment  profileFrag;

    private int currentTab = -1;

    private DatabaseReference callsRef;
    private ValueEventListener incomingCallListener;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        navDiscover   = findViewById(R.id.nav_discover);
        navChats      = findViewById(R.id.nav_chats);
        navProfile    = findViewById(R.id.nav_profile);
        labelDiscover = findViewById(R.id.nav_discover_label);
        labelChats    = findViewById(R.id.nav_chats_label);
        labelProfile  = findViewById(R.id.nav_profile_label);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        ArrayList<String> languages = getIntent().getStringArrayListExtra(EXTRA_LANGUAGES);
        ArrayList<String> hobbies   = getIntent().getStringArrayListExtra(EXTRA_HOBBIES);

        Bundle discoverArgs = new Bundle();
        discoverArgs.putString("name", name != null ? name : "");
        if (languages != null) discoverArgs.putStringArrayList("languages", languages);
        if (hobbies   != null) discoverArgs.putStringArrayList("hobbies",   hobbies);
        discoverFrag = new DiscoverFragment();
        discoverFrag.setArguments(discoverArgs);

        chatsFrag = new ChatsFragment();

        Bundle profileArgs = new Bundle();
        profileArgs.putString("name", name != null ? name : "");
        if (languages != null) profileArgs.putStringArrayList("languages", languages);
        if (hobbies   != null) profileArgs.putStringArrayList("hobbies",   hobbies);
        profileFrag = new ProfileFragment();
        profileFrag.setArguments(profileArgs);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.home_frame, profileFrag,  "profile")
                .add(R.id.home_frame, chatsFrag,    "chats")
                .add(R.id.home_frame, discoverFrag, "discover")
                .hide(profileFrag)
                .hide(chatsFrag)
                .commit();

        currentTab = 0;
        updateNavVisual(0);

        LinearLayout bottomNavBar = findViewById(R.id.bottom_nav_bar);
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), insets.bottom);
            return windowInsets;
        });

        navDiscover.setOnClickListener(v -> showTab(0));
        navChats   .setOnClickListener(v -> showTab(1));
        navProfile .setOnClickListener(v -> showTab(2));

        // Start listening for incoming calls
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            listenForIncomingCalls();
        }
    }

    private void listenForIncomingCalls() {
        callsRef = FirebaseDatabase.getInstance().getReference("calls");
        incomingCallListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot callSnap : snapshot.getChildren()) {
                    String calleeUid = callSnap.child("calleeUid").getValue(String.class);
                    String state     = callSnap.child("state").getValue(String.class);

                    // Only handle calls addressed to me that are still ringing
                    if (!myUid.equals(calleeUid)) continue;
                    if (!"ringing".equals(state))  continue;

                    String callId     = callSnap.getKey();
                    String callerName = callSnap.child("callerName").getValue(String.class);
                    String callerPhoto= callSnap.child("callerPhoto").getValue(String.class);
                    String chatId     = callSnap.child("chatId").getValue(String.class);
                    Boolean isVideo   = callSnap.child("isVideo").getValue(Boolean.class);

                    // Show incoming call screen
                    Intent intent = new Intent(HomeActivity.this, CallActivity.class);
                    intent.putExtra(CallActivity.EXTRA_MODE,         CallActivity.MODE_INCOMING);
                    intent.putExtra(CallActivity.EXTRA_CALL_ID,      callId);
                    intent.putExtra(CallActivity.EXTRA_CHAT_ID,      chatId);
                    intent.putExtra(CallActivity.EXTRA_CALLER_NAME,  callerName);
                    intent.putExtra(CallActivity.EXTRA_CALLER_PHOTO, callerPhoto != null ? callerPhoto : "");
                    intent.putExtra(CallActivity.EXTRA_IS_VIDEO,     isVideo != null && isVideo);
                    intent.putExtra(CallActivity.EXTRA_MY_NAME,      myUid); // resolved in CallActivity
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    break; // handle one call at a time
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        callsRef.addValueEventListener(incomingCallListener);
    }

    private void showTab(int tab) {
        if (tab == currentTab) return;
        currentTab = tab;

        Fragment toShow;
        switch (tab) {
            case 1:  toShow = chatsFrag;    break;
            case 2:  toShow = profileFrag;  break;
            default: toShow = discoverFrag; break;
        }

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(discoverFrag)
                .hide(chatsFrag)
                .hide(profileFrag)
                .show(toShow)
                .commit();

        updateNavVisual(tab);
    }

    private void updateNavVisual(int active) {
        LinearLayout[] navItems = {navDiscover, navChats, navProfile};
        TextView[]     labels   = {labelDiscover, labelChats, labelProfile};
        int activeColor   = getColor(R.color.brand_green_dark);
        int inactiveColor = getColor(R.color.text_hint);
        for (int i = 0; i < navItems.length; i++) {
            boolean isActive = (i == active);
            navItems[i].setBackground(isActive ? getDrawable(R.drawable.bg_nav_selected) : null);
            labels[i].setTextColor(isActive ? activeColor : inactiveColor);
            labels[i].setTypeface(null, isActive
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callsRef != null && incomingCallListener != null) {
            callsRef.removeEventListener(incomingCallListener);
        }
    }
}