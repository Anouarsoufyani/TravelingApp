package com.example.travelshare.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;

public class SessionManager {
    private static final String PREF_NAME = "TravelShareSession";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_USER_ID  = "userId";
    private static final String KEY_ANONYMOUS = "anonymous";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;
    private final FirebaseAuth mAuth;

    public SessionManager(Context context) {
        pref   = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
        mAuth  = FirebaseAuth.getInstance();
    }

    public void createLoginSession(int userId, String username) {
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putBoolean(KEY_ANONYMOUS, false);
        editor.apply();
    }

    public void createAnonymousSession() {
        editor.putInt(KEY_USER_ID, -1);
        editor.putString(KEY_USERNAME, "Anonyme");
        editor.putBoolean(KEY_ANONYMOUS, true);
        editor.apply();
    }

    public void logoutUser() {
        mAuth.signOut();
        editor.clear();
        editor.apply();
    }

    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    public boolean hasActiveSession() {
        return isLoggedIn() || pref.getBoolean(KEY_ANONYMOUS, false);
    }

    public boolean isAnonymous() {
        return pref.getBoolean(KEY_ANONYMOUS, false);
    }

    public String getUsername() {
        String username = pref.getString(KEY_USERNAME, "Anonyme");
        return username != null ? username : "Anonyme";
    }

    public int getUserId() {
        return pref.getInt(KEY_USER_ID, -1);
    }

}
