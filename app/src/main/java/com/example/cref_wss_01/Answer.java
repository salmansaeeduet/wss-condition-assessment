package com.example.cref_wss_01;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(primaryKeys = {"surveyId", "questionId"},
        foreignKeys = @ForeignKey(entity = Survey.class,
                                  parentColumns = "id",
                                  childColumns = "surveyId",
                                  onDelete = ForeignKey.CASCADE),
        indices = {@Index("surveyId")})
public class Answer {
    public long surveyId;
    public int questionId;

    public String answerValue;
    public String remarks;
}
