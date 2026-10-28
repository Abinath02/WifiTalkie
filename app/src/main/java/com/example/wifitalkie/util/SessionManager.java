package com.example.wifitalkie.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.wifitalkie.model.User;

public class SessionManager {
    private static final String PREF_NAME = "WifiTalkieSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_ROLE = "userRole";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_AUTH_TOKEN = "authToken";

    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context context;

    public SessionManager(Context context) {
        this.context = context;
        if (context != null) {
            pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            editor = pref.edit();
        }
    }

    public void createLoginSession(User user) {
        createLoginSession(user, null);
    }

    public void createLoginSession(User user, String token) {
        if (editor == null) return;
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, user.getId());
        editor.putString(KEY_USER_ROLE, user.getRole());
        editor.putString(KEY_USER_NAME, user.getFirstName() + " " + user.getLastName());
        if (token != null) {
            editor.putString(KEY_AUTH_TOKEN, token);
        }
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref != null && pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserRole() {
        return pref != null ? pref.getString(KEY_USER_ROLE, null) : null;
    }

    public String getUserName() {
        return pref != null ? pref.getString(KEY_USER_NAME, null) : null;
    }

    public String getAuthToken() {
        return pref != null ? pref.getString(KEY_AUTH_TOKEN, "") : "";
    }

    public User getUserDetails() {
        User user = new User();
        if (pref == null) return user;
        user.setId(pref.getInt(KEY_USER_ID, -1));
        user.setRole(pref.getString(KEY_USER_ROLE, null));
        String fullName = pref.getString(KEY_USER_NAME, "");
        if (fullName != null) {
            String[] parts = fullName.split(" ");
            if (parts.length > 0)
                user.setFirstName(parts[0]);
            if (parts.length > 1)
                user.setLastName(parts[1]);
        }
        return user;
    }

    public void logoutUser() {
        if (editor != null) {
            editor.clear();
            editor.commit();
        }
    }
}
