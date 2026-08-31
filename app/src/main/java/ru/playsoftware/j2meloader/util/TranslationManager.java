package ru.playsoftware.j2meloader.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// ==========================================
// 1. CLASS UTAMA (Public, nama file harus TranslationManager.java)
// ==========================================
public class TranslationManager {
    private static final Map<String, String> translationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> reverseTranslationMap = new ConcurrentHashMap<>();
    private static final Map<String, String> dumpedStrings = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> sedangDiterjemahkan = new ConcurrentHashMap<>();
    
    private static File translationFile;
    private static File dumpFile;
    
    private static volatile boolean isDumpMode = true; 
    private static volatile boolean autoTranslateEnabled = true;
    
    private static final AtomicBoolean hasNewDataToSave = new AtomicBoolean(false);
    private static final AtomicBoolean hasNewTranslationToSave = new AtomicBoolean(false);
    
    private static ScheduledExecutorService saveScheduler;
    private static ScheduledExecutorService translationSaveScheduler;
    private static ExecutorService translateExecutor;

    public static synchronized void init(File gameDir) {
        if (gameDir == null) return;
        
        translationFile = new File(gameDir, "translation.json");
        dumpFile = new File(gameDir, "dump.json");
        
        loadTranslation();
        loadExistingDump();

        if (translateExecutor == null || translateExecutor.isShutdown()) {
            translateExecutor = Executors.newFixedThreadPool(4);
        }

        if (saveScheduler == null || saveScheduler.isShutdown()) {
            saveScheduler = Executors.newSingleThreadScheduledExecutor();
            saveScheduler.scheduleWithFixedDelay(TranslationManager::saveDumpInternal, 500, 500, TimeUnit.MILLISECONDS);
        }

        if (translationSaveScheduler == null || translationSaveScheduler.isShutdown()) {
            translationSaveScheduler = Executors.newSingleThreadScheduledExecutor();
            translationSaveScheduler.scheduleWithFixedDelay(TranslationManager::saveTranslationInternal, 1000, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public static void loadTranslation() {
        translationMap.clear();
        reverseTranslationMap.clear();

        if (translationFile == null || !translationFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(translationFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dumpFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
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
        
        File tempFile = new File(dumpFile.getAbsolutePath() + ".tmp");
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : dumpedStrings.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
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
        
        File tempFile = new File(translationFile.getAbsolutePath() + ".tmp");
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : translationMap.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
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

    public static synchronized void shutdownScheduler() {
        saveDumpInternal();
        saveTranslationInternal();

        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdown();
            saveScheduler = null;
        }
        if (translationSaveScheduler != null && !translationSaveScheduler.isShutdown()) {
            translationSaveScheduler.shutdown();
            translationSaveScheduler = null;
        }
        if (translateExecutor != null && !translateExecutor.isShutdown()) {
            translateExecutor.shutdown();
            translateExecutor = null;
        }
    }

    private static String wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.length() <= maxCharsPerLine || text.contains("\n")) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        String[] words = text.split(" ");
        int currentLineLength = 0;

        for (String word : words) {
            if (currentLineLength + word.length() + 1 > maxCharsPerLine) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(word);
                currentLineLength = word.length();
            } else {
                if (sb.length() > 0 && currentLineLength > 0) {
                    sb.append(" ");
                    currentLineLength++;
                }
                sb.append(word);
                currentLineLength += word.length();
            }
        }

        return sb.toString();
    }

    public static String processString(String original) {
        if (original == null) return original;
        
        String trimmed = original.trim();
        
        if (trimmed.isEmpty() || trimmed.length() <= 1 || trimmed.matches("^\\d+$")) {
            return original;
        }

        if (translationMap.containsKey(trimmed)) {
            if (dumpedStrings.remove(trimmed) != null) {
                hasNewDataToSave.set(true);
            }
            
            String translated = translationMap.get(trimmed);
            int maxChar = Math.max(trimmed.length() + 3, 18);
            if (translated.length() > trimmed.length()) {
                translated = wrapText(translated, maxChar);
            }

            return original.replace(trimmed, translated);
        }

        if (reverseTranslationMap.containsKey(trimmed)) {
            return original;
        }

        if (autoTranslateEnabled && !sedangDiterjemahkan.containsKey(trimmed)) {
            sedangDiterjemahkan.put(trimmed, Boolean.TRUE);
            if (translateExecutor != null && !translateExecutor.isShutdown()) {
                translateExecutor.execute(() -> terjemahkanViaAPI(trimmed));
            }
        }

        if (isDumpMode) {
            dumpedStrings.keySet().removeIf(key -> trimmed.length() > key.length() && trimmed.contains(key));

            boolean isSubText = false;
            for (String key : dumpedStrings.keySet()) {
                if (key.length() >= trimmed.length() && key.contains(trimmed)) {
                    isSubText = true;
                    break;
                }
            }

            if (!isSubText && !dumpedStrings.containsKey(trimmed)) {
                dumpedStrings.put(trimmed, trimmed);
                hasNewDataToSave.set(true);
            }
        }

        return original;
    }

    private static void terjemahkanViaAPI(String teks) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t&q=" 
                    + URLEncoder.encode(teks, "UTF-8");
            
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

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

                            if (dumpedStrings.remove(teks) != null) {
                                hasNewDataToSave.set(true);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Error koneksi diabaikan agar thread UI tidak terganggu
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            sedangDiterjemahkan.remove(teks);
        }
    }

    public static void setDumpMode(boolean enabled) { isDumpMode = enabled; }
    public static boolean isDumpMode() { return isDumpMode; }
    public static void setAutoTranslateEnabled(boolean enabled) { autoTranslateEnabled = enabled; }
    public static boolean isAutoTranslateEnabled() { return autoTranslateEnabled; }
}

// ==========================================
// 2. CLASS PENDUKUNG RENDER (Package-Private)
// ==========================================
class GraphicsUtils {

