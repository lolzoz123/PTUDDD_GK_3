package com.example.a52300240_52300246;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collections;
import java.util.List;

/** Trạng thái dùng chung giữa Service, Widget và Live Wallpaper (cùng một process). */
public final class PlayerState {
    public static volatile boolean playing = false;

    /** true khi đang dùng nguồn nhạc trực tuyến (chỉ lưu trong bộ nhớ, không lưu lâu dài). */
    public static volatile boolean online = false;
    public static volatile String onlineTitle = null;

    /** Tiến trình bài đang phát, 0–100 (widget dùng để vẽ thanh tiến trình). */
    public static volatile int progress = 0;

    /** Vị trí hiện tại và độ dài bài hát (mili giây), widget dùng để hiện thời gian. */
    public static volatile int positionMs = 0;
    public static volatile int durationMs = 0;

    private PlayerState() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("player", Context.MODE_PRIVATE);
    }

    private static JSONArray array(Context c, String key) {
        try {
            return new JSONArray(prefs(c).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    // ---------- Danh sách phát file trên máy ----------
    public static void saveQueue(Context c, List<String> uris, List<String> titles, int index) {
        prefs(c).edit()
                .putString("q_uris", new JSONArray(uris).toString())
                .putString("q_titles", new JSONArray(titles).toString())
                .putInt("q_index", index)
                .apply();
    }

    /** Lưu một bài duy nhất (danh sách có 1 phần tử). */
    public static void save(Context c, String uri, String title) {
        saveQueue(c, Collections.singletonList(uri), Collections.singletonList(title), 0);
    }

    public static int queueSize(Context c) {
        return array(c, "q_uris").length();
    }

    public static int queueIndex(Context c) {
        int i = prefs(c).getInt("q_index", 0);
        return (i >= 0 && i < queueSize(c)) ? i : 0;
    }

    public static void setQueueIndex(Context c, int index) {
        prefs(c).edit().putInt("q_index", index).apply();
    }

    /** Uri của bài hiện tại trong danh sách file trên máy, hoặc null nếu chưa chọn. */
    public static String uri(Context c) {
        JSONArray a = array(c, "q_uris");
        return a.length() == 0 ? null : a.optString(queueIndex(c), null);
    }

    // ---------- Tên bài đang hiển thị ----------
    public static String title(Context c) {
        String o = onlineTitle;
        if (online && o != null) return o;
        JSONArray a = array(c, "q_titles");
        if (a.length() == 0) return "Chưa chọn bài hát";
        return a.optString(queueIndex(c), "Chưa chọn bài hát");
    }

    public static String sourceLabel() {
        return online ? "Nguồn: Trực tuyến" : "Nguồn: File trên máy";
    }
}