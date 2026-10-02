package ru.playsoftware.j2meloader.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class TranslationManager {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    // ===== Pengaturan =====
    private static final boolean isDumpMode = true;      // simpan teks baru ke translation.json
    private static final boolean autoTranslate = true;   // terjemahkan otomatis via Google
    private static final String TARGET_LANG = "id";
    private static final long DEBOUNCE_MS = 700;         // tunggu teks "mengetik" selesai
    private static final long OFFLINE_RETRY_MS = 60_000; // coba lagi setelah gagal jaringan

    // teks asli -> terjemahan
    private static final Map<String, String> translationMap = new ConcurrentHashMap<>();
    // nilai yang sudah berupa hasil terjemahan (jangan diterjemahkan ulang)
    private static final Set<String> translatedValues =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    // teks yang sudah pernah diproses (supaya tidak diulang tiap frame)
    private static final Map<String, Boolean> seen = new ConcurrentHashMap<>();
    // teks yang menunggu ditulis ke file
    private static final Map<String, String> pending = new ConcurrentHashMap<>();
    // teks yang baru dilihat (untuk mendeteksi efek mengetik)
    private static final Map<String, Long> recent = new ConcurrentHashMap<>();

    private static volatile File jsonFile;
    private static volatile boolean dirty = false;
    private static volatile long offlineUntil = 0;

    private static ScheduledExecutorService saver;
    private static final ScheduledExecutorService tlPool =
            Executors.newScheduledThreadPool(2, daemonFactory("AutoTL"));

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            }
        };
    }

    public static synchronized void init(File gameDir) {
        jsonFile = new File(gameDir, "translation.json");
        translationMap.clear();
        translatedValues.clear();
        seen.clear();
        pending.clear();
        recent.clear();
        offlineUntil = 0;
        loadTranslation();

        if (isDumpMode && saver == null) {
            saver = Executors.newSingleThreadScheduledExecutor(daemonFactory("TranslationDump"));
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
                String val = json.getString(key);
                // value == key berarti belum diterjemahkan, biarkan auto translate mengisinya
                if (!val.equals(key)) {
                    translationMap.put(key, val);
                    translatedValues.add(val);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Dipanggil periodik oleh thread terpisah. Panggil juga saat game ditutup. */
    public static synchronized void saveDump() {
        File f = jsonFile;
        if (!dirty || f == null || pending.isEmpty()) return;
        try {
            JSONObject json = f.exists() ? new JSONObject(readFile(f)) : new JSONObject();
            for (Map.Entry<String, String> e : pending.entrySet()) {
                String k = e.getKey();
                // tambah key baru, atau isi key yang belum diterjemahkan (value == key)
                if (!json.has(k) || json.optString(k).equals(k)) {
                    json.put(k, e.getValue());
                }
            }
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

    /** Dipanggil dari render thread: cepat, tanpa I/O. */
    public static String processString(String original) {
        if (original == null) return null;
        String t = original.trim();
        if (t.length() <= 1 || isNumeric(t)) return original;

        String tr = translationMap.get(t);
        if (tr != null) {
            // pertahankan spasi awal/akhir asli (untuk alignment)
            return t.length() == original.length() ? tr : original.replace(t, tr);
        }

        if (jsonFile == null || translatedValues.contains(t)) return original;

        if (autoTranslate && hasCjk(t)) {
            if (System.currentTimeMillis() < offlineUntil) return original; // coba lagi nanti
            if (seen.put(t, Boolean.TRUE) == null) {
                recent.put(t, System.currentTimeMillis());
                scheduleTranslation(t);
            }
        } else if (isDumpMode && seen.put(t, Boolean.TRUE) == null) {
            addPending(t, t, true);
        }
        return original;
    }

    private static void scheduleTranslation(final String text) {
        tlPool.schedule(new Runnable() {
            @Override
            public void run() {
                // teks yang ternyata hanya potongan dari teks yang lebih panjang
                // (efek mengetik) dilewati
                if (hasNewerText(text)) return;
                requestTranslation(text);
            }
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private static boolean hasNewerText(String text) {
        long now = System.currentTimeMillis();
        boolean found = false;
        for (Map.Entry<String, Long> e : recent.entrySet()) {
            long age = now - e.getValue();
            if (age > 10_000) {
                recent.remove(e.getKey());
                continue;
            }
            String k = e.getKey();
            if (age < 3_000 && k.length() > text.length() && k.contains(text)) {
                found = true;
            }
        }
        return found;
    }

    private static void requestTranslation(String text) {
        if (System.currentTimeMillis() < offlineUntil) {
            seen.remove(text);
            return;
        }
        HttpURLConnection c = null;
        try {
            URL url = new URL("https://translate.googleapis.com/translate_a/single"
                    + "?client=gtx&sl=auto&tl=" + TARGET_LANG + "&dt=t&q="
                    + URLEncoder.encode(text, "UTF-8"));
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            JSONArray parts = new JSONArray(new String(out.toByteArray(), UTF8)).getJSONArray(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                sb.append(parts.getJSONArray(i).getString(0)); // gabungkan semua segmen
            }
            String result = sb.toString().trim();
            if (!result.isEmpty() && !result.equals(text)) {
                translationMap.put(text, result);
                translatedValues.add(result);
                if (isDumpMode) addPending(text, result, false);
            }
        } catch (IOException e) {
            // gagal jaringan / limit: jeda dulu, teks akan dicoba lagi nanti
            offlineUntil = System.currentTimeMillis() + OFFLINE_RETRY_MS;
            seen.remove(text);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * filterSub = true: buang potongan teks (efek mengetik).
     * Hapus key pendek yang merupakan bagian dari key baru,
     * dan lewati key baru yang hanya bagian dari key lain.
     */
    private static void addPending(String key, String value, boolean filterSub) {
        if (filterSub) {
            for (String k : pending.keySet()) {
                if (key.length() > k.length() && key.contains(k)) {
                    pending.remove(k);
                } else if (k.length() >= key.length() && !k.equals(key) && k.contains(key)) {
                    return;
                }
            }
        }
        pending.put(key, value);
        dirty = true;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
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

    private static String readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            String s = new String(out.toByteArray(), UTF8);
            if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1); // buang BOM
            return s;
        }
    }
}
