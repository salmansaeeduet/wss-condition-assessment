package com.example.cref_wss_01;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OfflineMapAreaDao {
    @Insert
    void insert(OfflineMapArea area);

    @Delete
    void delete(OfflineMapArea area);

    @Query("SELECT * FROM offline_map_areas ORDER BY downloadedAt DESC")
    List<OfflineMapArea> getAll();
}
