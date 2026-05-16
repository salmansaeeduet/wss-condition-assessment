package com.example.cref_wss_01;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
                        t.length > 9 ? strip(t[9]) : "",
                        categories, questionById, subsByParentId);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private static void parseXlsx(AssetManager assetManager, String fileName, String sheetName,
            Map<String, CategoryItem> categories, Map<Integer, Question> questionById,
            Map<Integer, List<Question>> subsByParentId) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(assetManager.open(fileName))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();
                if (name.equals("xl/workbook.xml") ||
                        name.equals("xl/_rels/workbook.xml.rels") ||
                        name.equals("xl/sharedStrings.xml") ||
                        name.startsWith("xl/worksheets/")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                    entries.put(name, baos.toByteArray());
                }
                zis.closeEntry();
            }
        }

        String rId = xlsxSheetRId(entries.get("xl/workbook.xml"), sheetName);
        if (rId == null) throw new IOException("Sheet '" + sheetName + "' not found in " + fileName);

        String target = xlsxRelTarget(entries.get("xl/_rels/workbook.xml.rels"), rId);
        if (target == null) throw new IOException("Relationship not found for rId: " + rId);

        byte[] sheetData = entries.get("xl/" + target);
        if (sheetData == null) throw new IOException("Sheet file not found: xl/" + target);

        List<String> sst = new ArrayList<>();
        byte[] sstData = entries.get("xl/sharedStrings.xml");
        if (sstData != null) sst = xlsxSharedStrings(sstData);

        xlsxRows(sheetData, sst, categories, questionById, subsByParentId);
    }

    private static Document xlsxParseDom(byte[] data) throws IOException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new IOException("XML parse error: " + e.getMessage(), e);
        }
    }

    private static String xlsxSheetRId(byte[] workbookXml, String sheetName) throws IOException {
        if (workbookXml == null) return null;
        NodeList sheets = xlsxParseDom(workbookXml).getElementsByTagName("sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element el = (Element) sheets.item(i);
            if (sheetName.equals(el.getAttribute("name"))) {
                String rId = el.getAttribute("r:id");
                if (rId.isEmpty()) rId = el.getAttribute("id");
                return rId;
            }
        }
        return null;
    }

    private static String xlsxRelTarget(byte[] relsXml, String rId) throws IOException {
        if (relsXml == null) return null;
        NodeList rels = xlsxParseDom(relsXml).getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element el = (Element) rels.item(i);
            if (rId.equals(el.getAttribute("Id"))) return el.getAttribute("Target");
        }
        return null;
    }

    private static List<String> xlsxSharedStrings(byte[] sstXml) throws IOException {
        List<String> result = new ArrayList<>();
        NodeList sis = xlsxParseDom(sstXml).getElementsByTagName("si");
        for (int i = 0; i < sis.getLength(); i++) {
            NodeList tNodes = ((Element) sis.item(i)).getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < tNodes.getLength(); j++) sb.append(tNodes.item(j).getTextContent());
            result.add(sb.toString());
        }
        return result;
    }

    private static void xlsxRows(byte[] sheetXml, List<String> sst,
            Map<String, CategoryItem> categories, Map<Integer, Question> questionById,
            Map<Integer, List<Question>> subsByParentId) throws IOException {
        NodeList rowNodes = xlsxParseDom(sheetXml).getElementsByTagName("row");
        boolean firstRow = true;
        for (int i = 0; i < rowNodes.getLength(); i++) {
            if (firstRow) { firstRow = false; continue; }
            Element rowEl = (Element) rowNodes.item(i);
            NodeList cells = rowEl.getElementsByTagName("c");
            Map<Integer, String> cols = new TreeMap<>();
            for (int j = 0; j < cells.getLength(); j++) {
                Element c = (Element) cells.item(j);
                int col = xlsxColIndex(c.getAttribute("r"));
                String type = c.getAttribute("t");
                String val = "";
                NodeList vNodes = c.getElementsByTagName("v");
                if ("s".equals(type) && vNodes.getLength() > 0) {
                    int idx = (int) Double.parseDouble(vNodes.item(0).getTextContent().trim());
                    val = idx < sst.size() ? sst.get(idx) : "";
                } else if ("inlineStr".equals(type)) {
                    NodeList tNodes = c.getElementsByTagName("t");
                    if (tNodes.getLength() > 0) val = tNodes.item(0).getTextContent();
                } else if (vNodes.getLength() > 0) {
                    val = vNodes.item(0).getTextContent().trim();
                }
                cols.put(col, val);
            }
            String idStr = cols.getOrDefault(0, "").trim();
            if (idStr.isEmpty()) continue;
            try {
                processRow(idStr,
                    cols.getOrDefault(1, "").trim(), cols.getOrDefault(2, "").trim(),
                    cols.getOrDefault(3, "").trim(), cols.getOrDefault(4, "").trim(),
                    cols.getOrDefault(5, "").trim(), cols.getOrDefault(6, "").trim(),
                    cols.getOrDefault(7, "").trim(), cols.getOrDefault(8, "").trim(),
                    cols.getOrDefault(9, "").trim(),
                    categories, questionById, subsByParentId);
            } catch (NumberFormatException ignored) {}
        }
    }

    private static int xlsxColIndex(String cellRef) {
        int col = 0;
        for (char c : cellRef.toCharArray()) {
            if (c < 'A' || c > 'Z') break;
            col = col * 26 + (c - 'A' + 1);
        }
        return col - 1;
    }

    private static void processRow(String idStr, String categoryName, String subCatName,
            String questionText, String answerType, String answerOptions, String explanation,
            String parentIdStr, String triggerValue, String tag,
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
            case "LINE":
            case "POLYGON":
                answerType    = "GEOMETRY";
                answerOptions = "";
                break;
        }

        Question question = new Question(id, categoryName, subCatName, questionText,
                answerType, answerOptions, explanation, parentId, triggerValue, tag);
        if ("GEOMETRY".equals(answerType) && !answerOptions.isEmpty()) {
            question.setGeomLabel(answerOptions.split("\\|")[0].trim());
        }
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
