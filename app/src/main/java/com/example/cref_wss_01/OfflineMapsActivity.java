package com.example.cref_wss_01;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

public class OfflineMapsActivity extends AppCompatActivity
        implements OfflineMapAreaAdapter.OnDeleteClickListener {

    private OfflineMapAreaAdapter adapter;
    private AppDatabase db;
    private TextView tvCacheSize, tvEmpty;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_offline_maps);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvCacheSize  = findViewById(R.id.tvCacheSize);
        tvEmpty      = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerAreas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = AppDatabase.getDatabase(this);
        adapter = new OfflineMapAreaAdapter(this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAddArea);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, MapAreaDownloadActivity.class)));

        loadAreas();
        refreshCacheSize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAreas();
        refreshCacheSize();
    }

    private void loadAreas() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<OfflineMapArea> areas = db.offlineMapAreaDao().getAll();
            runOnUiThread(() -> {
                adapter.setAreas(areas);
                boolean empty = areas.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void refreshCacheSize() {
        Executors.newSingleThreadExecutor().execute(() -> {
            File cacheDir = Configuration.getInstance().getOsmdroidTileCache();
            long bytes = getFolderSize(cacheDir);
            runOnUiThread(() -> tvCacheSize.setText(formatBytes(bytes) + " used in tile cache"));
        });
    }

    private long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) size += f.isDirectory() ? getFolderSize(f) : f.length();
        return size;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void onDeleteClick(OfflineMapArea area) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + area.name + "\"?")
                .setMessage("This will remove the record for this area. Cached tiles will be freed "
                        + "automatically over time, or you can clear all tiles via Android Settings > "
                        + "Apps > WSS Condition Assessment > Storage > Clear Cache.")
                .setPositiveButton("Delete", (dialog, which) -> deleteArea(area))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteArea(OfflineMapArea area) {
        Executors.newSingleThreadExecutor().execute(() -> {
            db.offlineMapAreaDao().delete(area);
            runOnUiThread(() -> {
                loadAreas();
                Toast.makeText(this, "\"" + area.name + "\" deleted.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
