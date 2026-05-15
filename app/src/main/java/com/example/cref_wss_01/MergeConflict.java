package com.example.cref_wss_01;

import java.util.List;

public class MergeConflict {

    public static final int UNRESOLVED = 0;
    public static final int KEEP_LOCAL = 1;
    public static final int KEEP_IMPORTED = 2;
    public static final int COMBINE = 3;

    public Question question;
    public Answer localAnswer;       // null → no local answer for this question
    public Answer importedAnswer;    // null → no imported answer (attachment-only import)
    public List<String> importedAttachmentPaths; // temp file paths extracted from ZIP

    public int resolution = UNRESOLVED;

    public MergeConflict(Question question, Answer localAnswer, Answer importedAnswer,
                         List<String> importedAttachmentPaths) {
        this.question = question;
        this.localAnswer = localAnswer;
        this.importedAnswer = importedAnswer;
        this.importedAttachmentPaths = importedAttachmentPaths;
    }

    /** True when both sides have an answer value — user must resolve. */
    public boolean isTrueConflict() {
        return resolution == UNRESOLVED;
    }

    /**
     * Returns true for answer types where the two values can be merged into one
     * (rather than picking one side). Attachments are always merged regardless.
     */
    public boolean canCombine() {
        if (question == null) return false;
        switch (question.getAnswerType()) {
            case "TEXT":
            case "MULTIPLE_CHOICE_MULTI":
            case "COMPOUND":
            case "GEOMETRY":
                return true;
            default:
                return false;
        }
    }
}
