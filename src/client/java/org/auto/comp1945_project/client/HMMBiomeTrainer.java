package org.auto.comp1945_project.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class HMMBiomeTrainer {

    private static final Map<String, Integer> ORE_TO_ID = new HashMap<>();
    private static final Map<Integer, String> ID_TO_ORE = new HashMap<>();
    private static final List<String> BIOMES = List.of(
            "project:red", "project:blue", "project:yellow"
    );

    static {
        int id = 0;
        for (String ore : List.of(
                "minecraft:iron_block",
                "minecraft:redstone_block",
                "minecraft:emerald_block",
                "minecraft:diamond_block",
                "minecraft:coal_block",
                "minecraft:gold_block"
        )) {
            ORE_TO_ID.put(ore, id);
            ID_TO_ORE.put(id, ore);
            id++;
        }
    }

    // 🔥 執行緒安全
    private static final Map<UUID, List<Integer>> playerSequences = new ConcurrentHashMap<>();

    // 🔥 原子替換
    private static volatile HMM hmm;

    // 🔥 防止同時多次訓練
    private static final AtomicBoolean training = new AtomicBoolean(false);

    private static volatile int lastTotalOres = 0;

    // 🔥 平滑
    private static final Map<String, Double> smoothed = new ConcurrentHashMap<>();
    private static final double ALPHA = 0.3;

    // ==================== 收集 ====================

    public static void onBlockBreak(UUID uuid, String oreId) {
        Integer ore = ORE_TO_ID.get(oreId);
        if (ore == null) return;

        List<Integer> seq = playerSequences.computeIfAbsent(
                uuid, k -> Collections.synchronizedList(new ArrayList<>())
        );
        synchronized (seq) {
            seq.add(ore);
//            if (seq.size() > 200) {
//                seq.remove(0);
//            }
        }
    }

    // ==================== 訓練（非同步） ====================

    public static void trainAsync() {
        int total = playerSequences.values().stream()
                .mapToInt(List::size).sum();

        if (total == lastTotalOres) return;

        if (!training.compareAndSet(false, true)) {
            System.out.println("[HMMBiomeTrainer] ⏳ 上一次訓練還沒結束，跳過");
            return;
        }

        lastTotalOres = total;
        System.out.println("[HMMBiomeTrainer] 🏋️ 開始訓練，總礦物數: " + total);

        List<int[]> sequences = new ArrayList<>();
        for (List<Integer> seq : playerSequences.values()) {
            int[] arr;
            synchronized (seq) {
                if (seq.size() < 5) continue;
                arr = new int[seq.size()];
                for (int i = 0; i < seq.size(); i++) arr[i] = seq.get(i);
            }
            sequences.add(arr);
        }

        if (sequences.isEmpty()) {
            System.out.println("[HMMBiomeTrainer] ⚠️ 沒有足夠長的序列，跳過訓練");
            training.set(false);
            return;
        }

        Thread t = new Thread(() -> {
            try {
                SmileBiomeTrainer.train(sequences);
                System.out.println("[DL4J] 訓練完成");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                training.set(false);
            }
        }, "DL4J-Trainer");
        t.start();
    }

    // ==================== 預測 ====================

    public static Map<String, Double> predict(UUID uuid) {
        Map<String, Double> raw = new HashMap<>();
        for (String b : BIOMES) raw.put(b, 0.0);

        // 1. 模型還沒訓練好 → 回傳平滑快照
        if (!SmileBiomeTrainer.isReady()) {
            return smoothedSnapshot();
        }

        // 2. 沒有這個玩家的序列 → 回傳平滑快照
        List<Integer> seq = playerSequences.get(uuid);
        if (seq == null || seq.isEmpty()) {
            return smoothedSnapshot();
        }

        int[] arr;
        synchronized (seq) {
            arr = new int[seq.size()];
            for (int i = 0; i < seq.size(); i++) arr[i] = seq.get(i);
        }

        // 3. 用 SmileBiomeTrainer（邏輯回歸）預測
        Map<String, Double> smileProbs = SmileBiomeTrainer.predict(arr);
        if (smileProbs != null && !smileProbs.isEmpty()) {
            raw.putAll(smileProbs);
        }

        // 4. 正規化
        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0) {
            for (String k : raw.keySet()) {
                raw.put(k, raw.get(k) / total);
            }
        } else {
            for (String k : raw.keySet()) {
                raw.put(k, 1.0 / BIOMES.size());
            }
        }

        // 5. 平滑
        for (String k : raw.keySet()) {
            double old = smoothed.getOrDefault(k, 0.0);
            double now = raw.getOrDefault(k, 0.0);
            if (Double.isNaN(now) || Double.isInfinite(now)) now = 0.0;
            if (Double.isNaN(old) || Double.isInfinite(old)) old = 0.0;
            smoothed.put(k, old * (1 - ALPHA) + now * ALPHA);
        }

        return smoothedSnapshot();
    }

    private static Map<String, Double> smoothedSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        for (String b : BIOMES) {
            double v = smoothed.getOrDefault(b, 0.0);
            if (Double.isNaN(v) || Double.isInfinite(v)) v = 0.0;
            snapshot.put(b, v);
        }
        return snapshot;
    }

    private static int[] mapStatesToBiomes(HMM hmm) {
        double[][] B = hmm.getB();
        int[] mapping = new int[3];
        boolean[] used = new boolean[3];

        int[][] biomeOres = {
                {0, 1}, {2, 3}, {4, 5}
        };

        for (int biome = 0; biome < 3; biome++) {
            int bestState = -1;
            double bestScore = -1;

            for (int s = 0; s < 3; s++) {
                if (used[s]) continue;
                double score = 0;
                for (int ore : biomeOres[biome]) {
                    double p = B[s][ore];
                    if (Double.isNaN(p) || Double.isInfinite(p)) p = 0.0;
                    score += p;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestState = s;
                }
            }

            mapping[bestState] = biome;
            used[bestState] = true;
        }

        return mapping;
    }

    public static String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("hmm=").append(hmm != null ? "ok" : "null");
        sb.append(", training=").append(training.get());
        sb.append(", lastTotalOres=").append(lastTotalOres);
        sb.append(", players=").append(playerSequences.size());
        int total = playerSequences.values().stream().mapToInt(List::size).sum();
        sb.append(", totalOres=").append(total);
        sb.append(", smoothed=").append(smoothed);
        return sb.toString();
    }

    public static void clear() {
        playerSequences.clear();
        smoothed.clear();
        hmm = null;
        lastTotalOres = 0;
    }
}