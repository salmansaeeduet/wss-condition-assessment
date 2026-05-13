package com.example.cref_wss_01;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Question implements Parcelable {
    private int id;
    private String category;
    private String subCategory;
    private String questionText;
    private String answerType;
    private String answerOptions;
    private String explanation;
    private int parentId;       // 0 = top-level question
    private String triggerValue; // parent answer value that activates this sub-question

    // Not Parcelable — populated by QuestionnaireParser after all rows are read.
    private final List<Question> subQuestions = new ArrayList<>();

    public Question(int id, String category, String subCategory, String questionText,
                    String answerType, String answerOptions, String explanation,
                    int parentId, String triggerValue) {
        this.id = id;
        this.category = category;
        this.subCategory = subCategory;
        this.questionText = questionText;
        this.answerType = answerType;
        this.answerOptions = answerOptions;
        this.explanation = explanation;
        this.parentId = parentId;
        this.triggerValue = triggerValue != null ? triggerValue : "";
    }

    protected Question(Parcel in) {
        id = in.readInt();
        category = in.readString();
        subCategory = in.readString();
        questionText = in.readString();
        answerType = in.readString();
        answerOptions = in.readString();
        explanation = in.readString();
        parentId = in.readInt();
        triggerValue = in.readString();
    }

    public static final Creator<Question> CREATOR = new Creator<Question>() {
        @Override
        public Question createFromParcel(Parcel in) {
            return new Question(in);
        }

        @Override
        public Question[] newArray(int size) {
            return new Question[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(category);
        dest.writeString(subCategory);
        dest.writeString(questionText);
        dest.writeString(answerType);
        dest.writeString(answerOptions);
        dest.writeString(explanation);
        dest.writeInt(parentId);
        dest.writeString(triggerValue);
    }

    public int getId() { return id; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public String getQuestionText() { return questionText; }
    public String getAnswerType() { return answerType; }
    public String getAnswerOptions() { return answerOptions; }
    public String getExplanation() { return explanation; }
    public int getParentId() { return parentId; }
    public String getTriggerValue() { return triggerValue; }
    public List<Question> getSubQuestions() { return subQuestions; }

    public void addSubQuestion(Question sub) {
        subQuestions.add(sub);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question question = (Question) o;
        return id == question.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
