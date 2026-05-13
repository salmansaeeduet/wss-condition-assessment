package com.example.cref_wss_01;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.List;

public class QuestionPagerAdapter extends FragmentStateAdapter {

    private final List<Question> questions;
    private final long surveyId;

    public QuestionPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                List<Question> questions, long surveyId) {
        super(fragmentActivity);
        this.questions = questions;
        this.surveyId = surveyId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position < questions.size()) {
            Question q = questions.get(position);
            return QuestionFragment.newInstance(q, surveyId, position, q.getSubQuestions());
        } else {
            return new ThankYouFragment();
        }
    }

    @Override
    public int getItemCount() {
        return questions.size() + 1;
    }
}
