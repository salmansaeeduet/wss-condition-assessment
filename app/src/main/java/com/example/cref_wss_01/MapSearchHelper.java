package com.example.cref_wss_01;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class MapSearchHelper {

    static class Result {
        final String name;
        final GeoPoint center;
        final BoundingBox bbox; // null for point-only results

        Result(String name, GeoPoint center, BoundingBox bbox) {
            this.name = name;
            this.center = center;
            this.bbox = bbox;
        }
    }

    interface Callback {
        void onResults(List<Result> results);
        void onError(String msg);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void search(String query, Executor executor, Callback callback) {
        executor.execute(() -> {
            try {
                String q = URLEncoder.encode(query.trim(), "UTF-8");
                String url = "https://nominatim.openstreetmap.org/search?q=" + q
                        + "&format=json&limit=5&addressdetails=0";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "WSS-Condition-Assessment/1.2");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() != 200) {
                    MAIN.post(() -> callback.onError("HTTP " + conn.getResponseCode()));
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }

                JSONArray arr = new JSONArray(sb.toString());
                List<Result> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    double lat = o.getDouble("lat");
                    double lon = o.getDouble("lon");
                    String name = o.getString("display_name");

                    BoundingBox box = null;
                    JSONArray bb = o.optJSONArray("boundingbox");
                    if (bb != null && bb.length() == 4) {
                        // Nominatim boundingbox: [minLat, maxLat, minLon, maxLon]
                        box = new BoundingBox(
                                bb.getDouble(1), bb.getDouble(3),
                                bb.getDouble(0), bb.getDouble(2));
                    }
                    list.add(new Result(name, new GeoPoint(lat, lon), box));
                }
                List<Result> out = list;
                MAIN.post(() -> callback.onResults(out));
            } catch (Exception e) {
                String msg = e.getMessage();
                MAIN.post(() -> callback.onError(msg != null ? msg : "Unknown error"));
            }
        });
    }
}
