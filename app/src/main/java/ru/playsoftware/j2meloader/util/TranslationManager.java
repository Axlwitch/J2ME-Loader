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
    // Memuat terjemahan dari translation.json ("TEKS_ASLI": "TEKS_TERJEMAHAN")
    private static final Map<String, String> translationMap = new HashMap<>();
    
    // Reverse map untuk mengecek apakah suatu string adalah hasil terjemahan ("TEKS_TERJEMAHAN": "TEKS_ASLI")
    private static final Map<String, String> reverseTranslationMap = new HashMap<>();
    
    // Menampung hasil dump untuk dump.json ("TEKS_ASLI": "TEKS_ASLI")
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

        // Auto-save dump.json per 3 detik jika ada data baru
        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 3, 3, TimeUnit.SECONDS);
        }
    }

    // 1. Memuat file translation.json & membuat reverse map pencegah dump terjemahan
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
                String val = json.getString(key);
                translationMap.put(key, val);
                reverseTranslationMap.put(val, key); // Simpan hasil terjemahan untuk filter
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. Memuat dump.json yang sudah ada sebelumnya
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

    // 3. Menyimpan dump ke file secara aman
    private static synchronized void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave || dumpFile == null) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            try (FileWriter writer = new FileWriter(dumpFile)) {
                writer.write(json.toString(4)); // Formatting JSON 4 spasi
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

    // 4. Proses Pengecekan Teks Utama
    public static String processString(String original) {
        if (original == null) return original;
        
        String trimmed = original.trim();

        // Filter: Abaikan teks kosong, angka murni, atau teks 1 karakter
        if (trimmed.isEmpty() || trimmed.length() <= 1 || trimmed.matches("^\\d+$")) {
            return original;
        }

        // --- ATURAN 1: BILA TEKS SUDAH ADA DI KAMUS (translation.json) ---
        if (translationMap.containsKey(trimmed)) {
            // Bersihkan dari dump jika teks ini ternyata pernah masuk ke dump.json
            if (dumpedStrings.containsKey(trimmed)) {
                dumpedStrings.remove(trimmed);
                hasNewDataToSave = true;
            }
            return original.replace(trimmed, translationMap.get(trimmed));
        }

        // --- ATURAN 2: CEGAH DUMP TEKS HASIL TERJEMAHAN ---
        // Jika string ini adalah 'Value' (Bahasa Indonesia) dari kamus, jangan dimasukkan ke dump.json
        if (reverseTranslationMap.containsKey(trimmed)) {
            return original;
        }

        // --- ATURAN 3: DUMP TEKS MENTAH DENGAN FILTER SUBSTRING ---
        if (isDumpMode) {
            // 3a. Hapus substring yang lebih pendek dari daftar dump jika kalimat yang lebih panjang muncul
            for (String key : dumpedStrings.keySet()) {
                if (trimmed.length() > key.length() && trimmed.contains(key)) {
                    dumpedStrings.remove(key);
                    hasNewDataToSave = true;
                }
            }

            // 3b. Cek apakah teks ini merupakan pecahan/potongan dari teks panjang yang sudah di-dump
            boolean isSubText = false;
            for (String key : dumpedStrings.keySet()) {
                if (key.length() >= trimmed.length() && key.contains(trimmed)) {
                    isSubText = true;
                    break;
                }
            }

            // 3c. Jika benar-benar teks baru, simpan dengan format ("TEKS_ASLI": "TEKS_ASLI")
            if (!isSubText && !dumpedStrings.containsKey(trimmed)) {
                dumpedStrings.put(trimmed, trimmed);
                hasNewDataToSave = true;
            }
        }

        return original;
    }
}
