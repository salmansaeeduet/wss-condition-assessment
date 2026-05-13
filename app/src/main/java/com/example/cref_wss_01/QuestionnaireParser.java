package com.example.cref_wss_01;

import android.content.Context;
import android.content.res.AssetManager;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class QuestionnaireParser {

    public static List<CategoryItem> parseHierarchical(Context context, String fileName, String sheetName) {
        Map<String, CategoryItem> categories = new LinkedHashMap<>();
        Map<Integer, Question> questionById = new LinkedHashMap<>();
        Map<Integer, List<Question>> subsByParentId = new LinkedHashMap<>();

        AssetManager assetManager = context.getAssets();
        try {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm")) {
                parseXlsx(assetManager, fileName, sheetName, categories, questionById, subsByParentId);
            } else {
                parseCsv(assetManager, fileName, categories, questionById, subsByParentId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (Map.Entry<Integer, List<Question>> entry : subsByParentId.entrySet()) {
            Question parent = questionById.get(entry.getKey());
            if (parent != null) {
                for (Question sub : entry.getValue()) {
                    parent.addSubQuestion(sub);
                }
            }
        }

        return new ArrayList<>(categories.values());
    }

    private static void parseCsv(AssetManager assetManager, String fileName,
            Map<String, CategoryItem> categories, Map<Integer, Question> questionById,
            Map<Integer, List<Question>> subsByParentId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(fileName)))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] t = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (t.length < 6) continue;
                try {
                    processRow(
                        strip(t[0]), strip(t[1]), strip(t[2]), strip(t[3]), strip(t[4]), strip(t[5]),
                        t.length > 6 ? strip(t[6]) : "",
                        t.length > 7 ? strip(t[7]) : "",
                        t.length > 8 ? strip(t[8]) : "",
                        categories, questionById, subsByParentId);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static void parseXlsx(AssetManager assetManager, String fileName, String sheetName,
            Map<String, CategoryItem> categories, Map<Integer, Question> questionById,
            Map<Integer, List<Question>> subsByParentId) throws IOException {
        try (InputStream is = assetManager.open(fileName);
             ReadableWorkbook wb = new ReadableWorkbook(is)) {
            Sheet sheet = wb.findSheet(sheetName)
                    .orElseThrow(() -> new IOException("Sheet '" + sheetName + "' not found in " + fileName));
            List<Row> rows = new ArrayList<>();
            try (Stream<Row> rowStream = sheet.openStream()) {
                rowStream.forEach(rows::add);
            }
            for (int i = 1; i < rows.size(); i++) { // skip header row 0
                Row row = rows.get(i);
                String idStr = row.getCellText(0).trim();
                if (idStr.isEmpty()) continue;
                try {
                    processRow(
                        idStr,
                        row.getCellText(1).trim(), row.getCellText(2).trim(),
                        row.getCellText(3).trim(), row.getCellText(4).trim(),
                        row.getCellText(5).trim(), row.getCellText(6).trim(),
                        row.getCellText(7).trim(), row.getCellText(8).trim(),
                        categories, questionById, subsByParentId);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static void processRow(String idStr, String categoryName, String subCatName,
            String questionText, String answerType, String answerOptions, String explanation,
            String parentIdStr, String triggerValue,
            Map<String, CategoryItem> categories, Map<Integer, Question> questionById,
            Map<Integer, List<Question>> subsByParentId) {
        int id = (int) Double.parseDouble(idStr);
        int parentId = parentIdStr.isEmpty() ? 0 : (int) Double.parseDouble(parentIdStr);

        switch (answerType) {
            case "YES_NO":
                answerType    = "MULTIPLE_CHOICE_SINGLE";
                answerOptions = "YES|NO";
                break;
            case "INTEGER":
                answerType    = "NUMBER";
                answerOptions = "INTEGER" + (answerOptions.isEmpty() ? "" : "|" + answerOptions);
                break;
            case "DECIMAL":
                answerType    = "NUMBER";
                answerOptions = "DECIMAL" + (answerOptions.isEmpty() ? "" : "|" + answerOptions);
                break;
            case "PERCENTAGE":
                answerType    = "NUMBER";
                answerOptions = "PERCENTAGE" + (answerOptions.isEmpty() ? "" : "|" + answerOptions);
                break;
        }

        Question question = new Question(id, categoryName, subCatName, questionText,
                answerType, answerOptions, explanation, parentId, triggerValue);
        questionById.put(id, question);

        if (parentId > 0) {
            subsByParentId.computeIfAbsent(parentId, k -> new ArrayList<>()).add(question);
            return;
        }

        CategoryItem categoryItem = categories.computeIfAbsent(categoryName, CategoryItem::new);

        SubCategoryItem subCategoryItem = null;
        for (SubCategoryItem sc : categoryItem.getSubCategories()) {
            if (sc.getName().equals(subCatName)) { subCategoryItem = sc; break; }
        }
        if (subCategoryItem == null) {
            subCategoryItem = new SubCategoryItem(subCatName);
            categoryItem.getSubCategories().add(subCategoryItem);
        }

        subCategoryItem.getQuestions().add(new QuestionItem(question));
    }

    private static String strip(String s) {
        return s.trim().replaceAll("^\"|\"$", "");
    }
}
