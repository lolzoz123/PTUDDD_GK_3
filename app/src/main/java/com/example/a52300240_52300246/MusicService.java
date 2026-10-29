package com.example.a52300240_52300246;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicService extends Service {
    public static final String ACTION_TOGGLE = "com.example.musicwidget.TOGGLE";
    public static final String ACTION_RELOAD = "com.example.musicwidget.RELOAD";
    public static final String ACTION_STOP = "com.example.musicwidget.STOP";
    /** Phát một bài trực tuyến: tải về bộ nhớ tạm, phát, rồi xóa. */
    public static final String ACTION_PLAY_ONLINE = "com.example.musicwidget.PLAY_ONLINE";
    public static final String ACTION_NEXT = "com.example.musicwidget.NEXT";
    public static final String ACTION_PREV = "com.example.musicwidget.PREV";
    /** Danh sách bài trực tuyến: mảng url, mảng tên và vị trí bài cần phát. */
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_INDEX = "index";

    /** Nút "bài trước": quá mốc này thì tua về 0:00, chưa quá thì về bài trước. */
    private static final int PREV_RESTART_MS = 5000;

    private static final long MAX_BYTES = 30L * 1024 * 1024; // chặn file tạm quá lớn

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private File tempFile;              // file tạm của bài trực tuyến đang phát
    private volatile int loadToken = 0; // tăng mỗi khi đổi bài/hủy để bỏ kết quả cũ

    // Danh sách phát trực tuyến (chỉ giữ trong bộ nhớ)
    private final List<String> onlineUrls = new ArrayList<>();
    private final List<String> onlineTitles = new ArrayList<>();
    private int onlineIndex = 0;

    // Cập nhật thanh tiến trình của widget mỗi giây khi đang phát
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (PlayerState.playing) main.postDelayed(this, 1000);
        }
    };

    private void updateProgress() {
        int pos = 0, dur = 0, pct = 0;
        try {
            if (player != null) {
                dur = Math.max(player.getDuration(), 0); // -1 nếu chưa biết độ dài
                pos = player.getCurrentPosition();
                if (dur > 0) pct = (int) (pos * 100L / dur);
            }
        } catch (IllegalStateException ignored) { }
        PlayerState.progress = pct;
        PlayerState.positionMs = pos;
        PlayerState.durationMs = dur;
        MusicWidgetProvider.refresh(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        clearStreamDir(); // dọn file tạm còn sót từ lần chạy trước
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service được bật bằng startForegroundService nên phải gọi startForeground ngay.
        ServiceCompat.startForeground(this, 1, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) {
            if (PlayerState.online && player == null) return START_NOT_STICKY; // đang tải
            if (PlayerState.playing) pause(); else play();
        } else if (ACTION_RELOAD.equals(action)) {
            switchToLocal();
            play();
        } else if (ACTION_PLAY_ONLINE.equals(action)) {
            String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
            String[] titles = intent.getStringArrayExtra(EXTRA_TITLES);
            int index = intent.getIntExtra(EXTRA_INDEX, 0);
            if (urls != null && titles != null && urls.length == titles.length
                    && index >= 0 && index < urls.length) {
                onlineUrls.clear();
                onlineTitles.clear();
                Collections.addAll(onlineUrls, urls);
                Collections.addAll(onlineTitles, titles);
                playOnlineAt(index);
            } else {
                stopPlayback();
            }
        } else if (ACTION_NEXT.equals(action)) {
            step(1);
        } else if (ACTION_PREV.equals(action)) {
            previous();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_NOT_STICKY;
    }

    // ---------- Nguồn file trên máy ----------
    private void play() {
        try {
            if (player == null) {
                String uri = PlayerState.uri(this);
                if (uri == null) throw new IllegalStateException("Hãy mở app và chọn bài hát trước");
                player = new MediaPlayer();
                player.setAudioAttributes(audioAttributes());
                player.setDataSource(this, Uri.parse(uri));
                if (PlayerState.queueSize(this) > 1) {
                    // Nhiều bài: hết bài thì sang bài kế tiếp
                    player.setLooping(false);
                    player.setOnCompletionListener(m -> main.post(() -> step(1)));
                } else {
                    player.setLooping(true);
                }
                player.prepare();
            }
            player.start();
            setPlaying(true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Không phát được bài hát";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            stopPlayback();
        }
    }

    private void switchToLocal() {
        loadToken++; // hủy mọi lần tải trực tuyến đang chạy
        PlayerState.online = false;
        PlayerState.onlineTitle = null;
        releasePlayer();
    }

    // ---------- Chuyển bài ----------
    /** Nút "bài trước": đã phát quá 5 giây thì về 0:00, chưa quá thì về bài trước. */
    private void previous() {
        if (player != null) {
            int pos = 0;
            try {
                pos = player.getCurrentPosition();
            } catch (IllegalStateException ignored) { }
            if (pos > PREV_RESTART_MS) {
                player.seekTo(0);
                updateProgress();
                return;
            }
        }
        step(-1);
    }

    /** Sang bài kế (delta = 1) hoặc bài trước (delta = -1), quay vòng ở hai đầu danh sách. */
    private void step(int delta) {
        if (PlayerState.online) {
            int n = onlineUrls.size();
            if (n == 0) {
                stopPlayback();
                return;
            }
            playOnlineAt((onlineIndex + delta + n) % n);
        } else {
            int n = PlayerState.queueSize(this);
            if (n == 0) {
                Toast.makeText(this, "Hãy mở app và chọn bài hát trước", Toast.LENGTH_LONG).show();
                stopPlayback();
                return;
            }
            PlayerState.setQueueIndex(this, (PlayerState.queueIndex(this) + delta + n) % n);
            loadToken++;
            releasePlayer();
            play();
        }
    }

    // ---------- Nguồn trực tuyến ----------
    private void playOnlineAt(int index) {
        onlineIndex = index;
        final String url = onlineUrls.get(index);
        final String title = onlineTitles.get(index);
        final int token = ++loadToken;
        releasePlayer(); // giải phóng bài cũ và xóa file tạm cũ
        PlayerState.online = true;
        PlayerState.onlineTitle = title;
        setPlaying(false); // cập nhật thông báo "Đang tải…" và widget

        io.execute(() -> {
            File f = new File(streamDir(), "track_" + token + ".m4a");
            try {
                download(url, f, token);
                main.post(() -> startOnline(f, token));
            } catch (Exception e) {
                f.delete();
                if (token != loadToken) return; // đã hủy hoặc đổi sang bài khác
                String msg = e.getMessage() != null ? e.getMessage() : "lỗi mạng";
                main.post(() -> {
                    if (token != loadToken) return;
                    Toast.makeText(this, "Không tải được bài hát: " + msg, Toast.LENGTH_LONG).show();
                    stopPlayback();
                });
            }
        });
    }

    private void startOnline(File f, int token) {
        if (token != loadToken) { // người dùng đã đổi bài trong lúc tải
            f.delete();
            return;
        }
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(audioAttributes());
            mp.setDataSource(f.getAbsolutePath());
            mp.setLooping(false);
            mp.setOnCompletionListener(m -> stopPlayback()); // phát xong thì giải phóng
            mp.prepare();
            mp.start();
            player = mp;
            tempFile = f;
            setPlaying(true);
        } catch (Exception e) {
            if (mp != null) mp.release();
            f.delete();
            Toast.makeText(this, "Không phát được bài hát", Toast.LENGTH_LONG).show();
            stopPlayback();
        }
    }

    private void download(String link, File out, int token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16 * 1024];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (token != loadToken) throw new IOException("Đã hủy");
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("File quá lớn");
                    os.write(buf, 0, n);
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private File streamDir() {
        File dir = new File(getCacheDir(), "stream");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private void clearStreamDir() {
        File[] files = streamDir().listFiles();
        if (files == null) return;
        for (File f : files) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    // ---------- Dùng chung ----------
    private AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    private void pause() {
        if (player != null) player.pause();
        setPlaying(false);
    }

    /** Dừng hẳn: hủy tải, giải phóng MediaPlayer, xóa file tạm, đóng service. */
    private void stopPlayback() {
        loadToken++;
        releasePlayer();
        PlayerState.online = false;
        PlayerState.onlineTitle = null;
        setPlaying(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releasePlayer() {
        PlayerState.progress = 0;
        PlayerState.positionMs = 0;
        PlayerState.durationMs = 0;
        if (player != null) {
            player.release();
            player = null;
        }
        if (tempFile != null) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            tempFile = null;
        }
    }

    @SuppressLint("NotificationPermission") // đã kiểm tra bằng canNotify()
    private void setPlaying(boolean p) {
        PlayerState.playing = p;
        main.removeCallbacks(ticker);
        if (p) main.post(ticker);
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

    private String status() {
        if (PlayerState.playing) return "Đang phát";
        if (PlayerState.online && player == null) return "Đang tải…";
        return "Tạm dừng";
    }

    private Notification notification() {
        ensureChannel();
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "music")
                .setSmallIcon(PlayerState.playing ? R.drawable.ic_pause : R.drawable.ic_play)
                .setContentTitle(PlayerState.title(this))
                .setContentText(status())
                .setOngoing(true)
                .addAction(0, "Đóng", stop)
                .build();
    }

    @Override
    public void onDestroy() {
        loadToken++;
        io.shutdownNow();
        main.removeCallbacks(ticker);
        releasePlayer(); // xóa file tạm nếu còn
        PlayerState.playing = false;
        PlayerState.online = false;
        PlayerState.onlineTitle = null;
        MusicWidgetProvider.refresh(this);
        super.onDestroy();
    }
}