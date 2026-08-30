package ru.playsoftware.j2meloader.util;

import org.json.JSONObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TranslationManager {
    private static final Map<String, String> translationMap = new HashMap<>();
    // Use ConcurrentHashMap to avoid ConcurrentModificationException from scheduler vs writer
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    private static File jsonFile;
    private static boolean isDumpMode = true; // Set true untuk menyimpan teks baru ke JSON
    private static volatile boolean hasNewDataToSave = false;
    private static ScheduledExecutorService saveScheduler;

    public static void init(File gameDir) {
        if (gameDir == null) return;
        jsonFile = new File(gameDir, "translation.json");
        loadTranslation();

        // Jalankan background worker untuk menyimpan dump per 3 detik sekali jika ada data baru
        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 3, 3, TimeUnit.SECONDS);
        }
    }

    public static void loadTranslation() {
        if (jsonFile == null || !jsonFile.exists()) return;
        try (FileReader reader = new FileReader(jsonFile)) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            if (sb.length() == 0) return;
            
            JSONObject json = new JSONObject(sb.toString());
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = json.getString(key);
                translationMap.put(key, val);
                dumpedStrings.put(key, val);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static synchronized void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave || jsonFile == null) return;
        try {
            // Build JSON from current dumpedStrings snapshot
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(json.toString(4));
            }
            hasNewDataToSave = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Call from lifecycle when the app/game is stopping to avoid thread leak
    public static void shutdownScheduler() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdownNow();
            saveScheduler = null;
        }
    }

    public static String processString(String original) {
        if (original == null || original.trim().isEmpty()) return original;

        // 1. Cek terjemahan terlebih dahulu
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
}
