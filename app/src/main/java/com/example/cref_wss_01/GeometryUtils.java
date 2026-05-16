package com.example.cref_wss_01;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class GeometryUtils {

    private static final int[] PALETTE = {
        0xFFE53935, // Red
        0xFF1E88E5, // Blue
        0xFF43A047, // Green
        0xFFFF8F00, // Amber
        0xFF8E24AA, // Purple
        0xFF00ACC1, // Cyan
        0xFFFFB300, // Yellow-Amber
        0xFFD81B60, // Pink
    };

    public static int colorForQuestion(int questionId) {
        return PALETTE[Math.abs(questionId) % PALETTE.length];
    }

    public static class GeometryItem {
        public String type;          // "POINT", "LINE", "POLYGON", "LABEL"
        public List<GeoPoint> points;
        public boolean arrow;        // LINE only: draw arrowhead at last point
        public String text;          // LABEL only
        public String name;          // human-readable item name ("Pump 1")

        public GeometryItem(String type, List<GeoPoint> points) {
            this.type = type;
            this.points = points;
        }
    }

    /** Serialise list of items to JSON: [{"t":"LINE","c":[[lat,lng],...],"arrow":true}, ...] */
    public static String toJson(List<GeometryItem> items) {
        JSONArray outer = new JSONArray();
        for (GeometryItem item : items) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("t", item.type);
                JSONArray coords = new JSONArray();
                for (GeoPoint pt : item.points) {
                    JSONArray p = new JSONArray();
                    p.put(pt.getLatitude());
                    p.put(pt.getLongitude());
                    coords.put(p);
                }
                obj.put("c", coords);
                if (item.arrow) obj.put("arrow", true);
                if (item.text != null && !item.text.isEmpty()) obj.put("text", item.text);
                if (item.name != null && !item.name.isEmpty()) obj.put("n", item.name);
                outer.put(obj);
            } catch (JSONException ignored) {}
        }
        return outer.toString();
    }

    /**
     * Parse JSON to list of GeometryItems. Handles both the new typed format and the legacy
     * format [[[lat,lng],...], ...] (legacy items are assigned type "LINE").
     */
    public static List<GeometryItem> fromJson(String json) {
        List<GeometryItem> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        try {
            JSONArray outer = new JSONArray(json);
            if (outer.length() == 0) return result;

            boolean legacy = !(outer.get(0) instanceof JSONObject);

            for (int i = 0; i < outer.length(); i++) {
                if (legacy) {
                    JSONArray pts = outer.getJSONArray(i);
                    List<GeoPoint> coords = parseCoords(pts);
                    if (!coords.isEmpty()) result.add(new GeometryItem("LINE", coords));
                } else {
                    JSONObject obj = outer.getJSONObject(i);
                    String type = obj.optString("t", "LINE");
                    JSONArray coords = obj.optJSONArray("c");
                    if (coords == null) continue;
                    List<GeoPoint> pts = parseCoords(coords);
                    if (pts.isEmpty()) continue;
                    GeometryItem item = new GeometryItem(type, pts);
                    item.arrow = obj.optBoolean("arrow", false);
                    String text = obj.optString("text", null);
                    item.text = (text != null && !text.isEmpty()) ? text : null;
                    String name = obj.optString("n", null);
                    item.name = (name != null && !name.isEmpty()) ? name : null;
                    result.add(item);
                }
            }
        } catch (JSONException ignored) {}
        return result;
    }

    private static List<GeoPoint> parseCoords(JSONArray coords) throws JSONException {
        List<GeoPoint> pts = new ArrayList<>();
        for (int j = 0; j < coords.length(); j++) {
            JSONArray p = coords.getJSONArray(j);
            pts.add(new GeoPoint(p.getDouble(0), p.getDouble(1)));
        }
        return pts;
    }

    /** Human-readable summary, e.g. "Pump 1, Pump 2, 1 polygon saved" */
    public static String summary(List<GeometryItem> items) {
        if (items.isEmpty()) return "No annotation";
        int unnamedPoints = 0, unnamedLines = 0, unnamedPolygons = 0, unnamedLabels = 0;
        List<String> namedItems = new ArrayList<>();
        for (GeometryItem item : items) {
            if (item.name != null) { namedItems.add(item.name); continue; }
            switch (item.type) {
                case "POINT":   unnamedPoints++;   break;
                case "LINE":    unnamedLines++;    break;
                case "POLYGON": unnamedPolygons++; break;
                case "LABEL":   unnamedLabels++;   break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String n : namedItems) { if (sb.length() > 0) sb.append(", "); sb.append(n); }
        if (unnamedPoints  > 0) { if (sb.length() > 0) sb.append(", "); sb.append(unnamedPoints).append(unnamedPoints == 1 ? " point" : " points"); }
        if (unnamedLines   > 0) { if (sb.length() > 0) sb.append(", "); sb.append(unnamedLines).append(unnamedLines == 1 ? " line" : " lines"); }
        if (unnamedPolygons > 0) { if (sb.length() > 0) sb.append(", "); sb.append(unnamedPolygons).append(unnamedPolygons == 1 ? " polygon" : " polygons"); }
        if (unnamedLabels  > 0) { if (sb.length() > 0) sb.append(", "); sb.append(unnamedLabels).append(unnamedLabels == 1 ? " label" : " labels"); }
        return sb.append(" saved").toString();
    }
}
