package com.example.cref_wss_01;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.List;

public class SurveyWithAnswers {
    @Embedded
    public Survey survey;

    @Relation(
        parentColumn = "id",
        entityColumn = "surveyId"
    )
    public List<Answer> answers;
    @Relation(
            parentColumn =  "id",
            entityColumn = "surveyId"
    )
    public List<MediaAttachment> attachments;
}