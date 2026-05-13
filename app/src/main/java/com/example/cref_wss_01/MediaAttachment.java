package com.example.cref_wss_01;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(foreignKeys = @ForeignKey(entity = Answer.class,
        parentColumns = {"surveyId", "questionId"},
        childColumns = {"surveyId", "questionId"},
        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"surveyId", "questionId"})})
public class MediaAttachment {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long surveyId;
    public int questionId;

    public String uri;
    public String filePath; // To store the absolute file path for thumbnail generation
    public String mediaType; // "PHOTO", "VIDEO", or "AUDIO"
}