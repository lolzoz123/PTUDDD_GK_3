package com.example.a52300240_52300246;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.util.Locale;

public class MusicWidgetProvider extends AppWidgetProvider {

    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    private static RemoteViews build(Context ctx) {
        boolean playing = PlayerState.playing;
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_music);
        v.setTextViewText(R.id.w_title, PlayerState.title(ctx));
        v.setImageViewResource(R.id.w_toggle, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        v.setContentDescription(R.id.w_toggle, playing ? "Tạm dừng" : "Phát nhạc");

        // Thanh tiến trình: max 100, giá trị do MusicService cập nhật mỗi giây khi đang phát
        v.setProgressBar(R.id.w_progress, 100, PlayerState.progress, false);

        // Thời gian đã phát và thời gian còn lại (dạng 1:05 và -0:25)
        int pos = PlayerState.positionMs;
        int dur = PlayerState.durationMs;
        v.setTextViewText(R.id.w_time_current, formatTime(pos));
        v.setTextViewText(R.id.w_time_remaining, "-" + formatTime(Math.max(dur - pos, 0)));

        // Phát / tạm dừng, bài trước, bài tiếp: gửi lệnh tới MusicService
        v.setOnClickPendingIntent(R.id.w_toggle, serviceAction(ctx, MusicService.ACTION_TOGGLE, 0));
        v.setOnClickPendingIntent(R.id.w_prev, serviceAction(ctx, MusicService.ACTION_PREV, 1));
        v.setOnClickPendingIntent(R.id.w_next, serviceAction(ctx, MusicService.ACTION_NEXT, 2));

        // Bấm vào tên bài hoặc ảnh bìa: mở màn hình musicwidget
        PendingIntent open = openMusicScreen(ctx);
        v.setOnClickPendingIntent(R.id.w_title, open);
        v.setOnClickPendingIntent(R.id.w_cover, open);
        return v;
    }

    private static String formatTime(int ms) {
        int totalSeconds = Math.max(ms, 0) / 1000;
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static PendingIntent serviceAction(Context ctx, String action, int requestCode) {
        Intent intent = new Intent(ctx, MusicService.class).setAction(action);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(ctx, requestCode, intent, FLAGS)
                : PendingIntent.getService(ctx, requestCode, intent, FLAGS);
    }

    private static PendingIntent openMusicScreen(Context ctx) {
        Intent intent = new Intent(ctx, musicwidget.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 3, intent, FLAGS);
    }

    /** Gọi từ MusicService mỗi khi trạng thái phát thay đổi. */
    public static void refresh(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, MusicWidgetProvider.class));
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }
}