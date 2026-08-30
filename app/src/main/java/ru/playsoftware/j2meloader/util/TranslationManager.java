package ru.playsoftware.j2meloader.util;

import org.json.JSONObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationManager {
    // Penggunaan ConcurrentHashMap agar thread-safe saat dibaca & ditulis bersamaan
    private static final Map<String, String> translationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> reverseTranslationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    
    private static File translationFile;
    private static File dumpFile;
    
    private static boolean isDumpMode = true; 
    private static final AtomicBoolean hasNewDataToSave = new AtomicBoolean(false);
    private static ScheduledExecutorService saveScheduler;

    public static void init(File gameDir) {
        if (gameDir == null) return;
        
        translationFile = new File(gameDir, "translation.json");
        dumpFile = new File(gameDir, "dump.json");
        
        loadTranslation();
        loadExistingDump();

        // Interval dipercepat ke 1 detik agar dump responsif menangkap banyak teks tanpa data hilang
        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 1, 1, TimeUnit.SECONDS);
        }
    }

    // 1. Memuat translation.json & reset RAM jika file dihapus/kosong
    public static void loadTranslation() {
        translationMap.clear();
        reverseTranslationMap.clear();

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
                reverseTranslationMap.put(val, key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 2. Memuat dump.json yang tersimpan sebelumnya
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

    // 3. Menulis file dump secara atomik dan non-blocking
    private static void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave.compareAndSet(true, false) || dumpFile == null) return;
        try {
            JSONObject json = new JSONObject();
            // Snapshot cepat dari ConcurrentHashMap
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            // Tulis ke temp file dulu agar tidak corrupted jika aplikasi/game crash mendadak
            File tempFile = new File(dumpFile.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(json.toString(4));
            }
            
            if (tempFile.exists()) {
                if (dumpFile.exists()) dumpFile.delete();
                tempFile.renameTo(dumpFile);
            }
        } catch (Exception e) {
            hasNewDataToSave.set(true); // Re-flag jika gagal tulis file
            e.printStackTrace();
        }
    }

    public static void shutdownScheduler() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveDumpInternal(); // Simpan sisa data sebelum thread mati
            saveScheduler.shutdownNow();
            saveScheduler = null;
        }
    }

    // 4. Logika Pemrosesan & Penyaringan Teks Utama
    public static String processString(String original) {
        if (original == null) return original;
        
        String trimmed = original.trim();

        // Filter karakter sampah, angka murni, atau tanda baca tunggal
        if (trimmed.isEmpty() || trimmed.length() <= 1 || trimmed.matches("^[0-9%\\s\\.,!\\?_\\-+:=]+$")) {
            return original;
        }

        // --- PRIORITAS 1: CEK TRANSLATION ---
        if (translationMap.containsKey(trimmed)) {
            if (dumpedStrings.containsKey(trimmed)) {
                dumpedStrings.remove(trimmed);
                hasNewDataToSave.set(true);
            }
            return original.replace(trimmed, translationMap.get(trimmed));
        }

        // --- PRIORITAS 2: CEGAH DUMP HASIL TERJEMAHAN ---
        if (reverseTranslationMap.containsKey(trimmed)) {
            return original;
        }

        // --- PRIORITAS 3: AKURASI & DUMP SUBSTRING ---
        if (isDumpMode) {
            boolean shouldAdd = true;

            for (String existingKey : dumpedStrings.keySet()) {
                // Jika teks baru LEBIH PANJANG dan memuat potongan lama, hapus potongan lama (Pembersihan Substring)
                if (trimmed.length() > existingKey.length() && trimmed.contains(existingKey)) {
                    dumpedStrings.remove(existingKey);
                    hasNewDataToSave.set(true);
                } 
                // Jika teks baru ADALAH POTONGAN dari teks yang sudah ada di dump, abaikan (Jangan Dump)
                else if (existingKey.length() >= trimmed.length() && existingKey.contains(trimmed)) {
                    shouldAdd = false;
                }
            }

            if (shouldAdd && !dumpedStrings.containsKey(trimmed)) {
                dumpedStrings.put(trimmed, trimmed);
                hasNewDataToSave.set(true);
            }
        }

        return original;
    }
}
