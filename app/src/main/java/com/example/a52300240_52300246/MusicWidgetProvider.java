package com.example.a52300240_52300246;

import android.os.Build;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class MusicWidgetProvider extends AppWidgetProvider {

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

        Intent intent = new Intent(ctx, MusicService.class).setAction(MusicService.ACTION_TOGGLE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(ctx, 0, intent, flags)
                : PendingIntent.getService(ctx, 0, intent, flags);
        v.setOnClickPendingIntent(R.id.w_toggle, pi);
        return v;
    }

    /** Gọi từ MusicService mỗi khi trạng thái phát thay đổi. */
    public static void refresh(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, MusicWidgetProvider.class));
        for (int id : ids) m.updateAppWidget(id, build(ctx));
    }
}
