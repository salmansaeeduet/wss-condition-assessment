package com.example.cref_wss_01;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImportPreviewActivity extends AppCompatActivity {

    static final int REQUEST_MERGE = 1001;

    private SurveyRepository repo;
    private List<Question> allQuestions;
    private List<RequiredField> requiredFields;

    private SurveyImporter.ParseResult parseResult;
    private List<SurveyWithAnswers> allSurveys = new ArrayList<>();
    private long selectedSurveyId = -1;
    private boolean createNew = true;

    // Views
    private ProgressBar progressBar;
    private View cardSummary;
    private TextView tvSummaryTitle;
    private TextView tvSummarySubtitle;
    private TextView tvSummaryStats;
    private TextView tvModeLabel;
    private RadioGroup radioGroup;
    private TextView tvSurveyListLabel;
    private RecyclerView rvSurveys;
    private MaterialButton btnContinue;

    private SurveySelectAdapter surveySelectAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_preview);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar      = findViewById(R.id.progress_bar);
        cardSummary      = findViewById(R.id.card_summary);
        tvSummaryTitle   = findViewById(R.id.tv_summary_title);
        tvSummarySubtitle= findViewById(R.id.tv_summary_subtitle);
        tvSummaryStats   = findViewById(R.id.tv_summary_stats);
        tvModeLabel      = findViewById(R.id.tv_mode_label);
        radioGroup       = findViewById(R.id.radio_group);
        tvSurveyListLabel= findViewById(R.id.tv_survey_list_label);
        rvSurveys        = findViewById(R.id.rv_surveys);
        btnContinue      = findViewById(R.id.btn_continue);

        repo = new SurveyRepository(getApplication());

        // Load questionnaire questions
        allQuestions = new ArrayList<>();
        List<CategoryItem> categories = QuestionnaireParser.parseHierarchical(
                this, getString(R.string.questionnaire_file), getString(R.string.questionnaire_sheet));
        for (CategoryItem cat : categories)
            for (SubCategoryItem sub : cat.getSubCategories())
                for (QuestionItem qi : sub.getQuestions())
                    allQuestions.add(qi.getQuestion());

        requiredFields = RequiredField.parseAll(this, allQuestions);

        // Survey selection RecyclerView
        surveySelectAdapter = new SurveySelectAdapter(allSurveys, requiredFields, id -> {
            selectedSurveyId = id;
            updateContinueButton();
        });
        rvSurveys.setLayoutManager(new LinearLayoutManager(this));
        rvSurveys.setAdapter(surveySelectAdapter);
        rvSurveys.setNestedScrollingEnabled(false);

        // Load existing surveys for merge option
        repo.getAllSurveysWithAnswers(surveys -> runOnUiThread(() -> {
            allSurveys.clear();
            allSurveys.addAll(surveys);
            surveySelectAdapter.notifyDataSetChanged();
        }));

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            createNew = (checkedId == R.id.radio_create_new);
            rvSurveys.setVisibility(createNew ? View.GONE : View.VISIBLE);
            tvSurveyListLabel.setVisibility(createNew ? View.GONE : View.VISIBLE);
            if (createNew) selectedSurveyId = -1;
            updateContinueButton();
        });

        btnContinue.setOnClickListener(v -> onContinueClicked());

        // Resolve incoming URI
        Uri zipUri = resolveUri(getIntent());
        if (zipUri == null) {
            Toast.makeText(this, "No file to import.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        startParsing(zipUri);
    }

    private Uri resolveUri(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) return intent.getData();
        if (Intent.ACTION_SEND.equals(action)) return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        // Launched from file picker: URI stored in data
        return intent.getData();
    }

    private void startParsing(Uri zipUri) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            SurveyImporter.ParseResult result = SurveyImporter.parseZip(this, zipUri);
            runOnUiThread(() -> onParseComplete(result));
        }).start();
    }

    private void onParseComplete(SurveyImporter.ParseResult result) {
        progressBar.setVisibility(View.GONE);

        if (result.error != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Import Error")
                    .setMessage(result.error)
                    .setPositiveButton("OK", (d, w) -> finish())
                    .show();
            return;
        }

        parseResult = result;

        // Fill summary card
        String title    = requiredFieldValue(result.rows, 0);
        String subtitle = requiredFieldValue(result.rows, 1);
        tvSummaryTitle.setText(title.isEmpty() ? "Unnamed Survey" : title);
        if (!subtitle.isEmpty()) {
            tvSummarySubtitle.setText("By " + subtitle);
            tvSummarySubtitle.setVisibility(View.VISIBLE);
        }

        long answerCount = result.rows.stream().filter(r -> !r.answerValue.isEmpty()).count();
        long mediaCount  = result.mediaFiles.size();
        tvSummaryStats.setText(answerCount + " answer" + (answerCount == 1 ? "" : "s")
                + " • " + mediaCount + " media file" + (mediaCount == 1 ? "" : "s"));

        cardSummary.setVisibility(View.VISIBLE);
        tvModeLabel.setVisibility(View.VISIBLE);
        radioGroup.setVisibility(View.VISIBLE);
        updateContinueButton();
    }

    private String requiredFieldValue(List<SurveyImporter.ParsedRow> rows, int fieldIndex) {
        if (requiredFields.size() <= fieldIndex) return "";
        RequiredField field = requiredFields.get(fieldIndex);
        for (SurveyImporter.ParsedRow row : rows) {
            if (!row.tag.isEmpty() && row.tag.equals(field.tag)) return row.answerValue;
        }
        // Fallback: match by question text
        for (SurveyImporter.ParsedRow row : rows) {
            if (row.questionText.equalsIgnoreCase(field.label)) return row.answerValue;
        }
        return "";
    }

    private void updateContinueButton() {
        boolean ready = parseResult != null && (createNew || selectedSurveyId >= 0);
        btnContinue.setEnabled(ready);
    }

    private void onContinueClicked() {
        if (parseResult == null) return;
        btnContinue.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        if (createNew) {
            Survey newSurvey = new Survey();
            newSurvey.surveyDate = System.currentTimeMillis();
            repo.insertSurvey(newSurvey, newId -> {
                List<MergeConflict> conflicts = SurveyImporter.computeConflicts(
                        parseResult, newId, new ArrayList<>(), allQuestions);
                // All will auto-resolve to KEEP_IMPORTED since there are no local answers
                executeAndFinish(conflicts, newId);
            });
        } else {
            long surveyId = selectedSurveyId;
            repo.getAnswersForSurvey(surveyId, localAnswers -> {
                new Thread(() -> {
                    List<MergeConflict> conflicts = SurveyImporter.computeConflicts(
                            parseResult, surveyId, localAnswers, allQuestions);

                    List<MergeConflict> trueConflicts = new ArrayList<>();
                    for (MergeConflict c : conflicts) {
                        if (c.isTrueConflict()) trueConflicts.add(c);
                    }

                    runOnUiThread(() -> {
                        if (trueConflicts.isEmpty()) {
                            showNoConflictDialog(conflicts, surveyId);
                        } else {
                            progressBar.setVisibility(View.GONE);
                            btnContinue.setEnabled(true);
                            ImportSession.allConflicts = conflicts;
                            ImportSession.parseResult  = parseResult;
                            ImportSession.targetSurveyId = surveyId;
                            startActivityForResult(
                                    new Intent(this, MergeConflictActivity.class),
                                    REQUEST_MERGE);
                        }
                    });
                }).start();
            });
        }
    }

    private void showNoConflictDialog(List<MergeConflict> conflicts, long surveyId) {
        progressBar.setVisibility(View.GONE);
        btnContinue.setEnabled(true);

        long importCount = 0;
        long attCount    = 0;
        for (MergeConflict c : conflicts) {
            if (c.resolution == MergeConflict.KEEP_IMPORTED) importCount++;
            attCount += c.importedAttachmentPaths.size();
        }

        StringBuilder msg = new StringBuilder();
        msg.append(importCount).append(" answer").append(importCount == 1 ? "" : "s")
                .append(" will be imported automatically.");
        if (attCount > 0)
            msg.append("\n").append(attCount).append(" media file").append(attCount == 1 ? "" : "s")
                    .append(" will be added.");

        new AlertDialog.Builder(this)
                .setTitle("No Conflicts Found")
                .setMessage(msg.toString())
                .setPositiveButton("Merge", (d, w) -> executeAndFinish(conflicts, surveyId))
                .setNegativeButton("Cancel", (d, w) -> { /* user cancels */ })
                .show();
    }

    private void executeAndFinish(List<MergeConflict> conflicts, long surveyId) {
        progressBar.setVisibility(View.VISIBLE);
        btnContinue.setEnabled(false);

        new Thread(() -> {
            SurveyImporter.MergeBundle bundle =
                    SurveyImporter.buildMergeBundle(conflicts, surveyId, this);

            repo.executeBatchUpsert(bundle.answers, bundle.attachments, () ->
                    runOnUiThread(() -> {
                        if (parseResult != null && parseResult.tempDir != null)
                            SurveyImporter.deleteDir(parseResult.tempDir);
                        ImportSession.clear();
                        Toast.makeText(this, "Import complete!", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }));
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MERGE && resultCode == RESULT_OK) {
            // MergeConflictActivity finished and executed the merge
            finish();
        }
    }

    // -------------------------------------------------------------------------
    // Inner adapter for survey selection
    // -------------------------------------------------------------------------

    private static class SurveySelectAdapter
            extends RecyclerView.Adapter<SurveySelectAdapter.VH> {

        interface OnSurveySelected { void onSelected(long surveyId); }

        private final List<SurveyWithAnswers> surveys;
        private final List<RequiredField>     requiredFields;
        private final OnSurveySelected        listener;
        private int selectedPos = -1;

        SurveySelectAdapter(List<SurveyWithAnswers> surveys,
                            List<RequiredField> requiredFields,
                            OnSurveySelected listener) {
            this.surveys        = surveys;
            this.requiredFields = requiredFields;
            this.listener       = listener;
        }

        @Override
        @androidx.annotation.NonNull
        public VH onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_survey_select, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH h, int pos) {
            SurveyWithAnswers s = surveys.get(pos);
            boolean selected = (pos == selectedPos);
            h.radio.setChecked(selected);

            // Title: first required field answer
            String title = "Survey " + (pos + 1);
            if (!requiredFields.isEmpty()) {
                String val = SurveyListActivity.findAnswerValue(s.answers, requiredFields.get(0).id);
                String display = requiredFields.get(0).getDisplayValue(val);
                if (!display.isEmpty()) title = display;
            }
            h.tvTitle.setText(title);

            // Meta: date + answer count
            String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(new Date(s.survey.surveyDate));
            int ansCount = s.answers != null ? (int) s.answers.stream()
                    .filter(a -> a.answerValue != null && !a.answerValue.isEmpty()).count() : 0;
            h.tvMeta.setText(date + " • " + ansCount + " answer" + (ansCount == 1 ? "" : "s"));

            h.itemView.setOnClickListener(v -> {
                int prev = selectedPos;
                selectedPos = h.getAdapterPosition();
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(selectedPos);
                if (listener != null) listener.onSelected(s.survey.id);
            });
        }

        @Override
        public int getItemCount() { return surveys.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final android.widget.RadioButton radio;
            final TextView tvTitle;
            final TextView tvMeta;
            VH(View v) {
                super(v);
                radio   = v.findViewById(R.id.radio_select);
                tvTitle = v.findViewById(R.id.tv_survey_title);
                tvMeta  = v.findViewById(R.id.tv_survey_meta);
            }
        }
    }
}
