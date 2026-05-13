package com.example.cref_wss_01;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ExpandableNavAdapter.OnQuestionClickListener, OnAnswerListener, ThankYouFragment.OnCompletionActionsListener {

    private DrawerLayout drawerLayout;
    private ViewPager2 viewPager;
    private final List<Question> flatQuestionList = new ArrayList<>();
    private long surveyId;
    private SurveyRepository repository;
    private RecyclerView navRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surveyId = getIntent().getLongExtra("SURVEY_ID", -1);
        if (surveyId == -1) {
            finish();
            return;
        }

        repository = new SurveyRepository(getApplication());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        List<CategoryItem> categoryItems = QuestionnaireParser.parseHierarchical(this, getString(R.string.questionnaire_file), getString(R.string.questionnaire_sheet));

        for (CategoryItem category : categoryItems) {
            for (SubCategoryItem subCategory : category.getSubCategories()) {
                for (QuestionItem questionItem : subCategory.getQuestions()) {
                    flatQuestionList.add(questionItem.getQuestion());
                }
            }
        }

        viewPager = findViewById(R.id.view_pager);
        QuestionPagerAdapter questionAdapter = new QuestionPagerAdapter(this, flatQuestionList, surveyId);
        viewPager.setAdapter(questionAdapter);

        // Check if we need to jump to a specific question
        int jumpToQuestionId = getIntent().getIntExtra("JUMP_TO_QUESTION", -1);
        if (jumpToQuestionId != -1) {
            for (int i = 0; i < flatQuestionList.size(); i++) {
                if (flatQuestionList.get(i).getId() == jumpToQuestionId) {
                    viewPager.setCurrentItem(i, false); // false for immediate jump
                    break;
                }
            }
        }

        navRecyclerView = navigationView.findViewById(R.id.nav_recycler_view);
        navRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNavDrawer();
    }

    @Override
    public void onQuestionClick(Question question) {
        int position = flatQuestionList.indexOf(question);
        if (position != -1) {
            viewPager.setCurrentItem(position, true);
        }
        drawerLayout.closeDrawer(GravityCompat.START);
    }
    public void goToNextQuestion() {
        int currentItem = viewPager.getCurrentItem();
        if (currentItem < flatQuestionList.size()) {
            viewPager.setCurrentItem(currentItem + 1);
        }
    }

    public void goToPreviousQuestion() {
        int currentItem = viewPager.getCurrentItem();
        if (currentItem > 0) {
            viewPager.setCurrentItem(currentItem - 1);
        }
    }

    public void closeSurvey() {
        finish();
    }
    @Override
    public void onAnswerSaved() {
        refreshNavDrawer();
    }

    private void refreshNavDrawer() {
        repository.getSurveyWithAnswersAndAttachments(surveyId, surveyWithData -> {
            if (surveyWithData != null) {
                List<CategoryItem> categoryItems = QuestionnaireParser.parseHierarchical(this, getString(R.string.questionnaire_file), getString(R.string.questionnaire_sheet));
                ExpandableNavAdapter navAdapter = new ExpandableNavAdapter(categoryItems, surveyWithData.answers, surveyWithData.attachments, this);
                runOnUiThread(() -> navRecyclerView.setAdapter(navAdapter));
            }
        });
    }

    @Override
    public void onExportAndClose() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SURVEY_ID", surveyId);
        resultIntent.putExtra("action", "export");
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onShareAndClose() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SURVEY_ID", surveyId);
        resultIntent.putExtra("action", "share");
        setResult(RESULT_OK, resultIntent);
        finish();
    }

}