package com.example.a52300240_52300246;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.widget.Toast;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class MusicService extends Service {
    public static final String ACTION_TOGGLE = "com.example.musicwidget.TOGGLE";
    public static final String ACTION_RELOAD = "com.example.musicwidget.RELOAD";
    public static final String ACTION_STOP = "com.example.musicwidget.STOP";

    private MediaPlayer player;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service được bật bằng startForegroundService nên phải gọi startForeground ngay.
        ServiceCompat.startForeground(this, 1, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) {
            if (PlayerState.playing) pause(); else play();
        } else if (ACTION_RELOAD.equals(action)) {
            releasePlayer();
            play();
        } else if (ACTION_STOP.equals(action)) {
            setPlaying(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void play() {
        try {
            if (player == null) {
                String uri = PlayerState.uri(this);
                if (uri == null) throw new IllegalStateException("Hãy mở app và chọn bài hát trước");
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                player.setDataSource(this, Uri.parse(uri));
                player.setLooping(true);
                player.prepare();
            }
            player.start();
            setPlaying(true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Không phát được bài hát";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            releasePlayer();
            setPlaying(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void pause() {
        if (player != null) player.pause();
        setPlaying(false);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @SuppressLint("NotificationPermission") // đã kiểm tra bằng canNotify()
    private void setPlaying(boolean p) {
        PlayerState.playing = p;
        if (canNotify()) {
            getSystemService(NotificationManager.class).notify(1, notification());
        }
        MusicWidgetProvider.refresh(this);
    }

    private boolean canNotify() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("music", "Phát nhạc", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification() {
        ensureChannel();
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "music")
                .setSmallIcon(PlayerState.playing ? R.drawable.ic_pause : R.drawable.ic_play)
                .setContentTitle(PlayerState.title(this))
                .setContentText(PlayerState.playing ? "Đang phát" : "Tạm dừng")
                .setOngoing(true)
                .addAction(0, "Đóng", stop)
                .build();
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        PlayerState.playing = false;
        MusicWidgetProvider.refresh(this);
        super.onDestroy();
    }
}
