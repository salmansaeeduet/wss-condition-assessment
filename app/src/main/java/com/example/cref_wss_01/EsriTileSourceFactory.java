package com.example.cref_wss_01;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

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
}
