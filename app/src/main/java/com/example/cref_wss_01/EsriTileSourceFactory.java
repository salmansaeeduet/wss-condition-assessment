package com.example.cref_wss_01;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

public class EsriTileSourceFactory {

    public static final String NAME = "ESRI World Imagery";

    public static OnlineTileSourceBase create() {
        return new OnlineTileSourceBase(NAME, 0, 19, 256, "", new String[]{}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "/"
                        + MapTileIndex.getX(pMapTileIndex);
            }
        };
    }

    /**
     * Adds a transparent label overlay (CartoDB dark_only_labels @2x) on top of the satellite
     * base layer. White text on a transparent background — designed for dark/satellite maps.
     * @2x tiles are 512 px so text has twice the pixel density compared to standard 256 px tiles.
     */
    public static void addLabelOverlay(MapView mapView, Context context) {
        OnlineTileSourceBase labelSource = new OnlineTileSourceBase(
                "CartoDB Labels", 0, 19, 512, ".png", new String[]{
                "https://a.basemaps.cartocdn.com",
                "https://b.basemaps.cartocdn.com",
                "https://c.basemaps.cartocdn.com",
        }) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl() + "/dark_only_labels/"
                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + MapTileIndex.getX(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "@2x.png";
            }
        };
        TilesOverlay overlay = new TilesOverlay(
                new MapTileProviderBasic(context, labelSource), context);
        overlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        overlay.setLoadingLineColor(Color.TRANSPARENT);
        mapView.getOverlays().add(overlay);
    }
}
