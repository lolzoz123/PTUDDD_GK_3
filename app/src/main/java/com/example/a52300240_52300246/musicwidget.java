package com.example.a52300240_52300246;

import android.Manifest;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
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

public class musicwidget extends AppCompatActivity {
    private TextView label;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    private final ActivityResultLauncher<String[]> pickAudio =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) { }

                String name = "Bản nhạc";
                try (Cursor c = getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst() && c.getString(0) != null) {
                        String n = c.getString(0);
                        int dot = n.lastIndexOf('.');
                        name = dot > 0 ? n.substring(0, dot) : n;
                    }
                }
                PlayerState.save(this, uri.toString(), name);
                label.setText(name);
                send(MusicService.ACTION_RELOAD); // phát luôn bài vừa chọn
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
        label.setText(PlayerState.title(this));
        label.setPadding(0, 0, 0, pad);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 3, pad, pad);
        root.addView(label);
        root.addView(button("Chọn bài hát", () -> pickAudio.launch(new String[]{"audio/*"})));
        root.addView(button("Phát / Tạm dừng", () -> send(MusicService.ACTION_TOGGLE)));
        root.addView(button("Đặt làm hình nền động", this::setWallpaper));
        setContentView(root);
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

    private void setWallpaper() {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, LiveWallpaperService.class));
        startActivity(i);
    }
}
