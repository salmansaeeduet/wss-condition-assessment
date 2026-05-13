package com.example.cref_wss_01;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class SurveyAdapter extends RecyclerView.Adapter<SurveyAdapter.ViewHolder> {

    private List<SurveyWithAnswers> surveys;
    private final int totalQuestions;
    private final int surveyTitleQuestionId;
    private final OnDeleteClickListener deleteListener;
    private final OnSurveyClickListener listener;
    private final OnExportClickListener exportListener;
    private final OnShareClickListener shareListener;

    public interface OnShareClickListener { void onShareClick(Survey survey); }
    public interface OnDeleteClickListener { void onDeleteClick(Survey survey); }
    public interface OnSurveyClickListener { void onSurveyClick(Survey survey); }
    public interface OnExportClickListener { void onExportClick(SurveyWithAnswers surveyWithAnswers); }

    public SurveyAdapter(List<SurveyWithAnswers> surveys, int totalQuestions,
                         int surveyTitleQuestionId,
                         OnSurveyClickListener listener,
                         OnExportClickListener exportListener,
                         OnDeleteClickListener deleteListener,
                         OnShareClickListener shareListener) {
        this.surveys = surveys;
        this.totalQuestions = totalQuestions;
        this.surveyTitleQuestionId = surveyTitleQuestionId;
        this.listener = listener;
        this.exportListener = exportListener;
        this.deleteListener = deleteListener;
        this.shareListener = shareListener;
    }

    public void setSurveys(List<SurveyWithAnswers> surveys) {
        this.surveys = surveys;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.survey_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SurveyWithAnswers surveyWithAnswers = surveys.get(position);
        Survey survey = surveyWithAnswers.survey;

        String surveyTitle = "Unnamed Survey";
        for (Answer answer : surveyWithAnswers.answers) {
            if (answer.questionId == surveyTitleQuestionId
                    && answer.answerValue != null && !answer.answerValue.isEmpty()) {
                surveyTitle = answer.answerValue;
                break;
            }
        }
        holder.wssName.setText(surveyTitle);
        holder.surveyDate.setText(DateFormat.getDateTimeInstance().format(new Date(survey.surveyDate)));

        int answeredCount = 0;
        for (Answer answer : surveyWithAnswers.answers) {
            if (answer.answerValue != null && !answer.answerValue.isEmpty()) answeredCount++;
        }

        int completionPercentage = totalQuestions > 0
                ? (int) (((double) answeredCount / totalQuestions) * 100) : 0;
        holder.completionText.setText(completionPercentage + "% Complete");

        int backgroundColor;
        if (completionPercentage == 0) {
            backgroundColor = R.color.status_new;
        } else if (completionPercentage >= 100) {
            backgroundColor = R.color.status_completed;
        } else {
            backgroundColor = R.color.status_in_progress;
        }
        holder.cardBackground.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.getContext(), backgroundColor));

        holder.itemView.setOnClickListener(v -> listener.onSurveyClick(survey));
        holder.exportButton.setOnClickListener(v -> exportListener.onExportClick(surveyWithAnswers));
        holder.deleteButton.setOnClickListener(v -> deleteListener.onDeleteClick(survey));

        if (survey.lastExportedZipPath != null && !survey.lastExportedZipPath.isEmpty()) {
            holder.shareButton.setVisibility(View.VISIBLE);
            holder.shareButton.setOnClickListener(v -> { if (shareListener != null) shareListener.onShareClick(survey); });
        } else {
            holder.shareButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return surveys.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView wssName;
        TextView surveyDate;
        TextView completionText;
        RelativeLayout cardBackground;
        ImageView deleteButton;
        ImageView exportButton;
        ImageView shareButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            wssName = itemView.findViewById(R.id.wss_name_text);
            surveyDate = itemView.findViewById(R.id.survey_date_text);
            completionText = itemView.findViewById(R.id.completion_text);
            cardBackground = itemView.findViewById(R.id.card_background);
            exportButton = itemView.findViewById(R.id.export_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            shareButton = itemView.findViewById(R.id.share_button);
        }
    }
}
