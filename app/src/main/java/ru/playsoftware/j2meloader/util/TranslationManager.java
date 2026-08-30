package ru.playsoftware.j2meloader.util;

import org.json.JSONObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TranslationManager {
    private static final Map<String, String> translationMap = new HashMap<>();
    private static final Map<String, String> dumpedStrings = new HashMap<>();
    private static File jsonFile;
    private static boolean isDumpMode = true; // Set true untuk menyimpan teks baru ke JSON

    public static void init(File gameDir) {
        jsonFile = new File(gameDir, "translation.json");
        loadTranslation();
    }

    public static void loadTranslation() {
        if (jsonFile == null || !jsonFile.exists()) return;
        try (FileReader reader = new FileReader(jsonFile)) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            JSONObject json = new JSONObject(sb.toString());
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                translationMap.put(key, json.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void saveDump() {
        if (!isDumpMode || dumpedStrings.isEmpty() || jsonFile == null) return;
        try {
            JSONObject json = jsonFile.exists() ? new JSONObject(dumpedStrings) : new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                if (!json.has(entry.getKey())) {
                    json.put(entry.getKey(), entry.getValue());
                }
            }
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(json.toString(4));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String processString(String original) {
        if (original == null || original.trim().isEmpty()) return original;

        if (isDumpMode && !dumpedStrings.containsKey(original)) {
            dumpedStrings.put(original, original);
            saveDump();
        }

        if (translationMap.containsKey(original)) {
            return translationMap.get(original);
        }

        return original;
    }
}

