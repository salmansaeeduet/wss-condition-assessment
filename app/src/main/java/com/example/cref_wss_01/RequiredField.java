package com.example.cref_wss_01;

import java.util.ArrayList;
import java.util.List;

public class RequiredField {
    public final int id;
    public final String label;
    public final String tag;
    public final String answerType;

    private RequiredField(int id, String label, String tag, String answerType) {
        this.id = id;
        this.label = label;
        this.tag = tag;
        this.answerType = answerType != null ? answerType : "";
    }

    /**
     * Scans the question list for tags containing a "REQ:Label" part (pipe-delimited).
     * Returns an empty list when no REQ tags are found — export is then unrestricted.
     */
    public static List<RequiredField> parseAll(List<Question> questions) {
        List<RequiredField> result = new ArrayList<>();
        for (Question q : questions) {
            String tag = q.getTag();
            if (tag == null) continue;
            for (String part : tag.split("\\|")) {
                part = part.trim();
                if (part.toUpperCase().startsWith("REQ:")) {
                    String label = part.substring(4).trim();
                    if (label.isEmpty()) label = q.getQuestionText();
                    result.add(new RequiredField(q.getId(), label, tag, q.getAnswerType()));
                    break;
                }
            }
        }
        return result;
    }

    /** Returns the display value for this field from a raw DB answer string. */
    public String getDisplayValue(String raw) {
        return getDisplayValue(raw, answerType);
    }

    /** Static overload — usable without a RequiredField instance (e.g. for FilenamePrefix). */
    public static String getDisplayValue(String raw, String answerType) {
        if (raw == null) return "";
        if ("COMPOUND".equals(answerType)) return extractFirstCompoundField(raw);
        return raw;
    }

    /**
     * Extracts the value of the first sub-field from a COMPOUND answer string.
     * Format: "Label=value||Label=value||..."
     */
    public static String extractFirstCompoundField(String raw) {
        if (raw == null) return "";
        int eq = raw.indexOf('=');
        if (eq <= 0) return raw;
        int sep = raw.indexOf("||");
        return raw.substring(eq + 1, sep < 0 ? raw.length() : sep);
    }

    // -------------------------------------------------------------------------
    // FilenamePrefix — questions tagged PREFIX:n contribute to the ZIP filename
    // -------------------------------------------------------------------------

    public static class FilenamePrefix {
        public final int questionId;
        public final int order;
        public final String answerType;

        private FilenamePrefix(int questionId, int order, String answerType) {
            this.questionId = questionId;
            this.order = order;
            this.answerType = answerType != null ? answerType : "";
        }

        /**
         * Scans the question list for tags containing a "PREFIX:n" part (pipe-delimited).
         * Returns an empty list when no PREFIX tags are found.
         * Result is sorted ascending by n so callers can iterate in order.
         */
        public static List<FilenamePrefix> parseAll(List<Question> questions) {
            List<FilenamePrefix> result = new ArrayList<>();
            for (Question q : questions) {
                String tag = q.getTag();
                if (tag == null) continue;
                for (String part : tag.split("\\|")) {
                    part = part.trim();
                    if (part.toUpperCase().startsWith("PREFIX:")) {
                        try {
                            int n = Integer.parseInt(part.substring(7).trim());
                            result.add(new FilenamePrefix(q.getId(), n, q.getAnswerType()));
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            }
            result.sort((a, b) -> a.order - b.order);
            return result;
        }
    }
}
