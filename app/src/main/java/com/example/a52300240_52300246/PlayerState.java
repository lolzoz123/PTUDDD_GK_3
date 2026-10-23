package com.example.a52300240_52300246;

import android.content.Context;
import android.content.SharedPreferences;

/** Trạng thái dùng chung giữa Service, Widget và Live Wallpaper (cùng một process). */
public final class PlayerState {
    public static volatile boolean playing = false;

    private PlayerState() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("player", Context.MODE_PRIVATE);
    }

    public static String title(Context c) {
        String t = prefs(c).getString("title", null);
        return t != null ? t : "Chưa chọn bài hát";
    }

    public static String uri(Context c) {
        return prefs(c).getString("uri", null);
    }

    public static void save(Context c, String uri, String title) {
        prefs(c).edit().putString("uri", uri).putString("title", title).apply();
    }
}
