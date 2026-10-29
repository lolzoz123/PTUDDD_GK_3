package com.example.a52300240_52300246;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class musicwidget extends AppCompatActivity {
    private TextView label;
    private TextView sourceLabel;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    // Cho phép chọn nhiều file để tạo danh sách phát (nút bài trước / bài tiếp trên widget)
    private final ActivityResultLauncher<String[]> pickAudio =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;

                List<String> uriList = new ArrayList<>();
                List<String> names = new ArrayList<>();
                for (Uri uri : uris) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) { }
                    uriList.add(uri.toString());
                    names.add(displayName(uri));
                }
                PlayerState.saveQueue(this, uriList, names, 0);
                // ACTION_RELOAD chuyển về nguồn file trên máy và phát bài đầu tiên
                send(MusicService.ACTION_RELOAD);
                label.setText(names.get(0));
                sourceLabel.setText("Nguồn: File trên máy");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        int pad = (int) (24 * getResources().getDisplayMetrics().density);

        label = new TextView(this);
        label.setTextSize(20f);
        label.setPadding(0, 0, 0, pad / 4);

        sourceLabel = new TextView(this);
        sourceLabel.setTextSize(14f);
        sourceLabel.setPadding(0, 0, 0, pad);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 3, pad, pad);
        root.addView(label);
        root.addView(sourceLabel);
        root.addView(button("Chọn bài hát (có thể chọn nhiều file)",
                () -> pickAudio.launch(new String[]{"audio/*"})));
        root.addView(button("Phát nhạc đã chọn trên máy", () -> send(MusicService.ACTION_RELOAD)));
        root.addView(button("Tìm nhạc trực tuyến",
                () -> startActivity(new Intent(this, OnlineMusicActivity.class))));
        root.addView(button("Phát / Tạm dừng", () -> send(MusicService.ACTION_TOGGLE)));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cập nhật lại khi quay về từ màn hình tìm nhạc hoặc khi mở từ widget
        label.setText(PlayerState.title(this));
        sourceLabel.setText(PlayerState.sourceLabel());
    }

    private String displayName(Uri uri) {
        String name = "Bản nhạc";
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) {
                String n = c.getString(0);
                int dot = n.lastIndexOf('.');
                name = dot > 0 ? n.substring(0, dot) : n;
            }
        }
        return name;
    }

    private Button button(String text, Runnable onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void send(String action) {
        ContextCompat.startForegroundService(this,
                new Intent(this, MusicService.class).setAction(action));
    }


}