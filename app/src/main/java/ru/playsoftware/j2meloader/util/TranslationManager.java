package ru.playsoftware.j2meloader.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TranslationManager {
    private static final Map<String, String> translationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> reverseTranslationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    
    private static final Map<String, Boolean> sedangDiterjemahkan = new ConcurrentHashMap<>();
    
    private static File translationFile;
    private static File dumpFile;
    
    private static boolean isDumpMode = true; 
    private static boolean autoTranslateEnabled = true;
    
    private static final AtomicBoolean hasNewDataToSave = new AtomicBoolean(false);
    private static final AtomicBoolean hasNewTranslationToSave = new AtomicBoolean(false);
    
    private static ScheduledExecutorService saveScheduler;
    private static ScheduledExecutorService translationSaveScheduler;
    private static final ExecutorService translateExecutor = Executors.newSingleThreadExecutor();

    public static void init(File gameDir) {
        if (gameDir == null) return;
        
        translationFile = new File(gameDir, "translation.json");
        dumpFile = new File(gameDir, "dump.json");
        
        loadTranslation();
        loadExistingDump();

        if (isDumpMode && saveScheduler == null) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 500, 500, TimeUnit.MILLISECONDS);
        }

        if (translationSaveScheduler == null) {
            translationSaveScheduler = Executors.newSingleThreadScheduledExecutor();
            translationSaveScheduler.scheduleWithFixedDelay(TranslationManager::saveTranslationInternal, 1000, 1000, TimeUnit.MILLISECONDS);
        }
    }

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

    public static void saveDumpInternal() {
        if (!isDumpMode || !hasNewDataToSave.compareAndSet(true, false) || dumpFile == null) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            File tempFile = new File(dumpFile.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(json.toString(4));
            }
            
            if (tempFile.exists()) {
                if (dumpFile.exists()) dumpFile.delete();
                tempFile.renameTo(dumpFile);
            }
        } catch (Exception e) {
            hasNewDataToSave.set(true);
            e.printStackTrace();
        }
    }

    public static void saveTranslationInternal() {
        if (!hasNewTranslationToSave.compareAndSet(true, false) || translationFile == null) return;
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : translationMap.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            File tempFile = new File(translationFile.getAbsolutePath() + ".tmp");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(json.toString(4));
            }
            
            if (tempFile.exists()) {
                if (translationFile.exists()) translationFile.delete();
                tempFile.renameTo(translationFile);
            }
        } catch (Exception e) {
            hasNewTranslationToSave.set(true);
            e.printStackTrace();
        }
    }

    public static void shutdownScheduler() {
        saveDumpInternal();
        saveTranslationInternal();

        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdownNow();
            saveScheduler = null;
        }
        if (translationSaveScheduler != null && !translationSaveScheduler.isShutdown()) {
            translationSaveScheduler.shutdownNow();
            translationSaveScheduler = null;
        }
        if (translateExecutor != null && !translateExecutor.isShutdown()) {
            translateExecutor.shutdownNow();
        }
    }

    public static String processString(String original) {
        if (original == null) return original;
        
        if (original.isEmpty() || original.length() <= 1 || original.matches("^\\d+$")) {
            return original;
        }

        if (translationMap.containsKey(original)) {
            if (dumpedStrings.containsKey(original)) {
                dumpedStrings.remove(original);
                hasNewDataToSave.set(true);
            }
            return translationMap.get(original);
        }

        if (reverseTranslationMap.containsKey(original)) {
            return original;
        }

        if (autoTranslateEnabled && !sedangDiterjemahkan.containsKey(original)) {
            sedangDiterjemahkan.put(original, true);
            translateExecutor.execute(() -> terjemahkanViaAPI(original));
        }

        if (isDumpMode) {
            for (String key : dumpedStrings.keySet()) {
                if (original.length() > key.length() && original.contains(key)) {
                    dumpedStrings.remove(key);
                    hasNewDataToSave.set(true);
                }
            }
            
            boolean isSubText = false;
            for (String key : dumpedStrings.keySet()) {
                if (key.length() >= original.length() && key.contains(original)) {
                    isSubText = true;
                    break;
                }
            }
            
            if (!isSubText && !dumpedStrings.containsKey(original)) {
                dumpedStrings.put(original, original);
                hasNewDataToSave.set(true);
            }
        }

        return original;
    }

    private static void terjemahkanViaAPI(String teks) {
        try {
            Thread.sleep(300);

            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=" 
                    + URLEncoder.encode(teks, "UTF-8");
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONArray sentences = jsonArray.getJSONArray(0);
                    StringBuilder translatedResult = new StringBuilder();

                    for (int i = 0; i < sentences.length(); i++) {
                        JSONArray sentence = sentences.getJSONArray(i);
                        translatedResult.append(sentence.getString(0));
                    }

                    String hasilTranslate = translatedResult.toString();

                    if (!hasilTranslate.isEmpty() && !hasilTranslate.equals(teks)) {
                        translationMap.put(teks, hasilTranslate);
                        reverseTranslationMap.put(hasilTranslate, teks);

                        hasNewTranslationToSave.set(true);

                        if (dumpedStrings.containsKey(teks)) {
                            dumpedStrings.remove(teks);
                            hasNewDataToSave.set(true);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sedangDiterjemahkan.remove(teks);
        }
    }
}
