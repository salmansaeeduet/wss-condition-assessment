package com.example.cref_wss_01;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Overlay;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class GeometryPickerActivity extends AppCompatActivity {

    private static final double DEFAULT_LAT  = 28.5;
    private static final double DEFAULT_LNG  = 67.5;
    private static final double DEFAULT_ZOOM = 7.0;

    private enum PickerMode { DRAWING, SELECTING, EDITING }

    private MapView mapView;
    private TextView tvTitle, tvStatus;
    private Button btnUndo, btnAction, btnToggleEdit, btnDone;
    private MaterialButtonToggleGroup toggleGroupMode;

    private PickerMode pickerMode = PickerMode.DRAWING;
    private String activeDrawType = "LINE";

    private final List<GeometryUtils.GeometryItem> completedItems  = new ArrayList<>();
    private final List<Overlay>                     completedOverlays = new ArrayList<>();
    private int selectedIndex = -1;

    // Count of each geometry type present in other_geoms (all other survey sources),
    // used to produce globally unique auto-labels across the whole survey.
    private final Map<String, Integer> otherGeomTypeCounts = new HashMap<>();

    private final List<GeoPoint> currentDraft = new ArrayList<>();
    private Overlay draftPolylineOverlay;
    private DragHandlesOverlay dragHandlesOverlay;

    private int questionColor;
    private float density;
    private int searchVersion = 0;
    private String defaultLabel;  // from intent "default_label"; null = unlabelled mode

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_geometry_picker);

        density = getResources().getDisplayMetrics().density;

        int qId      = getIntent().getIntExtra("question_id", 0);
        String label = getIntent().getStringExtra("question_label");
        defaultLabel = getIntent().getStringExtra("default_label");

        questionColor = GeometryUtils.colorForQuestion(qId);

        mapView       = findViewById(R.id.mapView);
        tvTitle       = findViewById(R.id.tvTitle);
        tvStatus      = findViewById(R.id.tvStatus);
        btnUndo       = findViewById(R.id.btnUndo);
        btnAction     = findViewById(R.id.btnAction);
        btnToggleEdit = findViewById(R.id.btnToggleEdit);
        btnDone       = findViewById(R.id.btnDone);
        toggleGroupMode = findViewById(R.id.toggleGroupMode);

        tvTitle.setText(label != null ? label : "Geometry");

        setupMap();
        loadOtherGeoms();
        loadExistingGeoms();
        setupTapOverlay();
        setupModeSelector();
        setupButtons();

        // Push control panel below the status bar
        LinearLayout topPanelInner = findViewById(R.id.topPanelInner);
        final int panelPadding = (int)(10 * density);
        ViewCompat.setOnApplyWindowInsetsListener(topPanelInner, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(panelPadding, top + panelPadding, panelPadding, panelPadding);
            return insets;
        });

        setupSearch();

        // Default to LINE mode
        toggleGroupMode.check(R.id.btnModeLine);
        updateUI();
    }

    // ── Map setup ─────────────────────────────────────────────────────────────

    private void setupMap() {
        mapView.setTileSource(EsriTileSourceFactory.create());
        EsriTileSourceFactory.addLabelOverlay(mapView, this);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);

        String existing = getIntent().getStringExtra("existing_value");
        List<GeometryUtils.GeometryItem> loaded = GeometryUtils.fromJson(existing);
        if (!loaded.isEmpty()) {
            List<GeoPoint> all = new ArrayList<>();
            for (GeometryUtils.GeometryItem g : loaded) all.addAll(g.points);
            if (!all.isEmpty()) {
                try {
                    BoundingBox bb = BoundingBox.fromGeoPoints(all);
                    mapView.post(() -> mapView.zoomToBoundingBox(bb, true, 80));
                    return;
                } catch (Exception ignored) {}
            }
        }
        mapView.getController().setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));
        mapView.getController().setZoom(DEFAULT_ZOOM);
    }

    private void loadOtherGeoms() {
        String otherGeomsJson = getIntent().getStringExtra("other_geoms");
        if (otherGeomsJson == null || otherGeomsJson.isEmpty()) return;
        try {
            JSONObject map = new JSONObject(otherGeomsJson);
            Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String geomJson = map.getString(key);
                int colorSeed;
                try {
                    colorSeed = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    // "att_<id>" or other string key — use the numeric suffix if present
                    int under = key.lastIndexOf('_');
                    try { colorSeed = Integer.parseInt(under >= 0 ? key.substring(under + 1) : key); }
                    catch (NumberFormatException e2) { colorSeed = key.hashCode(); }
                }
                int color = GeometryUtils.colorForQuestion(colorSeed);
                List<GeometryUtils.GeometryItem> items = GeometryUtils.fromJson(geomJson);
                for (GeometryUtils.GeometryItem item : items) {
                    otherGeomTypeCounts.merge(item.type, 1, Integer::sum);
                    GeometryOverlay ov = new GeometryOverlay(item, color);
                    ov.readOnly = true;
                    mapView.getOverlays().add(0, ov);
                }
            }
        } catch (JSONException ignored) {}
    }

    private void loadExistingGeoms() {
        String existing = getIntent().getStringExtra("existing_value");
        List<GeometryUtils.GeometryItem> loaded = GeometryUtils.fromJson(existing);
        for (GeometryUtils.GeometryItem item : loaded) {
            completedItems.add(item);
            GeometryOverlay ov = new GeometryOverlay(item, questionColor);
            completedOverlays.add(ov);
            mapView.getOverlays().add(ov);
        }
    }

    // ── Tap overlay ───────────────────────────────────────────────────────────

    private void setupTapOverlay() {
        MapEventsOverlay tap = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                switch (pickerMode) {
                    case DRAWING:   onMapTapDrawing(p);   break;
                    case SELECTING: onMapTapSelecting(p); break;
                    case EDITING:   /* drag handles consume their taps; others ignored */ break;
                }
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) { return false; }
        });
        mapView.getOverlays().add(tap);
    }

    // ── Mode selector ─────────────────────────────────────────────────────────

    private void setupModeSelector() {
        toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if      (checkedId == R.id.btnModePoint)   setDrawType("POINT");
            else if (checkedId == R.id.btnModeLine)    setDrawType("LINE");
            else if (checkedId == R.id.btnModePolygon) setDrawType("POLYGON");
            else if (checkedId == R.id.btnModeLabel)   setDrawType("LABEL");
        });
    }

    private void setDrawType(String type) {
        if (pickerMode != PickerMode.DRAWING) return;
        activeDrawType = type;
        currentDraft.clear();
        redrawDraft();
        updateUI();
    }

    // ── Button setup ──────────────────────────────────────────────────────────

    private void setupButtons() {
        btnUndo.setOnClickListener(v -> {
            if (pickerMode == PickerMode.DRAWING) {
                if (!currentDraft.isEmpty()) {
                    currentDraft.remove(currentDraft.size() - 1);
                    redrawDraft();
                    updateUI();
                }
            } else if (pickerMode == PickerMode.EDITING && selectedIndex >= 0) {
                undoEditPoint();
            }
        });

        btnAction.setOnClickListener(v -> {
            if (pickerMode == PickerMode.DRAWING) {
                commitDraft();
            } else if (pickerMode == PickerMode.EDITING) {
                deleteSelected();
            }
        });

        btnToggleEdit.setOnClickListener(v -> {
            if (pickerMode == PickerMode.DRAWING) {
                if (completedItems.isEmpty()) {
                    Toast.makeText(this, "No shapes to edit yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                setPickerMode(PickerMode.SELECTING);
            } else if (pickerMode == PickerMode.SELECTING) {
                setPickerMode(PickerMode.DRAWING);
            } else if (pickerMode == PickerMode.EDITING) {
                stopEditing();
            }
        });

        btnDone.setOnClickListener(v -> finishWithResult());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ── DRAWING mode ──────────────────────────────────────────────────────────

    private void onMapTapDrawing(GeoPoint p) {
        if ("LABEL".equals(activeDrawType)) {
            showLabelDialog(p, null, -1);
            return;
        }
        currentDraft.add(p);
        redrawDraft();
        updateUI();
        // Points are single-coordinate: auto-commit on each tap so every tap = one named point
        if ("POINT".equals(activeDrawType)) {
            commitDraft();
        }
    }

    private void commitDraft() {
        int minPts = minPointsFor(activeDrawType);
        if (currentDraft.size() < minPts) return;
        GeometryUtils.GeometryItem item = new GeometryUtils.GeometryItem(
                activeDrawType, new ArrayList<>(currentDraft));
        int sameTypeInSession = 0;
        for (GeometryUtils.GeometryItem it : completedItems) {
            if (activeDrawType.equals(it.type)) sameTypeInSession++;
        }
        if (defaultLabel != null) {
            // Custom label: number within this session only ("Pump 1", "Pump 2", …)
            item.name = defaultLabel + " " + (sameTypeInSession + 1);
        } else {
            // Auto label: number globally across all survey sources
            int globalN = sameTypeInSession
                    + otherGeomTypeCounts.getOrDefault(activeDrawType, 0) + 1;
            item.name = typeLabelPrefix(activeDrawType) + " " + globalN;
        }
        addCompletedItem(item);
        currentDraft.clear();
        redrawDraft();
        updateUI();
    }

    private static String typeLabelPrefix(String type) {
        switch (type) {
            case "POINT":   return "Point";
            case "LINE":    return "Line";
            case "POLYGON": return "Polygon";
            case "LABEL":   return "Label";
            default:        return type;
        }
    }

    private int minPointsFor(String type) {
        switch (type) {
            case "POLYGON": return 3;
            case "POINT":   return 1;
            default:        return 2; // LINE
        }
    }

    private void addCompletedItem(GeometryUtils.GeometryItem item) {
        completedItems.add(item);
        GeometryOverlay ov = new GeometryOverlay(item, questionColor);
        completedOverlays.add(ov);
        mapView.getOverlays().add(ov);
        mapView.invalidate();
    }

    private void redrawDraft() {
        if (draftPolylineOverlay != null) {
            mapView.getOverlays().remove(draftPolylineOverlay);
            draftPolylineOverlay = null;
        }
        if (currentDraft.size() >= 2 && !"LABEL".equals(activeDrawType)) {
            List<GeoPoint> pts = new ArrayList<>(currentDraft);
            boolean closePoly = "POLYGON".equals(activeDrawType) && pts.size() >= 3;
            draftPolylineOverlay = new Overlay() {
                @Override
                public void draw(Canvas canvas, MapView mv, boolean shadow) {
                    if (shadow) return;
                    Projection proj = mv.getProjection();
                    android.graphics.Point sp = new android.graphics.Point();
                    Path path = new Path();
                    proj.toPixels(pts.get(0), sp);
                    path.moveTo(sp.x, sp.y);
                    for (int i = 1; i < pts.size(); i++) {
                        proj.toPixels(pts.get(i), sp);
                        path.lineTo(sp.x, sp.y);
                    }
                    if (closePoly) path.close();
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setColor(Color.WHITE);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(4f * density);
                    p.setPathEffect(new android.graphics.DashPathEffect(
                            new float[]{12f * density, 6f * density}, 0));
                    canvas.drawPath(path, p);
                }
            };
            mapView.getOverlays().add(draftPolylineOverlay);
        }
        // Draft dots
        mapView.getOverlays().remove(draftDotsOverlay);
        mapView.getOverlays().add(draftDotsOverlay);
        mapView.invalidate();
    }

    private final Overlay draftDotsOverlay = new Overlay() {
        private final Paint fill   = makePaint(Color.WHITE, Paint.Style.FILL);
        private final Paint ring   = makePaint(0xFF333333, Paint.Style.STROKE);
        { ring.setStrokeWidth(2f); }

        @Override
        public void draw(Canvas canvas, MapView mv, boolean shadow) {
            if (shadow || currentDraft.isEmpty()) return;
            Projection proj = mv.getProjection();
            android.graphics.Point sp = new android.graphics.Point();
            float r = 6f * density;
            for (GeoPoint pt : currentDraft) {
                proj.toPixels(pt, sp);
                canvas.drawCircle(sp.x, sp.y, r, fill);
                canvas.drawCircle(sp.x, sp.y, r, ring);
            }
        }
        private Paint makePaint(int color, Paint.Style style) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color); p.setStyle(style); return p;
        }
    };

    // ── SELECTING mode ────────────────────────────────────────────────────────

    private void onMapTapSelecting(GeoPoint p) {
        int hit = findTappedGeometry(p);
        if (hit >= 0) startEditing(hit);
    }

    private int findTappedGeometry(GeoPoint tap) {
        Projection proj = mapView.getProjection();
        android.graphics.Point tapPx = new android.graphics.Point();
        proj.toPixels(tap, tapPx);
        float threshold = 28f * density;

        for (int i = completedItems.size() - 1; i >= 0; i--) {
            GeometryUtils.GeometryItem item = completedItems.get(i);
            switch (item.type) {
                case "POINT":
                case "LABEL": {
                    android.graphics.Point ptPx = new android.graphics.Point();
                    proj.toPixels(item.points.get(0), ptPx);
                    if (dist(tapPx.x, tapPx.y, ptPx.x, ptPx.y) < threshold) return i;
                    break;
                }
                case "LINE": {
                    for (int j = 0; j < item.points.size() - 1; j++) {
                        android.graphics.Point a = new android.graphics.Point();
                        android.graphics.Point b = new android.graphics.Point();
                        proj.toPixels(item.points.get(j), a);
                        proj.toPixels(item.points.get(j + 1), b);
                        if (distToSegment(tapPx.x, tapPx.y, a.x, a.y, b.x, b.y) < threshold) return i;
                    }
                    break;
                }
                case "POLYGON": {
                    if (pointInPolygon(item.points, tap)) return i;
                    int n = item.points.size();
                    for (int j = 0; j < n; j++) {
                        android.graphics.Point a = new android.graphics.Point();
                        android.graphics.Point b = new android.graphics.Point();
                        proj.toPixels(item.points.get(j), a);
                        proj.toPixels(item.points.get((j + 1) % n), b);
                        if (distToSegment(tapPx.x, tapPx.y, a.x, a.y, b.x, b.y) < threshold) return i;
                    }
                    break;
                }
            }
        }
        return -1;
    }

    private float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return dist(px, py, ax, ay);
        float t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lenSq));
        return dist(px, py, ax + t * dx, ay + t * dy);
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean pointInPolygon(List<GeoPoint> polygon, GeoPoint point) {
        int n = polygon.size();
        boolean inside = false;
        double x = point.getLongitude(), y = point.getLatitude();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLongitude(), yi = polygon.get(i).getLatitude();
            double xj = polygon.get(j).getLongitude(), yj = polygon.get(j).getLatitude();
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi))
                inside = !inside;
        }
        return inside;
    }

    // ── EDITING mode ──────────────────────────────────────────────────────────

    private void startEditing(int index) {
        selectedIndex = index;
        GeometryUtils.GeometryItem item = completedItems.get(index);
        ((GeometryOverlay) completedOverlays.get(index)).highlighted = true;

        dragHandlesOverlay = new DragHandlesOverlay(item);
        mapView.getOverlays().add(dragHandlesOverlay);
        mapView.invalidate();

        setPickerMode(PickerMode.EDITING);

        if ("LABEL".equals(item.type)) {
            showEditLabelText(item);
        } else if (item.name != null) {
            showRenameDialog(item);
        }
    }

    private void stopEditing() {
        if (selectedIndex >= 0 && selectedIndex < completedOverlays.size()) {
            ((GeometryOverlay) completedOverlays.get(selectedIndex)).highlighted = false;
        }
        if (dragHandlesOverlay != null) {
            mapView.getOverlays().remove(dragHandlesOverlay);
            dragHandlesOverlay = null;
        }
        selectedIndex = -1;
        mapView.invalidate();
        setPickerMode(PickerMode.DRAWING);
    }

    private void deleteSelected() {
        if (selectedIndex < 0) return;
        if (dragHandlesOverlay != null) {
            mapView.getOverlays().remove(dragHandlesOverlay);
            dragHandlesOverlay = null;
        }
        mapView.getOverlays().remove(completedOverlays.get(selectedIndex));
        completedItems.remove(selectedIndex);
        completedOverlays.remove(selectedIndex);
        selectedIndex = -1;
        mapView.invalidate();
        setPickerMode(PickerMode.DRAWING);
    }

    private void undoEditPoint() {
        if (selectedIndex < 0) return;
        GeometryUtils.GeometryItem item = completedItems.get(selectedIndex);
        int minPts = minPointsFor(item.type);
        if (item.points.size() > minPts) {
            item.points.remove(item.points.size() - 1);
            mapView.invalidate();
            updateUI();
        }
    }

    private void showEditLabelText(GeometryUtils.GeometryItem item) {
        EditText input = new EditText(this);
        input.setText(item.text != null ? item.text : "");
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Edit label text")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        item.text = newText;
                        mapView.invalidate();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog(GeometryUtils.GeometryItem item) {
        EditText input = new EditText(this);
        input.setText(item.name != null ? item.name : "");
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Rename item")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    item.name = newName.isEmpty() ? null : newName;
                    mapView.invalidate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Label dialog ──────────────────────────────────────────────────────────

    private void showLabelDialog(GeoPoint position, String existingText, int replaceIndex) {
        EditText input = new EditText(this);
        if (existingText != null) {
            input.setText(existingText);
            input.setSelection(existingText.length());
        }
        input.setHint("Enter label text");
        new AlertDialog.Builder(this)
                .setTitle("Add label")
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    List<GeoPoint> pts = new ArrayList<>();
                    pts.add(position);
                    GeometryUtils.GeometryItem item = new GeometryUtils.GeometryItem("LABEL", pts);
                    item.text = text;
                    addCompletedItem(item);
                    updateUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Mode transitions ──────────────────────────────────────────────────────

    private void setPickerMode(PickerMode mode) {
        pickerMode = mode;
        updateUI();
    }

    // ── UI state ──────────────────────────────────────────────────────────────

    private void updateUI() {
        switch (pickerMode) {
            case DRAWING: {
                toggleGroupMode.setVisibility(View.VISIBLE);
                btnDone.setVisibility(View.VISIBLE);

                boolean canUndo = !currentDraft.isEmpty();
                btnUndo.setText("Undo");
                btnUndo.setEnabled(canUndo);
                btnUndo.setVisibility(canUndo ? View.VISIBLE : View.GONE);

                boolean canAdd = currentDraft.size() >= minPointsFor(activeDrawType)
                        && !"LABEL".equals(activeDrawType);
                btnAction.setText("Add");
                btnAction.setEnabled(canAdd);
                btnAction.setVisibility(canAdd ? View.VISIBLE : View.GONE);

                btnToggleEdit.setText("Edit");
                btnToggleEdit.setVisibility(completedItems.isEmpty() ? View.GONE : View.VISIBLE);

                tvStatus.setText(buildDrawingStatus());
                break;
            }
            case SELECTING: {
                toggleGroupMode.setVisibility(View.GONE);
                btnDone.setVisibility(View.VISIBLE);

                btnUndo.setVisibility(View.GONE);
                btnAction.setVisibility(View.GONE);

                btnToggleEdit.setText("Cancel");
                btnToggleEdit.setEnabled(true);

                tvStatus.setText("Tap a shape to select it");
                break;
            }
            case EDITING: {
                toggleGroupMode.setVisibility(View.GONE);
                btnDone.setVisibility(View.GONE);

                int minPts = selectedIndex >= 0 ? minPointsFor(completedItems.get(selectedIndex).type) : 1;
                int curPts = selectedIndex >= 0 ? completedItems.get(selectedIndex).points.size() : 0;
                boolean canUndoPt = curPts > minPts;
                btnUndo.setText("Undo Pt");
                btnUndo.setEnabled(canUndoPt);
                btnUndo.setVisibility(canUndoPt ? View.VISIBLE : View.GONE);

                btnAction.setText("Delete");
                btnAction.setEnabled(true);
                btnAction.setVisibility(View.VISIBLE);

                btnToggleEdit.setText("Done Edit");
                btnToggleEdit.setEnabled(true);

                tvStatus.setText("Drag handles to reposition • Tap midpoint to add • Tap Delete to remove");
                break;
            }
        }
    }

    private String buildDrawingStatus() {
        int n = currentDraft.size();
        String summary = GeometryUtils.summary(completedItems);
        if (n == 0) {
            return completedItems.isEmpty() ? "Tap the map to draw" : summary;
        }
        int min = minPointsFor(activeDrawType);
        String draftInfo = n + " pt" + (n != 1 ? "s" : "") + " in draft"
                + (n < min ? " (need " + min + ")" : " — ready to Add");
        return completedItems.isEmpty() ? draftInfo : draftInfo + "  ·  " + summary;
    }

    // ── Finish ────────────────────────────────────────────────────────────────

    private void finishWithResult() {
        if (pickerMode == PickerMode.EDITING) stopEditing();

        int minPts = minPointsFor(activeDrawType);
        if (pickerMode == PickerMode.DRAWING
                && !currentDraft.isEmpty()
                && !("LABEL".equals(activeDrawType))) {
            if (currentDraft.size() >= minPts) {
                commitDraft();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete draft")
                        .setMessage("Current draft needs " + minPts + " points. Discard it?")
                        .setPositiveButton("Discard & Save", (d, w) -> returnResult())
                        .setNegativeButton("Keep Drawing", null)
                        .show();
                return;
            }
        }
        returnResult();
    }

    private void returnResult() {
        Intent result = new Intent();
        result.putExtra("geometry_value", GeometryUtils.toJson(completedItems));
        setResult(RESULT_OK, result);
        finish();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  mapView.onPause();  }

    // ── Search ────────────────────────────────────────────────────────────────

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
                    Toast.makeText(GeometryPickerActivity.this,
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

    private int dpToPx(int dp) {
        return Math.round(dp * density);
    }

    // ── Inner class: GeometryOverlay ──────────────────────────────────────────

    private class GeometryOverlay extends Overlay {
        final GeometryUtils.GeometryItem item;
        final int color;
        boolean highlighted;
        boolean readOnly;

        GeometryOverlay(GeometryUtils.GeometryItem item, int color) {
            this.item = item;
            this.color = color;
        }

        @Override
        public void draw(Canvas canvas, MapView mv, boolean shadow) {
            if (shadow || item.points.isEmpty()) return;
            switch (item.type) {
                case "POINT":   drawPoint(canvas, mv);   break;
                case "LINE":    drawLine(canvas, mv);    break;
                case "POLYGON": drawPolygon(canvas, mv); break;
                case "LABEL":   drawLabel(canvas, mv);   break;
            }
            if (item.name != null && !item.name.isEmpty()) drawNameLabel(canvas, mv);
        }

        private void drawPoint(Canvas c, MapView mv) {
            android.graphics.Point sp = new android.graphics.Point();
            mv.getProjection().toPixels(item.points.get(0), sp);
            float r = 10f * density;
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(highlighted ? Color.WHITE : color);
            fill.setStyle(Paint.Style.FILL);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setColor(highlighted ? color : Color.WHITE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2.5f * density);
            c.drawCircle(sp.x, sp.y, r, fill);
            c.drawCircle(sp.x, sp.y, r, ring);
        }

        private void drawLine(Canvas c, MapView mv) {
            if (item.points.size() < 2) return;
            Projection proj = mv.getProjection();
            android.graphics.Point sp = new android.graphics.Point();
            Path path = new Path();
            proj.toPixels(item.points.get(0), sp);
            path.moveTo(sp.x, sp.y);
            for (int i = 1; i < item.points.size(); i++) {
                proj.toPixels(item.points.get(i), sp);
                path.lineTo(sp.x, sp.y);
            }
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setColor(highlighted ? Color.WHITE : color);
            lp.setStyle(Paint.Style.STROKE);
            lp.setStrokeWidth(6f * density);
            lp.setStrokeJoin(Paint.Join.ROUND);
            lp.setStrokeCap(Paint.Cap.ROUND);
            if (readOnly) lp.setAlpha(120);
            c.drawPath(path, lp);

            if (item.arrow) {
                android.graphics.Point p1 = new android.graphics.Point();
                android.graphics.Point p2 = new android.graphics.Point();
                proj.toPixels(item.points.get(item.points.size() - 2), p1);
                proj.toPixels(item.points.get(item.points.size() - 1), p2);
                drawArrowhead(c, p1.x, p1.y, p2.x, p2.y,
                        highlighted ? Color.WHITE : color, readOnly ? 120 : 255);
            }
        }

        private void drawArrowhead(Canvas c, float x1, float y1, float x2, float y2,
                                   int arrowColor, int alpha) {
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) return;
            dx /= len; dy /= len;
            float al = 22f * density, aw = 11f * density;
            float ax = x2 - dx * al, ay = y2 - dy * al;
            float px = -dy, py = dx;
            Path arrow = new Path();
            arrow.moveTo(x2, y2);
            arrow.lineTo(ax + px * aw, ay + py * aw);
            arrow.lineTo(ax - px * aw, ay - py * aw);
            arrow.close();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(arrowColor);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(alpha);
            c.drawPath(arrow, p);
        }

        private void drawPolygon(Canvas c, MapView mv) {
            if (item.points.size() < 3) return;
            Projection proj = mv.getProjection();
            android.graphics.Point sp = new android.graphics.Point();
            Path path = new Path();
            proj.toPixels(item.points.get(0), sp);
            path.moveTo(sp.x, sp.y);
            for (int i = 1; i < item.points.size(); i++) {
                proj.toPixels(item.points.get(i), sp);
                path.lineTo(sp.x, sp.y);
            }
            path.close();
            int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(highlighted
                    ? Color.argb(100, 255, 255, 255)
                    : Color.argb(readOnly ? 30 : 60, r, g, b));
            fill.setStyle(Paint.Style.FILL);
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(highlighted ? Color.WHITE : color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(5f * density);
            if (readOnly) stroke.setAlpha(120);
            c.drawPath(path, fill);
            c.drawPath(path, stroke);
        }

        private void drawLabel(Canvas c, MapView mv) {
            if (item.text == null || item.text.isEmpty()) return;
            android.graphics.Point sp = new android.graphics.Point();
            mv.getProjection().toPixels(item.points.get(0), sp);

            float ts = 13f * density;
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(highlighted ? Color.WHITE : color);
            textPaint.setTextSize(ts);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            if (readOnly) textPaint.setAlpha(160);

            float tw = textPaint.measureText(item.text);
            float pad = 5f * density;
            float rl = sp.x + 14f * density, rt = sp.y - ts - pad;
            float rr = rl + tw + pad * 2, rb = sp.y + pad * 0.5f;

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(highlighted
                    ? Color.argb(220, 50, 50, 50)
                    : Color.argb(readOnly ? 160 : 210, 255, 255, 255));
            bgPaint.setStyle(Paint.Style.FILL);
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(highlighted ? Color.WHITE : color);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1.5f * density);
            if (readOnly) borderPaint.setAlpha(120);

            float cr = 4f * density;
            c.drawRoundRect(rl, rt, rr, rb, cr, cr, bgPaint);
            c.drawRoundRect(rl, rt, rr, rb, cr, cr, borderPaint);
            c.drawText(item.text, rl + pad, sp.y - pad * 0.5f, textPaint);

            // Connector dot
            Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(highlighted ? Color.WHITE : color);
            dotPaint.setStyle(Paint.Style.FILL);
            if (readOnly) dotPaint.setAlpha(160);
            c.drawCircle(sp.x, sp.y, 5f * density, dotPaint);
        }

        private void drawNameLabel(Canvas c, MapView mv) {
            GeoPoint centroid = computeCentroid();
            if (centroid == null) return;
            android.graphics.Point sp = new android.graphics.Point();
            mv.getProjection().toPixels(centroid, sp);

            float ts = 11f * density;
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(ts);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            if (readOnly) textPaint.setAlpha(180);

            float tw = textPaint.measureText(item.name);
            float pad = 4f * density;
            float rl = sp.x - tw / 2 - pad;
            float rt = sp.y - ts - pad * 2;
            float rr = sp.x + tw / 2 + pad;
            float rb = sp.y - pad * 0.5f;

            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.argb(readOnly ? 160 : 210, 30, 30, 30));
            bgPaint.setStyle(Paint.Style.FILL);

            float cr = 4f * density;
            c.drawRoundRect(rl, rt, rr, rb, cr, cr, bgPaint);
            c.drawText(item.name, rl + pad, sp.y - pad * 1.5f, textPaint);
        }

        private GeoPoint computeCentroid() {
            if (item.points.isEmpty()) return null;
            double lat = 0, lng = 0;
            for (GeoPoint pt : item.points) { lat += pt.getLatitude(); lng += pt.getLongitude(); }
            return new GeoPoint(lat / item.points.size(), lng / item.points.size());
        }
    }

    // ── Inner class: DragHandlesOverlay ───────────────────────────────────────

    private class DragHandlesOverlay extends Overlay {
        private final GeometryUtils.GeometryItem item;
        private int draggingIndex = -1;

        DragHandlesOverlay(GeometryUtils.GeometryItem item) {
            this.item = item;
        }

        @Override
        public void draw(Canvas canvas, MapView mv, boolean shadow) {
            if (shadow) return;
            Projection proj = mv.getProjection();
            android.graphics.Point sp = new android.graphics.Point();
            float hr = 10f * density, mr = 6f * density;

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(Color.WHITE); fill.setStyle(Paint.Style.FILL);
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setColor(0xFF333333); stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2f * density);
            Paint midFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            midFill.setColor(0xCCFFFFFF); midFill.setStyle(Paint.Style.FILL);

            for (GeoPoint pt : item.points) {
                proj.toPixels(pt, sp);
                canvas.drawCircle(sp.x, sp.y, hr, fill);
                canvas.drawCircle(sp.x, sp.y, hr, stroke);
            }

            boolean hasSegments = "LINE".equals(item.type) || "POLYGON".equals(item.type);
            if (hasSegments && item.points.size() >= 2) {
                int n = item.points.size();
                int segments = "POLYGON".equals(item.type) ? n : n - 1;
                for (int i = 0; i < segments; i++) {
                    GeoPoint a = item.points.get(i);
                    GeoPoint b = item.points.get((i + 1) % n);
                    GeoPoint mid = new GeoPoint(
                            (a.getLatitude()  + b.getLatitude())  / 2,
                            (a.getLongitude() + b.getLongitude()) / 2);
                    proj.toPixels(mid, sp);
                    canvas.drawCircle(sp.x, sp.y, mr, midFill);
                    canvas.drawCircle(sp.x, sp.y, mr, stroke);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e, MapView mv) {
            float x = e.getX(), y = e.getY();
            float hr = 28f * density, mr = 22f * density;
            Projection proj = mv.getProjection();
            android.graphics.Point sp = new android.graphics.Point();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    for (int i = 0; i < item.points.size(); i++) {
                        proj.toPixels(item.points.get(i), sp);
                        if (dist(x, y, sp.x, sp.y) < hr) {
                            draggingIndex = i;
                            return true;
                        }
                    }
                    boolean hasSegs = "LINE".equals(item.type) || "POLYGON".equals(item.type);
                    if (hasSegs && item.points.size() >= 2) {
                        int n = item.points.size();
                        int segs = "POLYGON".equals(item.type) ? n : n - 1;
                        for (int i = 0; i < segs; i++) {
                            GeoPoint a = item.points.get(i);
                            GeoPoint b = item.points.get((i + 1) % n);
                            GeoPoint mid = new GeoPoint(
                                    (a.getLatitude() + b.getLatitude()) / 2,
                                    (a.getLongitude() + b.getLongitude()) / 2);
                            proj.toPixels(mid, sp);
                            if (dist(x, y, sp.x, sp.y) < mr) {
                                item.points.add(i + 1, new GeoPoint(
                                        mid.getLatitude(), mid.getLongitude()));
                                draggingIndex = i + 1;
                                mv.invalidate();
                                updateUI();
                                return true;
                            }
                        }
                    }
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (draggingIndex >= 0 && draggingIndex < item.points.size()) {
                        GeoPoint newPt = (GeoPoint) proj.fromPixels((int) x, (int) y);
                        item.points.set(draggingIndex, newPt);
                        mv.invalidate();
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (draggingIndex >= 0) { draggingIndex = -1; return true; }
                    return false;
            }
            return false;
        }

        private float dist(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2, dy = y1 - y2;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }
}
