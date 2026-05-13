package com.example.cref_wss_01;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MediaAttachmentDao {
    @Insert
    void insert(MediaAttachment attachment);

    @Delete
    void delete(MediaAttachment attachment);

    @Query("SELECT * FROM MediaAttachment WHERE surveyId = :surveyId AND questionId = :questionId")
    List<MediaAttachment> getAttachmentsForQuestion(long surveyId, int questionId);

    @Query("SELECT * FROM MediaAttachment WHERE surveyId = :surveyId")
    List<MediaAttachment> getAttachmentsForSurvey(long surveyId);
}
