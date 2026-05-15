package com.example.cref_wss_01;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class SurveyAdapter extends RecyclerView.Adapter<SurveyAdapter.ViewHolder> {

    private List<SurveyWithAnswers> surveys;
    private final int totalQuestions;
    private final int surveyTitleQuestionId;
    private final String surveyTitleAnswerType;
    private final OnDeleteClickListener deleteListener;
    private final OnSurveyClickListener listener;
    private final OnExportClickListener exportListener;
    private final OnShareClickListener shareListener;

    public interface OnShareClickListener { void onShareClick(Survey survey); }
    public interface OnDeleteClickListener { void onDeleteClick(Survey survey); }
    public interface OnSurveyClickListener { void onSurveyClick(Survey survey); }
    public interface OnExportClickListener { void onExportClick(SurveyWithAnswers surveyWithAnswers); }

    public SurveyAdapter(List<SurveyWithAnswers> surveys, int totalQuestions,
                         int surveyTitleQuestionId, String surveyTitleAnswerType,
                         OnSurveyClickListener listener,
                         OnExportClickListener exportListener,
                         OnDeleteClickListener deleteListener,
                         OnShareClickListener shareListener) {
        this.surveys = surveys;
        this.totalQuestions = totalQuestions;
        this.surveyTitleQuestionId = surveyTitleQuestionId;
        this.surveyTitleAnswerType = surveyTitleAnswerType != null ? surveyTitleAnswerType : "";
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
                String raw = answer.answerValue;
                surveyTitle = "COMPOUND".equals(surveyTitleAnswerType)
                        ? RequiredField.extractFirstCompoundField(raw) : raw;
                if (surveyTitle.isEmpty()) surveyTitle = "Unnamed Survey";
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
            String name = getDisplayName(holder.itemView.getContext(), survey.lastExportedZipPath);
            if (!name.isEmpty()) {
                holder.exportedFileText.setText("Last export: " + name);
                holder.exportedFileText.setVisibility(View.VISIBLE);
            } else {
                holder.exportedFileText.setVisibility(View.GONE);
            }
        } else {
            holder.shareButton.setVisibility(View.GONE);
            holder.exportedFileText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return surveys.size();
    }

    private static String getDisplayName(Context ctx, String storedUri) {
        if (storedUri == null || storedUri.isEmpty()) return "";
        Uri uri = Uri.parse(storedUri);
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } catch (Exception ignored) {}
        }
        String path = uri.getPath();
        return path != null ? new File(path).getName() : "";
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView wssName;
        TextView surveyDate;
        TextView completionText;
        TextView exportedFileText;
        RelativeLayout cardBackground;
        ImageView deleteButton;
        ImageView exportButton;
        ImageView shareButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            wssName = itemView.findViewById(R.id.wss_name_text);
            surveyDate = itemView.findViewById(R.id.survey_date_text);
            completionText = itemView.findViewById(R.id.completion_text);
            exportedFileText = itemView.findViewById(R.id.exported_file_text);
            cardBackground = itemView.findViewById(R.id.card_background);
            exportButton = itemView.findViewById(R.id.export_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            shareButton = itemView.findViewById(R.id.share_button);
        }
    }
}
