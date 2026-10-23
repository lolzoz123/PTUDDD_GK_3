package com.example.a52300240_52300246;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.Random;

/**
 * Hình nền động: đèn bokeh trôi + sóng ở đáy màn hình.
 * Khi nhạc phát (PlayerState.playing) hình nền chuyển động nhanh và "đập" theo nhịp;
 * khi tạm dừng nó chậm lại nhưng vẫn còn sống.
 */
public class LiveWallpaperService extends WallpaperService {

    private static class Dot {
        float x, y;
        final float r, vx, vy, ph;
        final int color;

        Dot(float x, float y, float r, float vx, float vy, int color, float ph) {
            this.x = x; this.y = y; this.r = r; this.vx = vx; this.vy = vy;
            this.color = color; this.ph = ph;
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new MusicEngine();
    }

    private class MusicEngine extends Engine {
        private final float density = getResources().getDisplayMetrics().density;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int[] palette = {0xFFFFB454, 0xFF5FD0C5, 0xFFFF7A90, 0xFFFFD696};
        private final int[] waveColors = {0x703B5B94, 0x782B4A80, 0x7F1C3866};
        private final Dot[] dots = new Dot[26];

        private final Paint bg = new Paint();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private float w = 1f, h = 1f, t = 0f, speed = .2f, energy = 0f;
        private long last = 0L;
        private int frames = 0;
        private String title = "";
        private boolean visible = false;

        private final Runnable runner = new Runnable() {
            @Override
            public void run() {
                frame();
                if (visible) handler.postDelayed(this, 16);
            }
        };

        MusicEngine() {
            Random rnd = new Random(7);
            for (int i = 0; i < dots.length; i++) {
                dots[i] = new Dot(rnd.nextFloat(), rnd.nextFloat(),
                        14f + rnd.nextFloat() * 40f,
                        (rnd.nextFloat() - .5f) * .03f, -.02f - rnd.nextFloat() * .04f,
                        palette[rnd.nextInt(4)], rnd.nextFloat() * 6.28f);
            }
            PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
            fill.setXfermode(add);
            ring.setXfermode(add);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2f * density);

            titlePaint.setColor(Color.WHITE);
            titlePaint.setAlpha(215);
            titlePaint.setTextSize(22f * density);
            titlePaint.setTextAlign(Paint.Align.CENTER);

            subPaint.setColor(Color.WHITE);
            subPaint.setAlpha(140);
            subPaint.setTextSize(13f * density);
            subPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            w = width;
            h = height;
            bg.setShader(new LinearGradient(0f, 0f, 0f, h, 0xFF0A1124, 0xFF1B1C3A, Shader.TileMode.CLAMP));
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            handler.removeCallbacks(runner);
            if (v) {
                last = SystemClock.uptimeMillis();
                handler.post(runner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(runner);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            visible = false;
            handler.removeCallbacks(runner);
            super.onDestroy();
        }

        private void frame() {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min((now - last) / 1000f, .05f);
            last = now;

            boolean playing = PlayerState.playing;
            speed += ((playing ? 1f : .2f) - speed) * Math.min(1f, dt * 3f);
            energy += ((playing ? 1f : 0f) - energy) * Math.min(1f, dt * 2f);
            float dts = dt * speed;
            t += dts;
            if (frames++ % 60 == 0) title = PlayerState.title(getApplicationContext());

            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = holder.lockCanvas();
            if (c == null) return;
            try {
                draw(c, dts, playing);
            } finally {
                holder.unlockCanvasAndPost(c);
            }
        }

        private void draw(Canvas c, float dts, boolean playing) {
            c.drawPaint(bg);

            // Đèn bokeh
            for (Dot o : dots) {
                o.x += o.vx * dts;
                o.y += o.vy * dts;
                if (o.y < -.12f) o.y = 1.12f;
                if (o.x < -.12f) o.x = 1.12f;
                if (o.x > 1.12f) o.x = -.12f;

                float pulse = .5f + .5f * (float) Math.sin(t * 2.4f + o.ph);
                float r = o.r * density * (1f + energy * .3f * pulse);
                float a = .16f + energy * .2f;

                fill.setColor(o.color);
                fill.setAlpha((int) (255 * a * .6f));
                ring.setColor(o.color);
                ring.setAlpha((int) Math.min(255f, 255 * a * 1.5f));
                c.drawCircle(o.x * w, o.y * h, r, fill);
                c.drawCircle(o.x * w, o.y * h, r, ring);
            }

            // Sóng ở đáy
            for (int i = 0; i < 3; i++) {
                float amp = h * .012f * (1f + energy * 1.5f) * (1f + i * .3f);
                path.reset();
                path.moveTo(0f, h);
                for (float x = 0f; x <= w; x += 16f) {
                    float phase = (float) (x / w * (5 + i) * Math.PI + t * (1f + i * .4f));
                    path.lineTo(x, h * (.8f + i * .05f) + (float) Math.sin(phase) * amp);
                }
                path.lineTo(w, h);
                path.close();
                wave.setColor(waveColors[i]);
                c.drawPath(path, wave);
            }

            // Tên bài hát + trạng thái
            String shown = title.length() > 28 ? title.substring(0, 28) : title;
            c.drawText(shown, w / 2f, h * .62f, titlePaint);
            c.drawText(playing ? "Đang phát" : "Tạm dừng", w / 2f, h * .62f + 24f * density, subPaint);
        }
    }
}
