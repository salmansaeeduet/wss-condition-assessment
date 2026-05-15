package com.example.cref_wss_01;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MapPickerActivity extends AppCompatActivity {

    private MapView mapView;
    private Marker pin;
    private TextView tvLat, tvLng;
    private Button btnConfirm;
    private int searchVersion = 0;

    private static final double DEFAULT_LAT  = 28.5;
    private static final double DEFAULT_LNG  = 67.5;
    private static final double DEFAULT_ZOOM = 7.0;
    private static final double PICK_ZOOM    = 14.0;

    private final ActivityResultLauncher<String> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) panToMyLocation();
                else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_picker);

        mapView    = findViewById(R.id.mapView);
        tvLat      = findViewById(R.id.tvLat);
        tvLng      = findViewById(R.id.tvLng);
        btnConfirm = findViewById(R.id.btnConfirm);
        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Top panel: push below status bar
        LinearLayout topPanelInner = findViewById(R.id.topPanelInner);
        final int panelPadding = dpToPx(10);
        ViewCompat.setOnApplyWindowInsetsListener(topPanelInner, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(panelPadding, top + panelPadding, panelPadding, panelPadding);
            return insets;
        });

        // Bottom bar and FAB: push above navigation bar
        final int fabOriginalBottom = (int)(96 * getResources().getDisplayMetrics().density);
        android.view.View bottomBar = findViewById(R.id.bottomBar);
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = navBar;
            v.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(fabMyLocation, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = fabOriginalBottom + navBar;
            v.setLayoutParams(lp);
            return insets;
        });

        setupMap();

        Intent in = getIntent();
        if (in.hasExtra("initial_lat") && in.hasExtra("initial_lng")) {
            double lat = in.getDoubleExtra("initial_lat", DEFAULT_LAT);
            double lng = in.getDoubleExtra("initial_lng", DEFAULT_LNG);
            GeoPoint pt = new GeoPoint(lat, lng);
            mapView.getController().setCenter(pt);
            mapView.getController().setZoom(PICK_ZOOM);
            placePin(pt);
        } else {
            mapView.getController().setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));
            mapView.getController().setZoom(DEFAULT_ZOOM);
        }

        MapEventsOverlay tapOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                placePin(p);
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        });
        mapView.getOverlays().add(0, tapOverlay);

        fabMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                panToMyLocation();
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (pin != null) {
                Intent result = new Intent();
                result.putExtra("lat", pin.getPosition().getLatitude());
                result.putExtra("lng", pin.getPosition().getLongitude());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        setupSearch();
    }

    private void setupMap() {
        mapView.setTileSource(EsriTileSourceFactory.create());
        EsriTileSourceFactory.addLabelOverlay(mapView, this);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
    }

    private void setupSearch() {
        EditText etSearch      = findViewById(R.id.etSearch);
        ImageButton btnSearch  = findViewById(R.id.btnSearch);
        LinearLayout llResults = findViewById(R.id.llResults);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        Runnable doSearch = () -> {
            String q = etSearch.getText().toString().trim();
            if (q.isEmpty()) return;
            if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            final int ver = ++searchVersion;
            llResults.setVisibility(View.GONE);

            MapSearchHelper.search(q, Executors.newSingleThreadExecutor(),
                    new MapSearchHelper.Callback() {
                @Override
                public void onResults(List<MapSearchHelper.Result> results) {
                    if (ver != searchVersion) return;
                    llResults.removeAllViews();
                    if (results.isEmpty()) {
                        addResultRow(llResults, "No results found.", null);
                    } else {
                        for (MapSearchHelper.Result r : results)
                            addResultRow(llResults, r.name, r);
                    }
                    llResults.setVisibility(View.VISIBLE);
                }
                @Override
                public void onError(String msg) {
                    if (ver != searchVersion) return;
                    Toast.makeText(MapPickerActivity.this,
                            "Search error: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
        };

        btnSearch.setOnClickListener(v -> doSearch.run());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch.run();
                return true;
            }
            return false;
        });
    }

    private void addResultRow(LinearLayout container, String label, MapSearchHelper.Result result) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int ph = dpToPx(6), pv = dpToPx(8);
        tv.setPadding(pv, ph, pv, ph);
        if (result != null) {
            android.util.TypedValue attr = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, attr, true);
            tv.setBackgroundResource(attr.resourceId);
            tv.setOnClickListener(view -> {
                searchVersion++;
                container.setVisibility(View.GONE);
                zoomToResult(result);
            });
        }
        container.addView(tv);
    }

    private void zoomToResult(MapSearchHelper.Result r) {
        if (r.bbox != null) {
            mapView.post(() -> mapView.zoomToBoundingBox(r.bbox, true, 80));
        } else {
            mapView.getController().animateTo(r.center, 14.0, 800L);
        }
    }

    private void placePin(GeoPoint point) {
        if (pin == null) {
            pin = new Marker(mapView);
            pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(pin);
        }
        pin.setPosition(point);
        mapView.invalidate();

        tvLat.setText("Lat: " + String.format(Locale.US, "%.6f", point.getLatitude()));
        tvLng.setText("Lng: " + String.format(Locale.US, "%.6f", point.getLongitude()));
        btnConfirm.setEnabled(true);
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
            GeoPoint pt = new GeoPoint(last);
            mapView.getController().animateTo(pt, mapView.getZoomLevelDouble(), 500L);
            placePin(pt);
            return;
        }

        Toast.makeText(this, "Acquiring location…", Toast.LENGTH_SHORT).show();
        String provider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        LocationListener[] ref = new LocationListener[1];
        ref[0] = loc -> {
            lm.removeUpdates(ref[0]);
            runOnUiThread(() -> {
                GeoPoint pt = new GeoPoint(loc);
                mapView.getController().animateTo(pt, mapView.getZoomLevelDouble(), 500L);
                placePin(pt);
            });
        };
        lm.requestLocationUpdates(provider, 0, 0, ref[0]);
    }

    @Override
    protected void onResume() { super.onResume(); mapView.onResume(); }

    @Override
    protected void onPause()  { super.onPause();  mapView.onPause();  }
}
