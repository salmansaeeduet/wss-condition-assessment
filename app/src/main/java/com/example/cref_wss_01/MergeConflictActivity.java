package com.example.cref_wss_01;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MergeConflictActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvConflictCount;
    private MaterialButton btnApply;
    private ConflictResolutionAdapter adapter;

    /** Only the true conflicts shown to the user; auto-merge ones stay in ImportSession. */
    private List<MergeConflict> trueConflicts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge_conflict);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar     = findViewById(R.id.progress_bar);
        tvConflictCount = findViewById(R.id.tv_conflict_count);
        btnApply        = findViewById(R.id.btn_apply_merge);

        // Collect true conflicts from ImportSession
        if (ImportSession.allConflicts != null) {
            for (MergeConflict c : ImportSession.allConflicts) {
                if (c.isTrueConflict()) trueConflicts.add(c);
            }
        }

        if (trueConflicts.isEmpty()) {
            // Nothing to resolve — shouldn't happen normally but handle gracefully
            finish();
            return;
        }

        updateCountLabel();

        adapter = new ConflictResolutionAdapter(trueConflicts, allResolved -> {
            btnApply.setEnabled(allResolved);
            updateCountLabel();
        });

        RecyclerView rv = findViewById(R.id.rv_conflicts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnApply.setOnClickListener(v -> applyMerge());
    }

    private void updateCountLabel() {
        long remaining = trueConflicts.stream()
                .filter(c -> c.resolution == MergeConflict.UNRESOLVED).count();
        tvConflictCount.setText(remaining == 0
                ? "All conflicts resolved — tap Apply to save."
                : remaining + " conflict" + (remaining == 1 ? "" : "s") + " remaining.");
    }

    private void applyMerge() {
        if (ImportSession.allConflicts == null) { finish(); return; }

        btnApply.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        long targetSurveyId = ImportSession.targetSurveyId;
        List<MergeConflict> allConflicts = ImportSession.allConflicts;

        SurveyRepository repo = new SurveyRepository(getApplication());

        new Thread(() -> {
            SurveyImporter.MergeBundle bundle =
                    SurveyImporter.buildMergeBundle(allConflicts, targetSurveyId, this);

            repo.executeBatchUpsert(bundle.answers, bundle.attachments, () ->
                    runOnUiThread(() -> {
                        if (ImportSession.parseResult != null
                                && ImportSession.parseResult.tempDir != null) {
                            SurveyImporter.deleteDir(ImportSession.parseResult.tempDir);
                        }
                        ImportSession.clear();
                        setResult(RESULT_OK);
                        finish();
                    }));
        }).start();
    }
}
