package com.example.cref_wss_01;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuestionnaireParser {

    public static List<CategoryItem> parseHierarchical(Context context, String fileName) {
        Map<String, CategoryItem> categories = new LinkedHashMap<>();
        Map<Integer, Question> questionById = new LinkedHashMap<>();
        Map<Integer, List<Question>> subsByParentId = new LinkedHashMap<>();

        AssetManager assetManager = context.getAssets();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(fileName)))) {
            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (tokens.length < 6) continue;

                try {
                    int id = Integer.parseInt(tokens[0].trim());
                    String categoryName  = tokens[1].trim().replaceAll("^\"|\"$", "");
                    String subCatName    = tokens[2].trim().replaceAll("^\"|\"$", "");
                    String questionText  = tokens[3].trim().replaceAll("^\"|\"$", "");
                    String answerType    = tokens[4].trim().replaceAll("^\"|\"$", "");
                    String answerOptions = tokens[5].trim().replaceAll("^\"|\"$", "");
                    String explanation   = tokens.length > 6 ? tokens[6].trim().replaceAll("^\"|\"$", "") : "";
                    int parentId         = (tokens.length > 7 && !tokens[7].trim().isEmpty())
                                          ? Integer.parseInt(tokens[7].trim()) : 0;
                    String triggerValue  = tokens.length > 8 ? tokens[8].trim().replaceAll("^\"|\"$", "") : "";

                    // Backward compat: remap legacy types
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
                        // Sub-question: defer adding to parent; exclude from nav hierarchy
                        subsByParentId.computeIfAbsent(parentId, k -> new ArrayList<>()).add(question);
                        continue;
                    }

                    // Top-level question: add to category/sub-category hierarchy
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

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Link sub-questions to their parents
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
}
