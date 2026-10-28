package com.example.wifitalkie.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Constants {
    public static final String PREFS_NAME = "WifiTalkiePrefs";
    public static final String KEY_SERVER_IP = "server_ip";
    public static final String DEFAULT_SERVER_IP = "192.168.0.115";

    // Legacy fallback (can be mutated dynamically)
    public static String BASE_URL = "http://" + DEFAULT_SERVER_IP + "/WifiTalkie/backend/";

    public static String getServerIp(Context context) {
        if (context == null) return DEFAULT_SERVER_IP;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP);
    }

    public static void setServerIp(Context context, String ip) {
        if (context == null || ip == null) return;
        String cleanIp = ip.trim().replaceAll("^http://", "").replaceAll("/.*$", "");
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SERVER_IP, cleanIp).apply();
        BASE_URL = "http://" + cleanIp + "/WifiTalkie/backend/";
    }

    public static String getBaseUrl(Context context) {
        String ip = getServerIp(context);
        return "http://" + ip + "/WifiTalkie/backend/";
    }
}
