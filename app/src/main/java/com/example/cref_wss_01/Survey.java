package com.example.cref_wss_01;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Survey {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long surveyDate;
    public String lastExportedZipPath;
}
