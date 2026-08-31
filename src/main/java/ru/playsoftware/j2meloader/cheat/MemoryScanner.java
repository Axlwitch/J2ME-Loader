package ru.playsoftware.j2meloader.cheat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryScanner {

    public static class ScanResult {
        public final Object instance;
        public final Field field;
        public volatile int value;

        public ScanResult(Object instance, Field field, int value) {
            this.instance = instance;
            this.field = field;
            this.value = value;
        }

        @Override
        public String toString() {
            String cls = field.getDeclaringClass() != null ? field.getDeclaringClass().getSimpleName() : "Class";
            return cls + "." + field.getName() + " = " + value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(instance) * 31 + field.getName().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ScanResult)) return false;
            ScanResult s = (ScanResult) o;
            return s.instance == this.instance && s.field.getName().equals(this.field.getName());
        }
    }

    private static final List<ScanResult> currentResults = new ArrayList<>();
    private static final Map<ScanResult, Integer> frozenList = new ConcurrentHashMap<>();
    private static ScheduledExecutorService freezeScheduler;

    public static synchronized List<ScanResult> firstScan(List<Object> activeObjects, int searchValue) {
        currentResults.clear();
        if (activeObjects == null) return new ArrayList<>();
        for (Object obj : activeObjects) {
            if (obj == null) continue;
            Field[] fields = obj.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() == int.class) {
                    try {
                        field.setAccessible(true);
                        int val = field.getInt(obj);
                        if (val == searchValue) {
                            currentResults.add(new ScanResult(obj, field, val));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return new ArrayList<>(currentResults);
    }

    public static synchronized List<ScanResult> nextScan(int searchValue) {
        List<ScanResult> filtered = new ArrayList<>();
        for (ScanResult result : currentResults) {
            try {
                result.field.setAccessible(true);
                int currentVal = result.field.getInt(result.instance);
                if (currentVal == searchValue) {
                    result.value = currentVal;
                    filtered.add(result);
                }
            } catch (Exception ignored) {}
        }
        currentResults.clear();
        currentResults.addAll(filtered);
        return new ArrayList<>(currentResults);
    }

    public static boolean inject(ScanResult result, int newValue) {
        try {
            result.field.setAccessible(true);
            result.field.setInt(result.instance, newValue);
            result.value = newValue;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void freeze(ScanResult result, int freezeValue) {
        if (result == null) return;
        inject(result, freezeValue);
        frozenList.put(result, freezeValue);
        startFreezeLoop();
    }

    public static void unfreeze(ScanResult result) {
        if (result == null) return;
        frozenList.remove(result);
        if (frozenList.isEmpty()) stopFreezeLoop();
    }

    public static void clearAll() {
        currentResults.clear();
        frozenList.clear();
        stopFreezeLoop();
    }

    private static void startFreezeLoop() {
        if (freezeScheduler == null || freezeScheduler.isShutdown()) {
            freezeScheduler = Executors.newSingleThreadScheduledExecutor();
            freezeScheduler.scheduleWithFixedDelay(() -> {
                for (Map.Entry<ScanResult, Integer> entry : frozenList.entrySet()) {
                    try {
                        inject(entry.getKey(), entry.getValue());
                    } catch (Throwable ignored) {}
                }
            }, 150, 150, TimeUnit.MILLISECONDS);
        }
    }

    private static void stopFreezeLoop() {
        if (freezeScheduler != null) {
            freezeScheduler.shutdownNow();
            freezeScheduler = null;
        }
    }

    public static List<ScanResult> getCurrentResults() {
        return currentResults;
    }
}
