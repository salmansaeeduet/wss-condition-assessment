package com.example.cref_wss_01;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SurveyMapActivity extends AppCompatActivity {

    private static final double DEFAULT_LAT  = 28.5;
    private static final double DEFAULT_LNG  = 67.5;
    private static final double DEFAULT_ZOOM = 7.0;

    private MapView mapView;
    private long surveyId;
    private float density;

    // All geometry items collected from answers + attachments, after global renumbering.
    // The canvas overlay draws POINT circles and name chips from these lists.
    private final List<GeometryUtils.GeometryItem> canvasItems  = new ArrayList<>();
    private final List<Integer>                    canvasColors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_survey_map);

        surveyId = getIntent().getLongExtra("SURVEY_ID", -1);
        density  = getResources().getDisplayMetrics().density;

        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(EsriTileSourceFactory.create());
        EsriTileSourceFactory.addLabelOverlay(mapView, this);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        mapView.getController().setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));
        mapView.getController().setZoom(DEFAULT_ZOOM);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        final int hintOriginalBottom = (int)(16 * density);
        android.view.View topBar = findViewById(R.id.topBar);
        android.view.View tvHint = findViewById(R.id.tvHint);
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(tvHint, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = hintOriginalBottom + navBar;
            v.setLayoutParams(lp);
            return insets;
        });

        if (surveyId != -1) loadAndRender();
    }

    private void loadAndRender() {
        Executors.newSingleThreadExecutor().execute(() -> {
            Map<Integer, Question> questionMap = buildQuestionMap();
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Answer> answers = db.answerDao().getAnswersForSurvey(surveyId);
            List<MediaAttachment> attachments = db.mediaAttachmentDao().getAttachmentsForSurvey(surveyId);

            runOnUiThread(() -> {
                List<GeoPoint> allPoints = new ArrayList<>();

                // ── Phase 1: collect all geometry items ──────────────────────
                for (Answer answer : answers) {
                    if (answer.answerValue == null || answer.answerValue.isEmpty()) continue;
                    Question q = questionMap.get(answer.questionId);
                    if (q == null) continue;
                    String qt = q.getAnswerType();
                    if (!"GEOMETRY".equals(qt) && !"LINE".equals(qt) && !"POLYGON".equals(qt)) continue;
                    int color = GeometryUtils.colorForQuestion(q.getId());
                    for (GeometryUtils.GeometryItem item : GeometryUtils.fromJson(answer.answerValue)) {
                        canvasItems.add(item);
                        canvasColors.add(color);
                    }
                }
                for (MediaAttachment att : attachments) {
                    if (!"GEOMETRY".equals(att.mediaType) || att.filePath == null) continue;
                    try {
                        String json = new String(java.nio.file.Files.readAllBytes(
                                java.nio.file.Paths.get(att.filePath)));
                        int color = GeometryUtils.colorForQuestion(att.questionId);
                        for (GeometryUtils.GeometryItem item : GeometryUtils.fromJson(json)) {
                            canvasItems.add(item);
                            canvasColors.add(color);
                        }
                    } catch (java.io.IOException ignored) {}
                }

                // ── Phase 2: renumber auto-generated names globally per type ─
                renumberAutoGeomNames(canvasItems);

                // ── Phase 3: render ──────────────────────────────────────────

                // LOCATION answers — explicit single-point picks, keep the drop-pin style
                for (Answer answer : answers) {
                    if (answer.answerValue == null || answer.answerValue.isEmpty()) continue;
                    Question q = questionMap.get(answer.questionId);
                    if (q == null || !"LOCATION".equals(q.getAnswerType())) continue;
                    renderLocation(answer.answerValue, q.getQuestionText(),
                            GeometryUtils.colorForQuestion(q.getId()), allPoints);
                }

                // Geometry items — LINE/POLYGON use osmdroid overlays for hit testing;
                // POINT circles are drawn by the canvas overlay below.
                for (int i = 0; i < canvasItems.size(); i++) {
                    GeometryUtils.GeometryItem item = canvasItems.get(i);
                    int color = canvasColors.get(i);
                    allPoints.addAll(item.points);
                    String label = item.name != null ? item.name : "Shape";
                    switch (item.type) {
                        case "LINE":    renderLine(item, color, label);    break;
                        case "POLYGON": renderPolygon(item, color, label); break;
                        case "LABEL":   renderLegacyLabel(item, label);   break;
                    }
                }

                // Canvas overlay: draws POINT circles + name chips + handles POINT taps
                mapView.getOverlays().add(buildCanvasOverlay());

                if (allPoints.isEmpty()) {
                    Toast.makeText(this, "No location or geometry answers in this survey yet.",
                            Toast.LENGTH_LONG).show();
                } else {
                    try {
                        BoundingBox bb = BoundingBox.fromGeoPoints(allPoints);
                        mapView.post(() -> mapView.zoomToBoundingBox(bb, true, 80));
                    } catch (Exception ignored) {}
                }
                mapView.invalidate();
            });
        });
    }

    // ── Renumber ─────────────────────────────────────────────────────────────

    private static void renumberAutoGeomNames(List<GeometryUtils.GeometryItem> items) {
        Map<String, Integer> counters = new HashMap<>();
        for (GeometryUtils.GeometryItem item : items) {
            if (!isAutoGeneratedName(item.name, item.type)) continue;
            String prefix = typePrefix(item.type);
            int n = counters.getOrDefault(item.type, 0) + 1;
            counters.put(item.type, n);
            item.name = prefix + " " + n;
        }
    }

    private static boolean isAutoGeneratedName(String name, String type) {
        if (name == null || name.isEmpty()) return false;
        return name.matches(typePrefix(type) + " \\d+");
    }

    private static String typePrefix(String type) {
        switch (type) {
            case "POINT":   return "Point";
            case "LINE":    return "Line";
            case "POLYGON": return "Polygon";
            case "LABEL":   return "Label";
            default:        return type;
        }
    }

    // ── Renderers ────────────────────────────────────────────────────────────

    private void renderLocation(String value, String label, int color, List<GeoPoint> allPts) {
        String[] parts = value.split("\\|", -1);
        if (parts.length < 2) return;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            GeoPoint pt = new GeoPoint(lat, lng);
            allPts.add(pt);
            Marker m = new Marker(mapView);
            m.setPosition(pt);
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle(label);
            m.setInfoWindow(null);
            m.setOnMarkerClickListener((mk, mv) -> {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
                return true;
            });
            mapView.getOverlays().add(m);
        } catch (NumberFormatException ignored) {}
    }

    private void renderLine(GeometryUtils.GeometryItem item, int color, String label) {
        if (item.points.size() < 2) return;
        Polyline pl = new Polyline(mapView);
        pl.setPoints(item.points);
        pl.getOutlinePaint().setColor(color);
        pl.getOutlinePaint().setStrokeWidth(6f);
        pl.setOnClickListener((p, mv, e) -> {
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
            return true;
        });
        mapView.getOverlays().add(pl);
    }

    private void renderPolygon(GeometryUtils.GeometryItem item, int color, String label) {
        if (item.points.size() < 3) return;
        Polygon pg = new Polygon(mapView);
        pg.setPoints(item.points);
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        pg.getFillPaint().setColor(Color.argb(60, r, g, b));
        pg.getOutlinePaint().setColor(color);
        pg.getOutlinePaint().setStrokeWidth(5f);
        pg.setOnClickListener((p, mv, e) -> {
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
            return true;
        });
        mapView.getOverlays().add(pg);
    }

    private void renderLegacyLabel(GeometryUtils.GeometryItem item, String label) {
        if (item.points.isEmpty() || item.text == null) return;
        Marker m = new Marker(mapView);
        m.setPosition(item.points.get(0));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(item.text);
        m.setInfoWindow(null);
        m.setOnMarkerClickListener((mk, mv) -> {
            Toast.makeText(this, item.text, Toast.LENGTH_SHORT).show();
            return true;
        });
        mapView.getOverlays().add(m);
    }

    // ── Canvas overlay ───────────────────────────────────────────────────────

    private Overlay buildCanvasOverlay() {
        float d = density;
        Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint ringPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint bgPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

        ringPaint.setColor(Color.WHITE);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.5f * d);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(11f * d);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        bgPaint.setColor(Color.argb(210, 30, 30, 30));
        bgPaint.setStyle(Paint.Style.FILL);

        return new Overlay() {
            @Override
            public void draw(Canvas canvas, MapView mv, boolean shadow) {
                if (shadow) return;
                android.graphics.Point sp = new android.graphics.Point();
                float r   = 10f * d;
                float ts  = 11f * d;
                float pad = 4f  * d;
                float cr  = 4f  * d;

                for (int i = 0; i < canvasItems.size(); i++) {
                    GeometryUtils.GeometryItem item = canvasItems.get(i);
                    if (item.points.isEmpty()) continue;

                    // Draw circle for POINT items
                    if ("POINT".equals(item.type)) {
                        mv.getProjection().toPixels(item.points.get(0), sp);
                        fillPaint.setColor(canvasColors.get(i));
                        fillPaint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(sp.x, sp.y, r, fillPaint);
                        canvas.drawCircle(sp.x, sp.y, r, ringPaint);
                    }

                    // Draw name chip for all named items
                    if (item.name == null || item.name.isEmpty()) continue;
                    double lat = 0, lng = 0;
                    for (GeoPoint pt : item.points) {
                        lat += pt.getLatitude();
                        lng += pt.getLongitude();
                    }
                    GeoPoint centroid = new GeoPoint(lat / item.points.size(),
                                                     lng / item.points.size());
                    mv.getProjection().toPixels(centroid, sp);
                    float tw = textPaint.measureText(item.name);
                    float rl = sp.x - tw / 2 - pad;
                    float rt = sp.y - ts - pad * 2;
                    float rr = sp.x + tw / 2 + pad;
                    float rb = sp.y - pad * 0.5f;
                    canvas.drawRoundRect(rl, rt, rr, rb, cr, cr, bgPaint);
                    canvas.drawText(item.name, rl + pad, sp.y - pad * 1.5f, textPaint);
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e, MapView mapView) {
                float tapX = e.getX(), tapY = e.getY();
                float hitR = 22f * d;
                android.graphics.Point sp = new android.graphics.Point();
                for (int i = canvasItems.size() - 1; i >= 0; i--) {
                    GeometryUtils.GeometryItem item = canvasItems.get(i);
                    if (!"POINT".equals(item.type) || item.points.isEmpty()) continue;
                    mapView.getProjection().toPixels(item.points.get(0), sp);
                    float dx = tapX - sp.x, dy = tapY - sp.y;
                    if (dx * dx + dy * dy <= hitR * hitR) {
                        Toast.makeText(SurveyMapActivity.this,
                                item.name != null ? item.name : "Point",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                return false;
            }
        };
    }

    // ── Question map ─────────────────────────────────────────────────────────

    private Map<Integer, Question> buildQuestionMap() {
        Map<Integer, Question> map = new LinkedHashMap<>();
        List<CategoryItem> cats = QuestionnaireParser.parseHierarchical(
                this, getString(R.string.questionnaire_file), getString(R.string.questionnaire_sheet));
        for (CategoryItem cat : cats)
            for (SubCategoryItem sub : cat.getSubCategories())
                for (QuestionItem qi : sub.getQuestions()) {
                    Question q = qi.getQuestion();
                    map.put(q.getId(), q);
                    for (Question subQ : q.getSubQuestions())
                        map.put(subQ.getId(), subQ);
                }
        return map;
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  mapView.onPause();  }
}
