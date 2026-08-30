package ru.playsoftware.j2meloader.util;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TranslationManager {
    private static final String TAG = "TranslationManager";
    private static final Map<String, String> translationMap = new HashMap<>();
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    private static File jsonFile;
    private static boolean isDumpMode = true;
    private static volatile boolean hasNewDataToSave = false;
    private static ScheduledExecutorService saveScheduler;

    public static void init(File gameDir) {
        if (gameDir == null) return;
        jsonFile = new File(gameDir, "translation.json");
        loadTranslation();

        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 3, 3, TimeUnit.SECONDS);
        }
    }

    public static void loadTranslation() {
        if (jsonFile == null || !jsonFile.exists()) {
            Log.w(TAG, "Translation file not found: " + (jsonFile != null ? jsonFile.getAbsolutePath() : "null"));
            return;
        }
        
        try (FileReader reader = new FileReader(jsonFile)) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            if (sb.length() == 0) {
                Log.w(TAG, "Translation file is empty");
                return;
            }
            
            String jsonStr = sb.toString();
            Log.d(TAG, "Loading translations from: " + jsonFile.getAbsolutePath());
            Log.d(TAG, "JSON content length: " + jsonStr.length());
            
            JSONObject json = new JSONObject(jsonStr);
            
            // Format: { "translations": { "key": "value" } }
            if (json.has("translations")) {
                JSONObject translationsObj = json.getJSONObject("translations");
                Iterator<String> keys = translationsObj.keys();
                int count = 0;
                while (keys.hasNext()) {
                    String key = keys.next();
                    String val = translationsObj.getString(key);
                    translationMap.put(key, val);
                    dumpedStrings.put(key, val);
                    count++;
                }
                Log.i(TAG, "✅ Loaded " + count + " translations from file");
            } else {
                // Fallback: langsung parse root sebagai translations
                Log.w(TAG, "No 'translations' key found, parsing root as translations");
                Iterator<String> keys = json.keys();
                int count = 0;
                while (keys.hasNext()) {
                    String key = keys.next();
                    String val = json.getString(key);
                    translationMap.put(key, val);
                    dumpedStrings.put(key, val);
                    count++;
                }
                Log.i(TAG, "✅ Loaded " + count + " translations from root");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading translation file: " + jsonFile.getAbsolutePath(), e);
        }
    }

    /**
     * Load translations from file (public method untuk dipanggil dari MicroActivity)
     */
    public static void loadTranslationsFromFile(File file) {
        if (file == null || !file.exists()) {
            Log.w(TAG, "Translation file not found: " + (file != null ? file.getAbsolutePath() : "null"));
            return;
        }
        // Clear existing translations
        translationMap.clear();
        dumpedStrings.clear();
        
        // Set jsonFile
        jsonFile = file;
        loadTranslation();
    }

    private static synchronized void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave || jsonFile == null) {
            return;
        }
        
        try {
            JSONObject json = new JSONObject();
            
            // Format: { "translations": { "key": "value" } }
            JSONObject translationsObj = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                translationsObj.put(entry.getKey(), entry.getValue());
            }
            json.put("translations", translationsObj);
            json.put("version", "1.0");
            json.put("timestamp", System.currentTimeMillis());
            
            // Write to file
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(json.toString(4));
            }
            
            hasNewDataToSave = false;
            Log.d(TAG, "✅ Dump saved to: " + jsonFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving dump", e);
        }
    }

    public static void shutdownScheduler() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdownNow();
            saveScheduler = null;
            Log.d(TAG, "Scheduler shut down");
        }
    }

    public static String processString(String original) {
        if (original == null || original.trim().isEmpty()) {
            return original;
        }

        // 1. Cek terjemahan
        if (translationMap.containsKey(original)) {
            return translationMap.get(original);
        }

        // 2. Jika belum ada di kamus dan mode dump aktif, daftarkan teks baru
        if (isDumpMode && !dumpedStrings.containsKey(original)) {
            dumpedStrings.put(original, original);
            hasNewDataToSave = true;
        }

        return original;
    }

    /**
     * Get all translations
     */
    public static Map<String, String> getTranslations() {
        return new HashMap<>(translationMap);
    }

    /**
     * Get all dumped strings
     */
    public static Map<String, String> getDumpedStrings() {
        return new HashMap<>(dumpedStrings);
    }

    /**
     * Clear all translations
     */
    public static void clearTranslations() {
        translationMap.clear();
    }
            }
