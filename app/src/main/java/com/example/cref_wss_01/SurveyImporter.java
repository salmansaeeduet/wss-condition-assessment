package com.example.cref_wss_01;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SurveyImporter {

    private static final String TAG = "SurveyImporter";

    // -------------------------------------------------------------------------
    // Data types
    // -------------------------------------------------------------------------

    public static class ParsedRow {
        public String tag = "";
        public String questionText = "";
        public String answerValue = "";
        public String remarks = "";
        public List<String> attachmentNames = new ArrayList<>();
    }

    public static class ParseResult {
        public List<ParsedRow> rows = new ArrayList<>();
        /** filename in ZIP → extracted temp File */
        public Map<String, File> mediaFiles = new HashMap<>();
        public File tempDir;
        public String error;
    }

    public static class MergeBundle {
        public List<Answer> answers = new ArrayList<>();
        public List<MediaAttachment> attachments = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // ZIP parsing
    // -------------------------------------------------------------------------

    public static ParseResult parseZip(Context ctx, Uri zipUri) {
        ParseResult result = new ParseResult();

        File tempDir = new File(ctx.getCacheDir(), "import_" + System.currentTimeMillis());
        tempDir.mkdirs();
        result.tempDir = tempDir;

        try (InputStream raw = ctx.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw))) {

            ZipEntry entry;
            byte[] csvBytes = null;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("survey_data.csv".equals(name)) {
                    csvBytes = readAll(zis);
                } else if (!entry.isDirectory()) {
                    File out = new File(tempDir, new File(name).getName()); // flatten any sub-paths
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                    result.mediaFiles.put(new File(name).getName(), out);
                }
                zis.closeEntry();
            }

            if (csvBytes == null) {
                result.error = "No survey_data.csv found in the ZIP file.";
                return result;
            }

            result.rows = parseCsv(new String(csvBytes, "UTF-8"));

        } catch (Exception e) {
            Log.e(TAG, "Error parsing ZIP", e);
            result.error = e.getMessage() != null ? e.getMessage() : "Unknown error";
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // CSV parsing
    // -------------------------------------------------------------------------

    private static List<ParsedRow> parseCsv(String csv) {
        List<ParsedRow> rows = new ArrayList<>();
        String[] lines = csv.split("\r?\n");
        if (lines.length < 2) return rows;

        List<String> header = parseLine(lines[0]);
        boolean newFormat = !header.isEmpty() && "Tag".equalsIgnoreCase(header.get(0).trim());

        // Column indices vary by format version
        int tagCol        = newFormat ? 0 : -1;
        int questionCol   = newFormat ? 1 : 0;
        int answerCol     = newFormat ? 2 : 1;
        int remarksCol    = newFormat ? 3 : 2;
        int attStartCol   = newFormat ? 7 : 6; // filenames start after the count columns

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            List<String> cols = parseLine(line);
            if (cols.size() <= questionCol) continue;

            ParsedRow row = new ParsedRow();
            row.tag         = (tagCol >= 0 && cols.size() > tagCol)  ? cols.get(tagCol).trim()      : "";
            row.questionText = cols.size() > questionCol             ? cols.get(questionCol).trim()  : "";
            row.answerValue  = cols.size() > answerCol               ? cols.get(answerCol).trim()    : "";
            row.remarks      = cols.size() > remarksCol              ? cols.get(remarksCol).trim()   : "";

            for (int c = attStartCol; c < cols.size(); c++) {
                String name = cols.get(c).trim();
                if (!name.isEmpty()) row.attachmentNames.add(name);
            }

            rows.add(row);
        }

        return rows;
    }

    /** Handles quoted fields with embedded commas. */
    private static List<String> parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result;
    }

    // -------------------------------------------------------------------------
    // Conflict computation
    // -------------------------------------------------------------------------

    /**
     * Compares imported rows against local answers and returns a list of MergeConflicts.
     * Auto-merge cases already have their resolution set; true conflicts are UNRESOLVED.
     *
     * @param localSurveyId the survey being merged into (used to stamp new Answer objects)
     * @param localAnswers  current answers in the local survey
     * @param allQuestions  flat list of questions from the questionnaire (top-level + sub-questions)
     */
    public static List<MergeConflict> computeConflicts(
            ParseResult parsed,
            long localSurveyId,
            List<Answer> localAnswers,
            List<Question> allQuestions) {

        Map<String, Question> byTag  = new HashMap<>();
        Map<String, Question> byText = new HashMap<>();
        buildLookups(allQuestions, byTag, byText);

        Map<Integer, Answer> localMap = new HashMap<>();
        for (Answer a : localAnswers) localMap.put(a.questionId, a);

        List<MergeConflict> conflicts = new ArrayList<>();

        for (ParsedRow row : parsed.rows) {
            Question q = resolve(row, byTag, byText);
            if (q == null) continue;

            boolean hasImported = row.answerValue != null && !row.answerValue.isEmpty();
            boolean hasAttachments = !row.attachmentNames.isEmpty();
            if (!hasImported && !hasAttachments) continue;

            Answer localAnswer = localMap.get(q.getId());
            boolean hasLocal = localAnswer != null
                    && localAnswer.answerValue != null
                    && !localAnswer.answerValue.isEmpty();

            Answer importedAnswer = null;
            if (hasImported) {
                importedAnswer = new Answer();
                importedAnswer.surveyId = localSurveyId;
                importedAnswer.questionId = q.getId();
                importedAnswer.answerValue = row.answerValue;
                importedAnswer.remarks = row.remarks;
            }

            List<String> attPaths = resolveAttachmentPaths(row.attachmentNames, parsed.mediaFiles);

            MergeConflict conflict = new MergeConflict(q, localAnswer, importedAnswer, attPaths);

            if (hasImported && hasLocal) {
                // Both sides answered — leave UNRESOLVED for the user
                conflict.resolution = MergeConflict.UNRESOLVED;
            } else {
                conflict.resolution = hasLocal ? MergeConflict.KEEP_LOCAL : MergeConflict.KEEP_IMPORTED;
            }

            conflicts.add(conflict);
        }

        return conflicts;
    }

    private static void buildLookups(List<Question> questions,
                                     Map<String, Question> byTag,
                                     Map<String, Question> byText) {
        for (Question q : questions) {
            if (q.getTag() != null && !q.getTag().isEmpty())
                byTag.put(q.getTag(), q);
            if (q.getQuestionText() != null)
                byText.put(q.getQuestionText().toLowerCase(Locale.ROOT).trim(), q);
            // Recurse into sub-questions
            buildLookups(q.getSubQuestions(), byTag, byText);
        }
    }

    private static Question resolve(ParsedRow row,
                                    Map<String, Question> byTag,
                                    Map<String, Question> byText) {
        if (!row.tag.isEmpty()) {
            Question q = byTag.get(row.tag);
            if (q != null) return q;
        }
        if (!row.questionText.isEmpty()) {
            return byText.get(row.questionText.toLowerCase(Locale.ROOT).trim());
        }
        return null;
    }

    /**
     * Matches CSV attachment names (original filename, without timestamp prefix) to
     * the actual temp files extracted from the ZIP.
     */
    private static List<String> resolveAttachmentPaths(List<String> attNames,
                                                       Map<String, File> mediaFiles) {
        List<String> paths = new ArrayList<>();
        for (String attName : attNames) {
            // Exact match first
            if (mediaFiles.containsKey(attName)) {
                paths.add(mediaFiles.get(attName).getAbsolutePath());
                continue;
            }
            // The ZIP entry name may have a timestamp prefix; strip it and compare
            for (Map.Entry<String, File> e : mediaFiles.entrySet()) {
                String zipName = e.getKey();
                int sep = zipName.indexOf('_');
                String original = sep > 0 ? zipName.substring(sep + 1) : zipName;
                if (original.equals(attName)) {
                    paths.add(e.getValue().getAbsolutePath());
                    break;
                }
            }
        }
        return paths;
    }

    // -------------------------------------------------------------------------
    // Merge bundle building (called on background thread)
    // -------------------------------------------------------------------------

    /**
     * Converts resolved conflicts into Answer + MediaAttachment lists ready for DB insert.
     * Also copies imported media files to permanent storage.
     */
    public static MergeBundle buildMergeBundle(List<MergeConflict> conflicts,
                                               long targetSurveyId,
                                               Context ctx) {
        MergeBundle bundle = new MergeBundle();
        File destDir = ctx.getExternalFilesDir(null);

        for (MergeConflict c : conflicts) {
            // Answer
            switch (c.resolution) {
                case MergeConflict.KEEP_IMPORTED:
                    if (c.importedAnswer != null) {
                        Answer a = copyAnswer(c.importedAnswer, targetSurveyId);
                        bundle.answers.add(a);
                    }
                    break;
                case MergeConflict.COMBINE: {
                    Answer a = new Answer();
                    a.surveyId   = targetSurveyId;
                    a.questionId = c.question.getId();
                    a.answerValue = combineValues(c);
                    a.remarks     = combineRemarks(c);
                    bundle.answers.add(a);
                    break;
                }
                case MergeConflict.KEEP_LOCAL:
                default:
                    // Nothing to change for the answer value
                    break;
            }

            // Attachments: always copy imported ones regardless of answer resolution
            if (destDir != null) {
                File geomDir = new File(ctx.getFilesDir(), "geom");
                geomDir.mkdirs();
                for (String tempPath : c.importedAttachmentPaths) {
                    File src = new File(tempPath);
                    if (!src.exists()) continue;
                    String mediaType = guessMediaType(src.getName());
                    File targetDir = "GEOMETRY".equals(mediaType) ? geomDir : destDir;
                    File dest = uniqueDest(targetDir, src.getName());
                    try {
                        copyFile(src, dest);
                        MediaAttachment att = new MediaAttachment();
                        att.surveyId   = targetSurveyId;
                        att.questionId = c.question.getId();
                        att.filePath   = dest.getAbsolutePath();
                        att.uri        = android.net.Uri.fromFile(dest).toString();
                        att.mediaType  = mediaType;
                        bundle.attachments.add(att);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to copy attachment: " + tempPath, e);
                    }
                }
            }
        }

        return bundle;
    }

    private static Answer copyAnswer(Answer src, long newSurveyId) {
        Answer a = new Answer();
        a.surveyId   = newSurveyId;
        a.questionId = src.questionId;
        a.answerValue = src.answerValue;
        a.remarks     = src.remarks;
        return a;
    }

    // -------------------------------------------------------------------------
    // Combine logic
    // -------------------------------------------------------------------------

    public static String combineValues(MergeConflict c) {
        String local    = c.localAnswer    != null && c.localAnswer.answerValue    != null ? c.localAnswer.answerValue    : "";
        String imported = c.importedAnswer != null && c.importedAnswer.answerValue != null ? c.importedAnswer.answerValue : "";

        switch (c.question.getAnswerType()) {
            case "TEXT":
                if (local.isEmpty()) return imported;
                if (imported.isEmpty()) return local;
                return local + "\n---\n" + imported;

            case "MULTIPLE_CHOICE_MULTI": {
                Set<String> combined = new LinkedHashSet<>();
                if (!local.isEmpty())    Collections.addAll(combined, local.split("\\|"));
                if (!imported.isEmpty()) Collections.addAll(combined, imported.split("\\|"));
                return String.join("|", combined);
            }

            case "COMPOUND": {
                // Field-level merge: keep local value; fill blanks from imported
                Map<String, String> localFields    = parseCompound(local);
                Map<String, String> importedFields = parseCompound(imported);
                for (Map.Entry<String, String> e : importedFields.entrySet()) {
                    localFields.putIfAbsent(e.getKey(), e.getValue());
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : localFields.entrySet()) {
                    if (sb.length() > 0) sb.append("||");
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
                return sb.toString();
            }

            case "GEOMETRY": {
                try {
                    JSONArray arr = new JSONArray();
                    if (!local.isEmpty()) {
                        JSONArray la = new JSONArray(local);
                        for (int i = 0; i < la.length(); i++) arr.put(la.get(i));
                    }
                    if (!imported.isEmpty()) {
                        JSONArray ia = new JSONArray(imported);
                        for (int i = 0; i < ia.length(); i++) arr.put(ia.get(i));
                    }
                    return arr.toString();
                } catch (JSONException e) {
                    Log.e(TAG, "Error combining GEOMETRY", e);
                    return local;
                }
            }

            default:
                return local;
        }
    }

    public static String combineRemarks(MergeConflict c) {
        String local    = c.localAnswer    != null && c.localAnswer.remarks    != null ? c.localAnswer.remarks    : "";
        String imported = c.importedAnswer != null && c.importedAnswer.remarks != null ? c.importedAnswer.remarks : "";
        if (local.isEmpty()) return imported;
        if (imported.isEmpty()) return local;
        return local + "\n---\n" + imported;
    }

    private static Map<String, String> parseCompound(String value) {
        Map<String, String> map = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return map;
        for (String part : value.split("\\|\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) map.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    public static String guessMediaType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp")) return "PHOTO";
        if (lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv")
                || lower.endsWith(".avi")) return "VIDEO";
        if (lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".wav")
                || lower.endsWith(".m4a") || lower.endsWith(".ogg")) return "AUDIO";
        if (lower.endsWith(".geom")) return "GEOMETRY";
        return "FILE";
    }

    private static File uniqueDest(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        String ext  = name.contains(".") ? name.substring(name.lastIndexOf('.'))    : "";
        int i = 1;
        while (dest.exists()) dest = new File(dir, base + "_" + i++ + ext);
        return dest;
    }

    public static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }

    private static void copyFile(File src, File dest) throws IOException {
        try (FileInputStream fis  = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) > 0) baos.write(buf, 0, len);
        return baos.toByteArray();
    }
}
