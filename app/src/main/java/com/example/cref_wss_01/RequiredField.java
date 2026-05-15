package com.example.cref_wss_01;

import android.content.Context;
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
     * Parses R.array.export_required_fields entries of the form "TAG:Label" and resolves
     * each tag to a question ID by searching the provided question list.
     *
     * Questions without a matching tag are silently omitted so that a misconfigured
     * entry never crashes the export flow.
     */
    public static List<RequiredField> parseAll(Context context, List<Question> questions) {
        String[] entries = context.getResources().getStringArray(R.array.export_required_fields);
        List<RequiredField> result = new ArrayList<>();
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            String tag   = entry.substring(0, colon).trim();
            String label = entry.substring(colon + 1).trim();
            Question q = findByTag(questions, tag);
            if (q != null) result.add(new RequiredField(q.getId(), label, tag, q.getAnswerType()));
        }
        return result;
    }

    /**
     * Returns the human-readable value for this field from a raw DB answer string.
     * For COMPOUND answers ("Label=value||Label=value...") returns the first field's value.
     * For all other types returns the raw string unchanged.
     */
    public String getDisplayValue(String raw) {
        if (raw == null) return "";
        if ("COMPOUND".equals(answerType)) return extractFirstCompoundField(raw);
        return raw;
    }

    /**
     * Extracts the value of the first sub-field from a COMPOUND answer string.
     * Format: "Label=value||Label=value||..."
     * Returns the raw string unchanged if it doesn't match the pattern.
     */
    public static String extractFirstCompoundField(String raw) {
        if (raw == null) return "";
        int eq = raw.indexOf('=');
        if (eq <= 0) return raw;
        int sep = raw.indexOf("||");
        return raw.substring(eq + 1, sep < 0 ? raw.length() : sep);
    }

    private static Question findByTag(List<Question> questions, String tag) {
        for (Question q : questions) {
            if (tag.equalsIgnoreCase(q.getTag())) return q;
        }
        return null;
    }
}
