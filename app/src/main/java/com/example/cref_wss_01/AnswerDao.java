package com.example.cref_wss_01;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;import java.util.List;

@Dao
public interface AnswerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Answer answer);

    @Update
    void update(Answer answer);

    @Transaction
    @Query("SELECT * FROM Answer WHERE surveyId = :surveyId")
    List<Answer> getAnswersForSurvey(long surveyId);

    @Query("SELECT * FROM Answer WHERE surveyId = :surveyId AND questionId = :questionId")
    Answer getAnswer(long surveyId, int questionId);

    @Query("SELECT answerValue FROM Answer WHERE questionId = :questionId AND surveyId!= :surveyId AND answerValue IS NOT NULL AND answerValue != ''")List<String> getAllPreviousAnswersForQuestion(int questionId, long surveyId);
}