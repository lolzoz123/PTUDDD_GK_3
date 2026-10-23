package com.example.a52300240_52300246;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button btnOpenDaylight = findViewById(R.id.btnOpenDaylight);
        btnOpenDaylight.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DaylightActivity.class)));

        Button btnOpenMusic = findViewById(R.id.btnOpenMusic);
        btnOpenMusic.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, musicwidget.class)));
    }
}