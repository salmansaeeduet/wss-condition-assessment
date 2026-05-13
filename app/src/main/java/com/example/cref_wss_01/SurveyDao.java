package com.example.cref_wss_01;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SurveyDao {
    @Transaction
    @Query("SELECT * FROM Survey ORDER BY surveyDate DESC")
    List<SurveyWithAnswers> getAllSurveysWithAnswers();

    @Insert
    long insert(Survey survey);
    @Transaction
    @Query("SELECT * FROM Survey WHERE id = :surveyId")
    SurveyWithAnswers getSurveyWithAnswersAndAttachments(long surveyId);

    @Delete
    void delete(Survey survey);

    @Update
    void update(Survey survey);


}
