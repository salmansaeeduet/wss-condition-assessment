package com.example.cref_wss_01;

public class QuestionItem extends NavItem {
    private final Question question;

    public QuestionItem(Question question) {
        this.question = question;
    }

    public Question getQuestion() {
        return question;
    }

    @Override
    public int getType() {
        return TYPE_QUESTION;
    }
}
