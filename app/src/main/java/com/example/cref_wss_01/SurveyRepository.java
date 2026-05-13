package com.example.cref_wss_01;

import android.app.Application;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;public class SurveyRepository {

    private final SurveyDao surveyDao;
    private final AnswerDao answerDao;
    private final MediaAttachmentDao mediaAttachmentDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SurveyRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        surveyDao = db.surveyDao();
        answerDao = db.answerDao();
        mediaAttachmentDao = db.mediaAttachmentDao();
    }

    // --- Survey Methods ---
    public void getAllSurveysWithAnswers(java.util.function.Consumer<List<SurveyWithAnswers>> callback) {
        executor.execute(() -> callback.accept(surveyDao.getAllSurveysWithAnswers()));
    }

    public void insertSurvey(Survey survey, java.util.function.Consumer<Long> callback) {
        executor.execute(() -> callback.accept(surveyDao.insert(survey)));
    }

    // --- Answer Methods (UPSERT Logic) ---
    public void upsertAnswer(Answer answer, @Nullable Runnable onComplete) {
        executor.execute(() -> {
            long id = answerDao.insert(answer);
            if (id == -1) { // -1 means insert failed due to conflict, so we update
                answerDao.update(answer);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void getAnswer(long surveyId, int questionId, java.util.function.Consumer<Answer> callback) {
        executor.execute(() -> callback.accept(answerDao.getAnswer(surveyId, questionId)));
    }

    public void getAnswersForSurvey(long surveyId, java.util.function.Consumer<List<Answer>> callback) {
        executor.execute(() -> callback.accept(answerDao.getAnswersForSurvey(surveyId)));
    }

    // --- MediaAttachment Methods ---
    public void saveMediaAttachment(MediaAttachment attachment, Runnable onComplete) {
        executor.execute(() -> {
            mediaAttachmentDao.insert(attachment);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    void deleteSurvey(Survey survey, Runnable onComplete) {
        executor.execute(() -> {
            surveyDao.delete(survey);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void updateSurvey(Survey survey, Runnable onComplete) {
        executor.execute(() -> {
            surveyDao.update(survey);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }


    public void deleteMediaAttachment(MediaAttachment attachment) {
        executor.execute(() -> mediaAttachmentDao.delete(attachment));
    }

    public void getAllPreviousAnswersForQuestion(long surveyId, int questionId, java.util.function.Consumer<List<String>> callback) {
        executor.execute(() -> callback.accept(answerDao.getAllPreviousAnswersForQuestion(questionId, surveyId)));
    }

    public void getAttachmentsForQuestion(long surveyId, int questionId, java.util.function.Consumer<List<MediaAttachment>> callback) {
        executor.execute(() -> callback.accept(mediaAttachmentDao.getAttachmentsForQuestion(surveyId, questionId)));
    }

    public void getSurveyWithAnswersAndAttachments(long surveyId, java.util.function.Consumer<SurveyWithAnswers> callback) {
        executor.execute(() -> callback.accept(surveyDao.getSurveyWithAnswersAndAttachments(surveyId)));
    }
}