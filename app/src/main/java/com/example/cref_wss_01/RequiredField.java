package com.example.cref_wss_01;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class RequiredField {
    public final int id;
    public final String label;

    private RequiredField(int id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Parses R.array.export_required_fields entries of the form "questionId:Label". */
    public static List<RequiredField> parseAll(Context context) {
        String[] entries = context.getResources().getStringArray(R.array.export_required_fields);
        List<RequiredField> result = new ArrayList<>();
        for (String entry : entries) {
            int colon = entry.indexOf(':');
            if (colon > 0) {
                try {
                    int id = Integer.parseInt(entry.substring(0, colon).trim());
                    String label = entry.substring(colon + 1).trim();
                    result.add(new RequiredField(id, label));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
