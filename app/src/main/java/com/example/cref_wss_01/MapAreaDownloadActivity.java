package com.example.cref_wss_01;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.widget.SeekBar;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Locale;
import java.util.concurrent.Executors;

public class MapAreaDownloadActivity extends AppCompatActivity {

    private static final double DEFAULT_LAT  = 28.5;
    private static final double DEFAULT_LNG  = 67.5;
    private static final double DEFAULT_ZOOM = 7.0;
    private static final int    MAX_DOWNLOAD_ZOOM = 17;

    private MapView mapView;
    private CacheManager cacheManager;
    private TextView tvZoomInfo, tvTileEstimate, tvZoomDepthValue;
    private SeekBar sliderZoomDepth;

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) panToMyLocation();
                else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map_area_download);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mapView          = findViewById(R.id.mapView);
        tvZoomInfo       = findViewById(R.id.tvZoomInfo);
        tvTileEstimate   = findViewById(R.id.tvTileEstimate);
        sliderZoomDepth  = findViewById(R.id.sliderZoomDepth);
        tvZoomDepthValue = findViewById(R.id.tvZoomDepthValue);
        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);
        Button btnDownload = findViewById(R.id.btnDownloadArea);

        setupMap();
        cacheManager = new CacheManager(mapView);

        mapView.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { updateEstimate(); return false; }
            @Override public boolean onZoom(ZoomEvent e)     { updateEstimate(); return false; }
        });

        sliderZoomDepth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvZoomDepthValue.setText("+" + progress);
                updateEstimate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        fabMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                panToMyLocation();
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        btnDownload.setOnClickListener(v -> showNameDialog());

        updateEstimate();
    }

    private void setupMap() {
        mapView.setTileSource(EsriTileSourceFactory.create());
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        mapView.getController().setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));
        mapView.getController().setZoom(DEFAULT_ZOOM);
    }

    private void updateEstimate() {
        BoundingBox bbox = mapView.getBoundingBox();
        int zMin = (int) mapView.getZoomLevelDouble();
        int zDepth = (int) sliderZoomDepth.getProgress();
        int zMax = Math.min(zMin + zDepth, MAX_DOWNLOAD_ZOOM);
        int tileCount = cacheManager.possibleTilesInArea(bbox, zMin, zMax);

        tvZoomInfo.setText("Zoom levels: " + zMin + " – " + zMax);
        tvTileEstimate.setText("Estimated tiles: " + String.format(Locale.US, "%,d", tileCount)
                + (tileCount > 5000 ? "  ⚠ Large — use Wi-Fi" : ""));
    }

    private void showNameDialog() {
        EditText etName = new EditText(this);
        etName.setHint("e.g. Quetta Region, Turbat Area");
        etName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int pad = dpToPx(16);
        etName.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setTitle("Name this area")
                .setView(etName)
                .setPositiveButton("Download", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = "Area " + (System.currentTimeMillis() / 1000);
                    startDownload(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownload(String name) {
        BoundingBox bbox    = mapView.getBoundingBox();
        int zMin            = (int) mapView.getZoomLevelDouble();
        int zMax            = Math.min(zMin + (int) sliderZoomDepth.getProgress(), MAX_DOWNLOAD_ZOOM);
        int tileCount       = cacheManager.possibleTilesInArea(bbox, zMin, zMax);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(tileCount);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbParams.topMargin = dpToPx(4);
        progressBar.setLayoutParams(pbParams);

        TextView tvProgress = new TextView(this);
        tvProgress.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvProgress.setGravity(Gravity.CENTER);
        tvProgress.setText("0 / " + String.format(Locale.US, "%,d", tileCount) + " tiles");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), 0);
        content.addView(progressBar);
        content.addView(tvProgress);

        final int    finalZMin      = zMin;
        final int    finalZMax      = zMax;
        final int    finalTileCount = tileCount;
        final String finalName      = name;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Downloading \"" + name + "\"")
                .setView(content)
                .setNegativeButton("Stop", null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnStop = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            cacheManager.downloadAreaAsync(this, bbox, finalZMin, finalZMax,
                    new CacheManager.CacheManagerCallback() {
                @Override
                public void onTaskComplete() {
                    saveAreaToDb(finalName, bbox, finalZMin, finalZMax, finalTileCount);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(MapAreaDownloadActivity.this,
                                "\"" + finalName + "\" saved.", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }

                @Override
                public void updateProgress(int progress, int currentZoom, int zoomMin, int zoomMax) {
                    runOnUiThread(() -> {
                        progressBar.setProgress(progress);
                        tvProgress.setText(String.format(Locale.US,
                                "%,d / %,d tiles  (zoom %d)", progress, finalTileCount, currentZoom));
                    });
                }

                @Override public void downloadStarted() {}
                @Override public void setPossibleTilesInArea(int total) {}

                @Override
                public void onTaskFailed(int errors) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(MapAreaDownloadActivity.this,
                                "Download finished with " + errors + " error(s).",
                                Toast.LENGTH_LONG).show();
                    });
                }
            });

            btnStop.setOnClickListener(v -> {
                cacheManager.cancelAllJobs();
                dialog.dismiss();
                Toast.makeText(this, "Download cancelled.", Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private void saveAreaToDb(String name, BoundingBox bbox, int zMin, int zMax, int tileCount) {
        OfflineMapArea area = new OfflineMapArea();
        area.name        = name;
        area.north       = bbox.getLatNorth();
        area.south       = bbox.getLatSouth();
        area.east        = bbox.getLonEast();
        area.west        = bbox.getLonWest();
        area.zoomMin     = zMin;
        area.zoomMax     = zMax;
        area.tileCount   = tileCount;
        area.downloadedAt = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() ->
                AppDatabase.getDatabase(this).offlineMapAreaDao().insert(area));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @SuppressLint("MissingPermission")
    private void panToMyLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (last != null) {
            mapView.getController().animateTo(new GeoPoint(last), mapView.getZoomLevelDouble(), 500L);
            return;
        }

        Toast.makeText(this, "Acquiring location…", Toast.LENGTH_SHORT).show();
        String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        LocationListener[] ref = new LocationListener[1];
        ref[0] = loc -> {
            lm.removeUpdates(ref[0]);
            runOnUiThread(() -> mapView.getController()
                    .animateTo(new GeoPoint(loc), mapView.getZoomLevelDouble(), 500L));
        };
        lm.requestLocationUpdates(provider, 0, 0, ref[0]);
    }

    @Override
    protected void onResume() { super.onResume(); mapView.onResume(); }

    @Override
    protected void onPause() { super.onPause(); mapView.onPause(); }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
