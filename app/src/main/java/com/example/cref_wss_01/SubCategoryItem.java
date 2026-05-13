package com.example.cref_wss_01;

import java.util.ArrayList;
import java.util.List;

public class SubCategoryItem extends NavItem {
    private final String name;
    private final List<QuestionItem> questions = new ArrayList<>();
    public boolean isExpanded = false;

    public SubCategoryItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<QuestionItem> getQuestions() {
        return questions;
    }

    @Override
    public int getType() {
        return TYPE_SUB_CATEGORY;
    }
}
