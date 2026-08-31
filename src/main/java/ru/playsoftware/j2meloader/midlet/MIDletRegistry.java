package ru.playsoftware.j2meloader.midlet;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple registry untuk menyimpan instance aktif yang dapat di-scan.
 * Panggil MIDletRegistry.register(instance) dari MIDlet/game saat start,
 * dan MIDletRegistry.unregister(instance) saat destroy/stop.
 */
public class MIDletRegistry {

    private static final CopyOnWriteArrayList<Object> instances = new CopyOnWriteArrayList<>();

    public static void register(Object instance) {
        if (instance != null && !instances.contains(instance)) {
            instances.add(instance);
        }
    }

    public static void unregister(Object instance) {
        if (instance != null) {
            instances.remove(instance);
        }
    }

    public static List<Object> getActiveInstances() {
        return Collections.unmodifiableList(instances);
    }
}
