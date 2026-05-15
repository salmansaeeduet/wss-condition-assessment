package com.example.cref_wss_01;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class RequiredField {
    public final int id;
    public final String label;
    public final String tag;

    private RequiredField(int id, String label, String tag) {
        this.id = id;
        this.label = label;
        this.tag = tag;
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
            int id = findIdByTag(questions, tag);
            if (id != -1) result.add(new RequiredField(id, label, tag));
        }
        return result;
    }

    private static int findIdByTag(List<Question> questions, String tag) {
        for (Question q : questions) {
            if (tag.equalsIgnoreCase(q.getTag())) return q.getId();
        }
        return -1;
    }
}
