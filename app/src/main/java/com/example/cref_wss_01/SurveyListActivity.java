package com.example.cref_wss_01;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SurveyListActivity extends AppCompatActivity
        implements SurveyAdapter.OnDeleteClickListener, SurveyAdapter.OnSurveyClickListener,
        SurveyAdapter.OnExportClickListener, SurveyAdapter.OnShareClickListener {

    private SurveyRepository repository;
    private SurveyAdapter adapter;
    private int totalQuestions = 0;
    private final List<Question> allQuestions = new ArrayList<>();
    private List<RequiredField> requiredFields;
    private ActivityResultLauncher<Intent> surveyActivityLauncher;
    private ActivityResultLauncher<String> importFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_survey_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        repository = new SurveyRepository(getApplication());

        // Load all top-level questions from the configured XLSX (for export and completion %)
        List<CategoryItem> categoryItems = QuestionnaireParser.parseHierarchical(
                this, getString(R.string.questionnaire_file), getString(R.string.questionnaire_sheet));
        for (CategoryItem category : categoryItems) {
            for (SubCategoryItem subCategory : category.getSubCategories()) {
                for (QuestionItem questionItem : subCategory.getQuestions()) {
                    allQuestions.add(questionItem.getQuestion());
                    totalQuestions++;
                }
            }
        }

        requiredFields = RequiredField.parseAll(this, allQuestions);

        int titleQuestionId = requiredFields.isEmpty() ? 0 : requiredFields.get(0).id;
        String titleAnswerType = requiredFields.isEmpty() ? "" : requiredFields.get(0).answerType;

        RecyclerView recyclerView = findViewById(R.id.survey_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SurveyAdapter(new ArrayList<>(), totalQuestions, titleQuestionId, titleAnswerType,
                this, this, this, this);
        repository.getAllSurveysWithAnswers(surveys -> runOnUiThread(() -> adapter.setSurveys(surveys)));
        recyclerView.setAdapter(adapter);

        FloatingActionButton fabOfflineMaps = findViewById(R.id.fab_offline_maps);
        fabOfflineMaps.setOnClickListener(v ->
                startActivity(new Intent(SurveyListActivity.this, OfflineMapsActivity.class)));

        FloatingActionButton fab = findViewById(R.id.fab_new_survey);
        FloatingActionButton fabImport = findViewById(R.id.fab_import_survey);

        // Push FABs above the navigation bar
        final int fabOfflineOriginal = (int)(80  * getResources().getDisplayMetrics().density);
        final int fabImportOriginal  = (int)(144 * getResources().getDisplayMetrics().density);
        final int fabNewOriginal     = (int)(16  * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(fabOfflineMaps, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = fabOfflineOriginal + navBar;
            v.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(fabImport, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = fabImportOriginal + navBar;
            v.setLayoutParams(lp);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = fabNewOriginal + navBar;
            v.setLayoutParams(lp);
            return insets;
        });

        fab.setOnClickListener(view -> {
            Survey newSurvey = new Survey();
            newSurvey.surveyDate = System.currentTimeMillis();
            repository.insertSurvey(newSurvey, newSurveyId -> {
                Intent intent = new Intent(SurveyListActivity.this, MainActivity.class);
                intent.putExtra("SURVEY_ID", newSurveyId);
                surveyActivityLauncher.launch(intent);
            });
        });

        surveyActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        long surveyId = result.getData().getLongExtra("SURVEY_ID", -1);
                        String action = result.getData().getStringExtra("action");
                        if (surveyId != -1 && action != null) {
                            repository.getSurveyWithAnswersAndAttachments(surveyId, surveyWithData -> {
                                if (surveyWithData != null) {
                                    runOnUiThread(() -> {
                                        if ("export".equals(action)) onExportClick(surveyWithData);
                                        else if ("share".equals(action)) onShareClick(surveyWithData.survey);
                                    });
                                }
                            });
                        }
                    }
                });

        // Import survey from file picker
        importFileLauncher = registerForActivityResult(new GetContent(), uri -> {
            if (uri != null) {
                Intent intent = new Intent(this, ImportPreviewActivity.class);
                intent.setData(uri);
                startActivityForResult(intent, 0);
            }
        });

        fabImport.setOnClickListener(v -> importFileLauncher.launch("application/zip"));

        createNotificationChannel();
    }

    @Override
    public void onSurveyClick(Survey survey) {
        Intent intent = new Intent(SurveyListActivity.this, MainActivity.class);
        intent.putExtra("SURVEY_ID", survey.id);
        surveyActivityLauncher.launch(intent);
    }

    public static String findAnswerValue(List<Answer> answers, int questionId) {
        for (Answer answer : answers) {
            if (answer.questionId == questionId) return answer.answerValue;
        }
        return null;
    }

    @Override
    public void onExportClick(SurveyWithAnswers surveyWithAnswers) {
        // Validate all required fields
        for (RequiredField field : requiredFields) {
            String displayVal = field.getDisplayValue(findAnswerValue(surveyWithAnswers.answers, field.id));
            if (displayVal.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Missing Information")
                        .setMessage("\"" + field.label + "\" is required before exporting. Go to that question now?")
                        .setPositiveButton("Go to Question", (dialog, which) -> {
                            Intent intent = new Intent(SurveyListActivity.this, MainActivity.class);
                            intent.putExtra("SURVEY_ID", surveyWithAnswers.survey.id);
                            intent.putExtra("JUMP_TO_QUESTION", field.id);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        new Thread(() -> {
            Uri exportUri = SurveyExporter.export(this, surveyWithAnswers, allQuestions);
            runOnUiThread(() -> {
                if (exportUri != null) {
                    Survey surveyToUpdate = surveyWithAnswers.survey;
                    surveyToUpdate.lastExportedZipPath = exportUri.toString();
                    repository.updateSurvey(surveyToUpdate, this::refreshSurveyList);
                    String fileName = getDisplayName(exportUri);
                    String msg = "Survey saved to the Documents folder."
                            + (fileName.isEmpty() ? "" : "\n\nFile: " + fileName);
                    new AlertDialog.Builder(this)
                            .setTitle("Export Successful")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Share", (dialog, which) -> shareUri(exportUri))
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Export Failed")
                            .setMessage("An error occurred during the export process.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    private void shareUri(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "No exported file found to share.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType("application/zip");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share Survey"));
    }

    private String getDisplayName(Uri uri) {
        if (uri == null) return "";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } catch (Exception ignored) {}
        }
        String path = uri.getPath();
        return path != null ? new File(path).getName() : "";
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "EXPORT_CHANNEL", "Export Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for survey export status");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSurveyList();
    }

    @Override
    public void onDeleteClick(Survey survey) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Survey")
                .setMessage("Are you sure you want to permanently delete this survey and all its data? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) ->
                        repository.deleteSurvey(survey, this::refreshSurveyList))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Refresh list after a completed import
        if (resultCode == RESULT_OK) refreshSurveyList();
    }

    private void refreshSurveyList() {
        repository.getAllSurveysWithAnswers(surveys -> runOnUiThread(() -> adapter.setSurveys(surveys)));
    }

    @Override
    public void onShareClick(Survey survey) {
        if (survey.lastExportedZipPath == null || survey.lastExportedZipPath.isEmpty()) {
            Toast.makeText(this, "No exported file found to share.", Toast.LENGTH_SHORT).show();
            return;
        }
        String stored = survey.lastExportedZipPath;
        Uri uri;
        if (stored.startsWith("content://")) {
            uri = Uri.parse(stored);
        } else {
            // Legacy: stored as file path (old exports before MediaStore migration)
            File f = new File(stored);
            if (!f.exists()) { Toast.makeText(this, "Exported file not found.", Toast.LENGTH_SHORT).show(); return; }
            uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
        }
        shareUri(uri);
    }
}
