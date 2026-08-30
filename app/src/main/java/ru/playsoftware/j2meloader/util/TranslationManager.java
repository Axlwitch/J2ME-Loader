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
    // Membaca kamus terjemahan dari translation.json ("pay": "bayar")
    private static final Map<String, String> translationMap = new HashMap<>();
    
    // Menampung teks mentah yang ditangkap untuk disimpan ke dump.json
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    
    private static File translationFile;
    private static File dumpFile;
    
    private static boolean isDumpMode = true; 
    private static volatile boolean hasNewDataToSave = false;
    private static ScheduledExecutorService saveScheduler;

    public static void init(File gameDir) {
        if (gameDir == null) return;
        
        translationFile = new File(gameDir, "translation.json");
        dumpFile = new File(gameDir, "dump.json");
        
        loadTranslation();
        loadExistingDump();

        // Auto-save dump.json per 3 detik jika ada string baru
        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 3, 3, TimeUnit.SECONDS);
        }
    }

    // 1. Memuat kamus terjemahan (translation.json)
    public static void loadTranslation() {
        if (translationFile == null || !translationFile.exists()) return;
        try (FileReader reader = new FileReader(translationFile)) {
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
                translationMap.put(key, json.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. Memuat dump lama agar data tidak terhapus saat game di-restart
    private static void loadExistingDump() {
        if (dumpFile == null || !dumpFile.exists()) return;
        try (FileReader reader = new FileReader(dumpFile)) {
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
                dumpedStrings.put(key, json.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. Menyimpan teks mentah ke dump.json
    private static synchronized void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave || dumpFile == null) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            try (FileWriter writer = new FileWriter(dumpFile)) {
                writer.write(json.toString(4)); // Indentasi 4 spasi agar rapi dibaca
            }
            hasNewDataToSave = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void shutdownScheduler() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdownNow();
            saveScheduler = null;
        }
    }

    public static String processString(String original) {
        if (original == null || original.trim().isEmpty()) return original;

        // 1. Cek apakah ada di translation.json (kamus)
        if (translationMap.containsKey(original)) {
            return translationMap.get(original);
        }

        // 2. Jika tidak ada di kamus dan belum ada di dump.json, catat ke dump.json
        if (isDumpMode && !dumpedStrings.containsKey(original)) {
            // Value diisi string kosong "" agar mudah di-edit di dashboard/editor kamu
            dumpedStrings.put(original, "");
            hasNewDataToSave = true;
        }

        return original;
    }
}
