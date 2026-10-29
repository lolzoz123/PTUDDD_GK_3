package com.example.a52300240_52300246;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tìm nhạc trực tuyến bằng iTunes Search API (không cần API key, trả về bản nghe thử 30 giây). */
public class OnlineMusicActivity extends AppCompatActivity {

    private static final String SEARCH_URL =
            "https://itunes.apple.com/search?media=music&entity=song&limit=25&term=";

    static class Track {
        final String title, artist, previewUrl;

        Track(String title, String artist, String previewUrl) {
            this.title = title;
            this.artist = artist;
            this.previewUrl = previewUrl;
        }
    }

    private final List<Track> tracks = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int searchToken = 0;

    private TrackAdapter adapter;
    private EditText etQuery;
    private ProgressBar progress;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_online_search);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.online_root), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        etQuery = findViewById(R.id.etQuery);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new TrackAdapter();
        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnSearch).setOnClickListener(v -> search());
        etQuery.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        searchToken++;
        io.shutdownNow();
        super.onDestroy();
    }

    // ---------- Tìm kiếm ----------
    private void search() {
        String q = etQuery.getText().toString().trim();
        if (q.isEmpty()) return;

        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(etQuery.getWindowToken(), 0);

        final int token = ++searchToken;
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        io.execute(() -> {
            try {
                List<Track> found = query(q);
                main.post(() -> {
                    if (token != searchToken) return; // đã có lần tìm mới hơn
                    progress.setVisibility(View.GONE);
                    tracks.clear();
                    tracks.addAll(found);
                    adapter.notifyDataSetChanged();
                    if (found.isEmpty()) {
                        tvEmpty.setText("Không tìm thấy bài hát nào");
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (token != searchToken) return;
                    progress.setVisibility(View.GONE);
                    tracks.clear();
                    adapter.notifyDataSetChanged();
                    tvEmpty.setText("Không tìm được. Hãy kiểm tra kết nối mạng.");
                    tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private List<Track> query(String q) throws Exception {
        HttpURLConnection c = (HttpURLConnection)
                new URL(SEARCH_URL + URLEncoder.encode(q, "UTF-8")).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONArray arr = new JSONObject(sb.toString()).getJSONArray("results");
            List<Track> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String url = o.optString("previewUrl", "");
                if (url.isEmpty()) continue;
                list.add(new Track(o.optString("trackName", "(không tên)"),
                        o.optString("artistName", ""), url));
            }
            return list;
        } finally {
            c.disconnect();
        }
    }

    // ---------- Phát ----------
    /** Phát bài ở vị trí position; cả danh sách kết quả trở thành danh sách phát. */
    private void play(int position) {
        int n = tracks.size();
        String[] urls = new String[n];
        String[] titles = new String[n];
        for (int k = 0; k < n; k++) {
            Track t = tracks.get(k);
            urls[k] = t.previewUrl;
            titles[k] = t.title + " - " + t.artist;
        }
        Intent i = new Intent(this, MusicService.class)
                .setAction(MusicService.ACTION_PLAY_ONLINE)
                .putExtra(MusicService.EXTRA_URLS, urls)
                .putExtra(MusicService.EXTRA_TITLES, titles)
                .putExtra(MusicService.EXTRA_INDEX, position);
        ContextCompat.startForegroundService(this, i);
        Toast.makeText(this, "Đang tải: " + tracks.get(position).title, Toast.LENGTH_SHORT).show();
    }

    // ---------- Adapter ----------
    private class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView title, artist;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.tvTrackTitle);
                artist = v.findViewById(R.id.tvTrackArtist);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_online_track, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Track t = tracks.get(position);
            h.title.setText(t.title);
            h.artist.setText(t.artist);
            h.itemView.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) play(p);
            });
        }

        @Override
        public int getItemCount() {
            return tracks.size();
        }
    }
}