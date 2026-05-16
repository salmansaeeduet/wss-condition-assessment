package com.example.cref_wss_01;

import android.graphics.Color;
import android.os.Bundle;
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
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_survey_map);

        surveyId = getIntent().getLongExtra("SURVEY_ID", -1);

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

        // Inset top bar below status bar; push hint above nav bar
        final int hintOriginalBottom = (int)(16 * getResources().getDisplayMetrics().density);
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
            // 1. Build questionId → Question map from questionnaire
            Map<Integer, Question> questionMap = buildQuestionMap();

            // 2. Load answers and GEOMETRY attachments from Room DB
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Answer> answers = db.answerDao().getAnswersForSurvey(surveyId);
            List<MediaAttachment> attachments = db.mediaAttachmentDao().getAttachmentsForSurvey(surveyId);

            // 3. Render on main thread
            List<GeoPoint> allPoints = new ArrayList<>();
            runOnUiThread(() -> {
                for (Answer answer : answers) {
                    if (answer.answerValue == null || answer.answerValue.isEmpty()) continue;
                    Question q = questionMap.get(answer.questionId);
                    if (q == null) continue;

                    String qType = q.getAnswerType();
                    int color = GeometryUtils.colorForQuestion(q.getId());
                    String label = q.getQuestionText();

                    switch (qType) {
                        case "LOCATION":
                            renderLocation(answer.answerValue, label, color, allPoints);
                            break;
                        case "GEOMETRY":
                        case "LINE":
                        case "POLYGON":
                            renderGeometry(answer.answerValue, label, color, allPoints);
                            break;
                    }
                }

                // Render geometry sketch attachments
                for (MediaAttachment att : attachments) {
                    if (!"GEOMETRY".equals(att.mediaType) || att.filePath == null) continue;
                    try {
                        String json = new String(java.nio.file.Files.readAllBytes(
                                java.nio.file.Paths.get(att.filePath)));
                        Question q = questionMap.get(att.questionId);
                        String label = q != null ? q.getQuestionText() : "Sketch";
                        int color = GeometryUtils.colorForQuestion(att.questionId);
                        renderGeometry(json, label, color, allPoints);
                    } catch (java.io.IOException ignored) {}
                }

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

    private void renderLocation(String value, String label, int color, List<GeoPoint> allPts) {
        String[] parts = value.split("\\|", -1);
        if (parts.length < 2) return;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            GeoPoint pt = new GeoPoint(lat, lng);
            allPts.add(pt);

            Marker marker = new Marker(mapView);
            marker.setPosition(pt);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(label);
            marker.setInfoWindow(null);
            marker.setOnMarkerClickListener((m, mv) -> {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
                return true;
            });
            mapView.getOverlays().add(marker);
        } catch (NumberFormatException ignored) {}
    }

    private void renderGeometry(String value, String groupLabel, int color, List<GeoPoint> allPts) {
        List<GeometryUtils.GeometryItem> items = GeometryUtils.fromJson(value);
        for (GeometryUtils.GeometryItem item : items) {
            allPts.addAll(item.points);
            String itemLabel = item.name != null ? item.name : groupLabel;
            switch (item.type) {
                case "POINT": {
                    if (item.points.isEmpty()) break;
                    Marker m = new Marker(mapView);
                    m.setPosition(item.points.get(0));
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    m.setTitle(itemLabel);
                    m.setInfoWindow(null);
                    m.setOnMarkerClickListener((mk, mv) -> {
                        Toast.makeText(this, itemLabel, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    mapView.getOverlays().add(m);
                    break;
                }
                case "LINE": {
                    if (item.points.size() < 2) break;
                    Polyline pl = new Polyline(mapView);
                    pl.setPoints(item.points);
                    pl.getOutlinePaint().setColor(color);
                    pl.getOutlinePaint().setStrokeWidth(6f);
                    pl.setOnClickListener((p, mv, e) -> {
                        Toast.makeText(this, itemLabel, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    mapView.getOverlays().add(pl);
                    if (item.name != null) addNameMarker(item, itemLabel, groupLabel);
                    break;
                }
                case "POLYGON": {
                    if (item.points.size() < 3) break;
                    Polygon pg = new Polygon(mapView);
                    pg.setPoints(item.points);
                    int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                    pg.getFillPaint().setColor(Color.argb(60, r, g, b));
                    pg.getOutlinePaint().setColor(color);
                    pg.getOutlinePaint().setStrokeWidth(5f);
                    pg.setOnClickListener((p, mv, e) -> {
                        Toast.makeText(this, itemLabel, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    mapView.getOverlays().add(pg);
                    if (item.name != null) addNameMarker(item, itemLabel, groupLabel);
                    break;
                }
                case "LABEL": {
                    if (item.points.isEmpty() || item.text == null) break;
                    Marker m = new Marker(mapView);
                    m.setPosition(item.points.get(0));
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    m.setTitle(item.text);
                    m.setSnippet(groupLabel);
                    m.setInfoWindow(null);
                    m.setOnMarkerClickListener((mk, mv) -> {
                        Toast.makeText(this, item.text, Toast.LENGTH_SHORT).show();
                        return true;
                    });
                    mapView.getOverlays().add(m);
                    break;
                }
            }
        }
    }

    private void addNameMarker(GeometryUtils.GeometryItem item, String name, String snippet) {
        if (item.points.isEmpty()) return;
        double lat = 0, lng = 0;
        for (GeoPoint pt : item.points) { lat += pt.getLatitude(); lng += pt.getLongitude(); }
        GeoPoint centroid = new GeoPoint(lat / item.points.size(), lng / item.points.size());
        Marker m = new Marker(mapView);
        m.setPosition(centroid);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(name);
        m.setSnippet(snippet);
        m.setIcon(null);
        m.setInfoWindow(null);
        m.setOnMarkerClickListener((mk, mv) -> {
            Toast.makeText(this, name + " (" + snippet + ")", Toast.LENGTH_SHORT).show();
            return true;
        });
        mapView.getOverlays().add(m);
    }

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
