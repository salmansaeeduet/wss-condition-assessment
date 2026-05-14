package com.example.cref_wss_01;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_map_areas")
public class OfflineMapArea {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public double north;
    public double south;
    public double east;
    public double west;
    public int zoomMin;
    public int zoomMax;
    public int tileCount;
    public long downloadedAt;
}
