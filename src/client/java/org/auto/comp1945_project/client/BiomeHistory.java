package org.auto.comp1945_project.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BiomeHistory {

    private static final List<HistoryEntry> history = new ArrayList<>();
    private static final int MAX = 1000;

    public static void add(long tick, Map<String, Double> probs) {
        HistoryEntry e = new HistoryEntry();
        e.tick = tick;
        e.red = probs.getOrDefault("project:red", 0.0);
        e.blue = probs.getOrDefault("project:blue", 0.0);
        e.yellow = probs.getOrDefault("project:yellow", 0.0);

        synchronized (history) {
            history.add(e);
            if (history.size() > MAX) {
                history.remove(0);
            }
        }
    }

    public static List<HistoryEntry> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public static void clear() {
        synchronized (history) {
            history.clear();
        }
    }

    public static class HistoryEntry {
        public long tick;
        public double red;
        public double blue;
        public double yellow;
    }
}