    private static final String DEFAULT_FONT_NAME = "Arial";
    private static final int DEFAULT_STYLE = Font.BOLD;

    public static void drawStringIntercepted(Graphics g, String str, int x, int y, int anchor) {
        if (str == null || str.trim().isEmpty()) return;

        String teksBaru = TranslationManager.processString(str);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            Font fontAsli = g2d.getFont();
            int fontSize = (fontAsli != null) ? fontAsli.getSize() : 13;
            Font boldArial = new Font(DEFAULT_FONT_NAME, DEFAULT_STYLE, fontSize);
            g2d.setFont(boldArial);

            if (teksBaru.contains("\n")) {
                double lineHeight = fontSize * 1.6;
                String[] baris = teksBaru.split("\n");
                for (int i = 0; i < baris.length; i++) {
                    int nextY = Math.round((float) (y + (i * lineHeight)));
                    renderAndScaleText(g2d, str, baris[i], x, nextY, anchor);
                }
            } else {
                renderAndScaleText(g2d, str, teksBaru, x, y, anchor);
            }

        } finally {
            g2d.dispose();
        }
    }

    private static void renderAndScaleText(Graphics2D g2d, String teksAsli, String teksRender, int x, int y, int anchor) {
        FontMetrics fm = g2d.getFontMetrics();
        
        int widthAsli = fm.stringWidth(teksAsli);
        int widthBaru = fm.stringWidth(teksRender);

        int drawX = alignX(x, widthAsli, anchor);
        int drawY = alignY(y, fm, anchor);

        AffineTransform oldTransform = g2d.getTransform();

        boolean butuhScaling = !teksRender.equals(teksAsli) && (widthBaru > widthAsli) && (widthBaru > 0);

        if (butuhScaling) {
            double scaleX = (double) widthAsli / widthBaru;

            g2d.translate(drawX, drawY);
            g2d.scale(scaleX, 1.0);

            drawShadowAndText(g2d, teksRender, 0, 0);

            g2d.setTransform(oldTransform);
        } else {
            drawShadowAndText(g2d, teksRender, drawX, drawY);
        }
    }

    private static void drawShadowAndText(Graphics2D g2d, String text, int x, int y) {
        Color colorUtama = g2d.getColor();

        g2d.setColor(new Color(0, 0, 0, 140));
        g2d.drawString(text, x + 1, y + 1);

        g2d.setColor(colorUtama);
        g2d.drawString(text, x, y);
    }

    private static int alignX(int x, int width, int anchor) {
        if ((anchor & 1) != 0) return x - (width / 2);
        if ((anchor & 8) != 0) return x - width;
        return x;
    }

    private static int alignY(int y, FontMetrics fm, int anchor) {
        if ((anchor & 16) != 0) return y + fm.getAscent();
        if ((anchor & 32) != 0) return y + (fm.getAscent() / 2);
        if ((anchor & 64) != 0) return y - fm.getDescent();
        return y;
    }
}
