package com.example.cref_wss_01;

import java.util.List;

/** Static holder for passing import data between ImportPreviewActivity and MergeConflictActivity. */
public class ImportSession {
    public static SurveyImporter.ParseResult parseResult;
    public static List<MergeConflict> allConflicts; // auto-merge + true conflicts
    public static long targetSurveyId = -1;

    public static void clear() {
        parseResult = null;
        allConflicts = null;
        targetSurveyId = -1;
    }
}
