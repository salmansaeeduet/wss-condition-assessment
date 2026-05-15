package com.example.cref_wss_01;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SurveyExporter {

    private static final String TAG = "SurveyExporter";

    /** Returns a content URI (API 29+) or file URI (API < 29) for the exported ZIP, or null on failure. */
    public static Uri export(Context context, SurveyWithAnswers surveyWithAnswers, List<Question> allQuestions) {
        List<RequiredField> fields = RequiredField.parseAll(context, allQuestions);

        String part1 = fields.size() > 0
                ? fields.get(0).getDisplayValue(
                        SurveyListActivity.findAnswerValue(surveyWithAnswers.answers, fields.get(0).id)) : "";
        String part2 = fields.size() > 1
                ? fields.get(1).getDisplayValue(
                        SurveyListActivity.findAnswerValue(surveyWithAnswers.answers, fields.get(1).id)) : "";

        boolean part1Missing = fields.size() > 0 && (part1 == null || part1.isEmpty());
        boolean part2Missing = fields.size() > 1 && (part2 == null || part2.isEmpty());
        if (part1Missing || part2Missing) return null;

        try {
            // Build CSV
            StringBuilder csvData = new StringBuilder();
            csvData.append("Tag,Question,Answer,Remarks,Photo Count,Video Count,Audio Count,Attachments\n");

            for (Question question : allQuestions) {
                Answer answer = findAnswer(surveyWithAnswers.answers, question.getId());
                List<MediaAttachment> attachments = findAttachments(surveyWithAnswers.attachments, question.getId());
                attachments.sort((a1, a2) -> {
                    int typeCompare = getMediaTypeSortOrder(a1.mediaType) - getMediaTypeSortOrder(a2.mediaType);
                    if (typeCompare != 0) return typeCompare;
                    String name1 = new File(a1.filePath != null ? a1.filePath : a1.uri).getName();
                    String name2 = new File(a2.filePath != null ? a2.filePath : a2.uri).getName();
                    return name1.compareTo(name2);
                });

                String tag = question.getTag() != null ? question.getTag() : "";
                csvData.append("\"").append(tag).append("\",");
                csvData.append("\"").append(question.getQuestionText()).append("\",");
                csvData.append("\"").append(answer != null && answer.answerValue != null ? answer.answerValue : "").append("\",");
                csvData.append("\"").append(answer != null && answer.remarks != null ? answer.remarks : "").append("\",");

                long photoCount = attachments.stream().filter(a -> "PHOTO".equals(a.mediaType)).count();
                long videoCount = attachments.stream().filter(a -> "VIDEO".equals(a.mediaType)).count();
                long audioCount = attachments.stream().filter(a -> "AUDIO".equals(a.mediaType)).count();
                csvData.append(photoCount).append(",");
                csvData.append(videoCount).append(",");
                csvData.append(audioCount).append(",");

                for (MediaAttachment attachment : attachments) {
                    String fullName = new File(attachment.filePath != null ? attachment.filePath : attachment.uri).getName();
                    int sep = fullName.indexOf('_');
                    String originalName = sep > -1 ? fullName.substring(sep + 1) : fullName;
                    csvData.append("\"").append(originalName).append("\",");
                }
                csvData.append("\n");
            }

            // Write CSV to a temp file
            File csvFile = new File(context.getCacheDir(), "survey_data.csv");
            FileWriter writer = new FileWriter(csvFile);
            writer.append(csvData.toString());
            writer.flush();
            writer.close();

            // Build ZIP filename
            String timeStamp = new SimpleDateFormat("yyMMddHHmmss", Locale.US).format(new Date());
            String safePart1 = part1.replaceAll("[^a-zA-Z0-9.-]", "_");
            String safePart2 = part2.replaceAll("[^a-zA-Z0-9.-]", "_");
            String baseName = (safePart2.isEmpty() ? safePart1 : safePart1 + "_" + safePart2) + "_" + timeStamp;

            Uri result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                result = writeViaMediaStore(context, csvFile, surveyWithAnswers.attachments, baseName);
            } else {
                result = writeViaFile(context, csvFile, surveyWithAnswers.attachments, baseName);
            }

            csvFile.delete();
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error during export", e);
            return null;
        }
    }

    /** Android 10+: write ZIP directly into the public Documents folder via MediaStore. */
    private static Uri writeViaMediaStore(Context context, File csvFile,
                                          List<MediaAttachment> attachments, String baseName) throws IOException {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".zip");
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/");

        ContentResolver cr = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = cr.insert(collection, cv);
        if (uri == null) throw new IOException("MediaStore insert returned null");

        try (OutputStream os = cr.openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            writeZipContents(zos, csvFile, attachments);
        }
        return uri;
    }

    /** Android 9 and below: write ZIP directly to the public Documents directory. */
    private static Uri writeViaFile(Context context, File csvFile,
                                    List<MediaAttachment> attachments, String baseName) throws IOException {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        documentsDir.mkdirs();
        File zipFile = new File(documentsDir, baseName + ".zip");
        int counter = 0;
        while (zipFile.exists()) zipFile = new File(documentsDir, baseName + " (" + ++counter + ").zip");

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
            writeZipContents(zos, csvFile, attachments);
        }
        return Uri.fromFile(zipFile);
    }

    private static void writeZipContents(ZipOutputStream zos, File csvFile,
                                         List<MediaAttachment> attachments) throws IOException {
        addToZip(zos, csvFile, "survey_data.csv");
        if (attachments != null) {
            for (MediaAttachment attachment : attachments) {
                if (attachment.filePath != null) {
                    File mediaFile = new File(attachment.filePath);
                    if (mediaFile.exists()) addToZip(zos, mediaFile, mediaFile.getName());
                }
            }
        }
    }

    private static Answer findAnswer(List<Answer> answers, int questionId) {
        return answers.stream().filter(a -> a.questionId == questionId).findFirst().orElse(null);
    }

    private static List<MediaAttachment> findAttachments(List<MediaAttachment> attachments, int questionId) {
        List<MediaAttachment> result = new ArrayList<>();
        if (attachments != null) {
            for (MediaAttachment a : attachments) {
                if (a.questionId == questionId) result.add(a);
            }
        }
        return result;
    }

    private static void addToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        byte[] buffer = new byte[1024];
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            zos.putNextEntry(new ZipEntry(entryName));
            int length;
            while ((length = bis.read(buffer)) > 0) zos.write(buffer, 0, length);
            zos.closeEntry();
        }
    }

    private static int getMediaTypeSortOrder(String mediaType) {
        if (mediaType == null) return 4;
        return switch (mediaType) {
            case "PHOTO" -> 1;
            case "VIDEO" -> 2;
            case "AUDIO" -> 3;
            default -> 4;
        };
    }
}
