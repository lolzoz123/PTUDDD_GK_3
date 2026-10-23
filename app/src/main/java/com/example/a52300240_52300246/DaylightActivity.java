package com.example.a52300240_52300246;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Màn hình "Daylight" - chuyển từ React (App.tsx + index.css) sang XML + Java. */
public class DaylightActivity extends AppCompatActivity {

    // ---------- Model ----------
    static class Task {
        final long id;
        String title;
        final String note;
        boolean done;
        final int color; // 0 peach, 1 blue, 2 mint, 3 lilac

        Task(long id, String title, String note, boolean done, int color) {
            this.id = id;
            this.title = title;
            this.note = note;
            this.done = done;
            this.color = color;
        }
    }

    private static final int[] DOT_BG = {
            R.drawable.bg_task_dot_peach, R.drawable.bg_task_dot_blue,
            R.drawable.bg_task_dot_mint, R.drawable.bg_task_dot_lilac};

    private final List<Task> tasks = new ArrayList<>();
    private final List<ValueAnimator> animators = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    private long nextId = 100;
    private TaskAdapter adapter;
    private TextView tvDate, tvTime, tvLeft;
    private ProgressBar progress;
    private View btnAddRow, formAdd;
    private EditText etNewTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Tràn viền: nền wallpaper phủ cả thanh trạng thái / thanh điều hướng
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_daylight);

        View content = findViewById(R.id.contentRoot);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
        tvLeft = findViewById(R.id.tvLeft);
        progress = findViewById(R.id.progress);
        btnAddRow = findViewById(R.id.btnAddRow);
        formAdd = findViewById(R.id.formAdd);
        etNewTask = findViewById(R.id.etNewTask);

        // Dữ liệu ban đầu (giống initialTasks)
        tasks.add(new Task(1, "Prepare project brief", "Design · 10:00 AM", false, 0));
        tasks.add(new Task(2, "Team stand-up", "Meet · 11:30 AM", true, 1));
        tasks.add(new Task(3, "Book a dentist appointment", "Personal · 2:00 PM", false, 2));
        tasks.add(new Task(4, "Evening run", "Health · 6:30 PM", false, 3));

        adapter = new TaskAdapter(tasks, this::updateSummary);
        RecyclerView rv = findViewById(R.id.rvTasks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setNestedScrollingEnabled(false); // cuộn cùng NestedScrollView bên ngoài
        rv.setAdapter(adapter);

        btnAddRow.setOnClickListener(v -> {
            btnAddRow.setVisibility(View.GONE);
            formAdd.setVisibility(View.VISIBLE);
            showKeyboard(etNewTask);
        });
        findViewById(R.id.btnAddSubmit).setOnClickListener(v -> addTask());
        etNewTask.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask();
                return true;
            }
            return false;
        });

        updateSummary();
        updateClock();
        startAmbientAnimations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ValueAnimator a : animators) a.cancel();
    }

    // ---------- Logic ----------
    private void addTask() {
        String title = etNewTask.getText().toString().trim();
        if (title.isEmpty()) return;

        tasks.add(new Task(nextId++, title, "New task · Today", false, tasks.size() % 4));
        adapter.notifyItemInserted(tasks.size() - 1);

        etNewTask.setText("");
        hideKeyboard(etNewTask);
        formAdd.setVisibility(View.GONE);
        btnAddRow.setVisibility(View.VISIBLE);
        updateSummary();
    }

    private void updateSummary() {
        int open = 0;
        for (Task t : tasks) if (!t.done) open++;
        tvLeft.setText(open + " tasks left");
        int percent = tasks.isEmpty() ? 0 : (tasks.size() - open) * 100 / tasks.size();
        progress.setProgress(percent, true);
    }

    private void updateClock() {
        Date now = new Date();
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        tvDate.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
    }

    // ---------- Animation (thay cho @keyframes drift-* và sound) ----------
    private void startAmbientAnimations() {
        drift(findViewById(R.id.auroraOne), 70, 40, 18000);
        drift(findViewById(R.id.auroraTwo), -70, 70, 22000);
        drift(findViewById(R.id.auroraThree), -40, -60, 20000);

        LinearLayout wave = findViewById(R.id.soundWave);
        for (int i = 0; i < wave.getChildCount(); i++) {
            ObjectAnimator a = ObjectAnimator.ofFloat(wave.getChildAt(i), View.SCALE_Y, 1f, 0.45f);
            a.setDuration(1100);
            a.setStartDelay(i * 250L);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.start();
            animators.add(a);
        }
    }

    private void drift(View v, float dxDp, float dyDp, long duration) {
        float d = getResources().getDisplayMetrics().density;
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dxDp * d),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dyDp * d),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f));
        a.setDuration(duration);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
        animators.add(a);
    }

    // ---------- Bàn phím ----------
    static void showKeyboard(View v) {
        v.postDelayed(() -> {
            v.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    v.getContext().getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }, 60);
    }

    static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager)
                v.getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ---------- Adapter ----------
    static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {
        private final List<Task> data;
        private final Runnable onChanged;
        private long editingId = -1;

        TaskAdapter(List<Task> data, Runnable onChanged) {
            this.data = data;
            this.onChanged = onChanged;
        }

        static class VH extends RecyclerView.ViewHolder {
            final View btnCheck, dot, layoutView, layoutContent, layoutEdit, tvSave, tvCancel;
            final ImageView ivCheck;
            final TextView tvTitle, tvNote;
            final ImageButton btnEdit, btnDelete;
            final EditText etEdit;

            VH(@NonNull View v) {
                super(v);
                btnCheck = v.findViewById(R.id.btnCheck);
                dot = v.findViewById(R.id.dot);
                ivCheck = v.findViewById(R.id.ivCheck);
                layoutView = v.findViewById(R.id.layoutView);
                layoutContent = v.findViewById(R.id.layoutContent);
                layoutEdit = v.findViewById(R.id.layoutEdit);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvNote = v.findViewById(R.id.tvNote);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                etEdit = v.findViewById(R.id.etEdit);
                tvSave = v.findViewById(R.id.tvSave);
                tvCancel = v.findViewById(R.id.tvCancel);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_task, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Task t = data.get(position);
            boolean editing = t.id == editingId;

            h.layoutView.setVisibility(editing ? View.GONE : View.VISIBLE);
            h.layoutEdit.setVisibility(editing ? View.VISIBLE : View.GONE);

            // .task-dot / .is-done
            h.dot.setBackgroundResource(t.done ? R.drawable.bg_task_dot_done : DOT_BG[t.color]);
            h.ivCheck.setVisibility(t.done ? View.VISIBLE : View.INVISIBLE);

            h.tvTitle.setText(t.title);
            h.tvNote.setText(t.note);
            h.tvTitle.setAlpha(t.done ? 0.46f : 1f);
            h.tvNote.setAlpha(t.done ? 0.55f : 1f);
            int flags = h.tvTitle.getPaintFlags();
            h.tvTitle.setPaintFlags(t.done
                    ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                    : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);

            View.OnClickListener toggle = v -> {
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION) return;
                data.get(p).done = !data.get(p).done;
                notifyItemChanged(p);
                onChanged.run();
            };
            h.btnCheck.setOnClickListener(toggle);
            h.layoutContent.setOnClickListener(toggle);

            h.btnEdit.setOnClickListener(v -> startEditing(h));
            h.btnDelete.setOnClickListener(v -> delete(h));

            h.tvSave.setOnClickListener(v -> saveEdit(h));
            h.tvCancel.setOnClickListener(v -> cancelEdit(h));
            h.etEdit.setOnEditorActionListener((tv, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveEdit(h);
                    return true;
                }
                return false;
            });

            if (editing) {
                h.etEdit.setText(t.title);
                h.etEdit.setSelection(h.etEdit.getText().length());
                showKeyboard(h.etEdit);
            }
        }

        private void startEditing(VH h) {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;
            long old = editingId;
            editingId = data.get(p).id;
            if (old != -1) {
                for (int i = 0; i < data.size(); i++) {
                    if (data.get(i).id == old) notifyItemChanged(i);
                }
            }
            notifyItemChanged(p);
        }

        private void saveEdit(VH h) {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;
            String title = h.etEdit.getText().toString().trim();
            if (title.isEmpty()) return;
            data.get(p).title = title;
            editingId = -1;
            hideKeyboard(h.etEdit);
            notifyItemChanged(p);
        }

        private void cancelEdit(VH h) {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;
            editingId = -1;
            hideKeyboard(h.etEdit);
            notifyItemChanged(p);
        }

        private void delete(VH h) {
            int p = h.getBindingAdapterPosition();
            if (p == RecyclerView.NO_POSITION) return;
            if (data.get(p).id == editingId) editingId = -1;
            data.remove(p);
            notifyItemRemoved(p);
            onChanged.run();
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
