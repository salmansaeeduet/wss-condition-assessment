package com.example.cref_wss_01;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ConflictResolutionAdapter
        extends RecyclerView.Adapter<ConflictResolutionAdapter.ViewHolder> {

    public interface OnResolutionChangedListener {
        void onResolutionChanged(boolean allResolved);
    }

    private final List<MergeConflict> conflicts;
    private final OnResolutionChangedListener listener;

    public ConflictResolutionAdapter(List<MergeConflict> conflicts,
                                     OnResolutionChangedListener listener) {
        this.conflicts = conflicts;
        this.listener  = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_merge_conflict, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        MergeConflict c = conflicts.get(position);

        h.tvQuestion.setText(c.question.getQuestionText());

        String localVal    = c.localAnswer    != null && c.localAnswer.answerValue    != null ? c.localAnswer.answerValue    : "(empty)";
        String importedVal = c.importedAnswer != null && c.importedAnswer.answerValue != null ? c.importedAnswer.answerValue : "(empty)";
        h.tvLocal.setText(localVal);
        h.tvImported.setText(importedVal);

        // Combine option: enabled only for types that support it
        h.radioCombine.setEnabled(c.canCombine());
        h.radioCombine.setAlpha(c.canCombine() ? 1f : 0.35f);

        // Attachment note
        if (!c.importedAttachmentPaths.isEmpty()) {
            int n = c.importedAttachmentPaths.size();
            h.tvAttachmentsNote.setText(n + " imported attachment" + (n == 1 ? "" : "s") + " will be added automatically.");
            h.tvAttachmentsNote.setVisibility(View.VISIBLE);
        } else {
            h.tvAttachmentsNote.setVisibility(View.GONE);
        }

        // Restore saved resolution
        h.radioGroup.setOnCheckedChangeListener(null);
        switch (c.resolution) {
            case MergeConflict.KEEP_LOCAL:     h.radioGroup.check(R.id.radio_keep_local);     break;
            case MergeConflict.KEEP_IMPORTED:  h.radioGroup.check(R.id.radio_keep_imported);  break;
            case MergeConflict.COMBINE:        h.radioGroup.check(R.id.radio_combine);        break;
            default:                           h.radioGroup.clearCheck();                     break;
        }

        h.radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_keep_local)    c.resolution = MergeConflict.KEEP_LOCAL;
            else if (checkedId == R.id.radio_keep_imported) c.resolution = MergeConflict.KEEP_IMPORTED;
            else if (checkedId == R.id.radio_combine)  c.resolution = MergeConflict.COMBINE;
            else                                        c.resolution = MergeConflict.UNRESOLVED;

            if (listener != null) listener.onResolutionChanged(allResolved());
        });
    }

    @Override
    public int getItemCount() { return conflicts.size(); }

    public boolean allResolved() {
        for (MergeConflict c : conflicts) {
            if (c.resolution == MergeConflict.UNRESOLVED) return false;
        }
        return true;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView   tvQuestion;
        final TextView   tvLocal;
        final TextView   tvImported;
        final RadioGroup radioGroup;
        final RadioButton radioCombine;
        final TextView   tvAttachmentsNote;

        ViewHolder(View v) {
            super(v);
            tvQuestion        = v.findViewById(R.id.tv_question);
            tvLocal           = v.findViewById(R.id.tv_local_answer);
            tvImported        = v.findViewById(R.id.tv_imported_answer);
            radioGroup        = v.findViewById(R.id.radio_group);
            radioCombine      = v.findViewById(R.id.radio_combine);
            tvAttachmentsNote = v.findViewById(R.id.tv_attachments_note);
        }
    }
}
