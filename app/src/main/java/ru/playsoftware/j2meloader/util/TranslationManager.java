package ru.playsoftware.j2meloader.util;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class TranslationManager {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    // teks asli -> terjemahan (dibaca dari translation.json)
    private static final Map<String, String> translationMap = new ConcurrentHashMap<>();
    // teks yang sudah pernah dilihat game (supaya tidak diproses berulang tiap frame)
    private static final Map<String, Boolean> seen = new ConcurrentHashMap<>();
    // teks baru yang menunggu ditulis ke file
    private static final Map<String, String> pending = new ConcurrentHashMap<>();

    private static volatile File jsonFile;
    private static volatile boolean dirty = false;
    private static ScheduledExecutorService saver;

    // true = simpan teks baru ke translation.json (set false kalau sudah selesai menerjemahkan)
    private static final boolean isDumpMode = true;

    public static synchronized void init(File gameDir) {
        jsonFile = new File(gameDir, "translation.json");
        translationMap.clear();
        seen.clear();
        pending.clear();
        loadTranslation();

        if (isDumpMode && saver == null) {
            saver = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TranslationDump");
                    t.setDaemon(true);
                    return t;
                }
            });
            saver.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    saveDump();
                }
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    /** Bisa dipanggil ulang untuk reload setelah JSON diedit. */
    public static synchronized void loadTranslation() {
        File f = jsonFile;
        if (f == null || !f.exists()) return;
        try {
            JSONObject json = new JSONObject(readFile(f));
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                translationMap.put(key, json.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Dipanggil periodik oleh thread terpisah. Panggil juga saat game ditutup. */
    public static synchronized void saveDump() {
        File f = jsonFile;
        if (!isDumpMode || !dirty || f == null || pending.isEmpty()) return;
        try {
            // baca isi file terbaru supaya terjemahan yang sudah ada tidak tertimpa
            JSONObject json = f.exists() ? new JSONObject(readFile(f)) : new JSONObject();
            for (Map.Entry<String, String> e : pending.entrySet()) {
                if (!json.has(e.getKey())) {
                    json.put(e.getKey(), e.getValue());
                }
            }
            // tulis ke file sementara dulu, lalu rename (aman kalau app crash)
            File tmp = new File(f.getParentFile(), "translation.json.tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), UTF8)) {
                w.write(json.toString(4));
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
            pending.clear();
            dirty = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Dipanggil dari render thread: harus cepat, tanpa I/O. */
    public static String processString(String original) {
        if (original == null || original.trim().isEmpty()) return original;

        String translated = translationMap.get(original);
        if (translated != null) return translated;

        if (isDumpMode && jsonFile != null && !isNumeric(original)
                && seen.put(original, Boolean.TRUE) == null) {
            pending.put(original, original);
            dirty = true;
        }
        return original;
    }

    private static boolean isNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && !Character.isWhitespace(c)
                    && c != '/' && c != ':' && c != '.' && c != '-' && c != '+' && c != '%') {
                return false;
            }
        }
        return true;
    }

    private static String readFile(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            String s = new String(out.toByteArray(), UTF8);
            // buang BOM kalau file disimpan editor Windows
            if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
            return s;
        }
    }
}
