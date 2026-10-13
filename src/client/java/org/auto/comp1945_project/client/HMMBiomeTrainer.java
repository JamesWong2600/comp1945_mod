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
            // 🔥 限制長度，避免越跑越慢
            if (seq.size() > 200) {
                seq.remove(0);
            }
        }
    }

    // ==================== 訓練（非同步） ====================

    public static void trainAsync() {
        int total = playerSequences.values().stream()
                .mapToInt(List::size).sum();

        if (total == lastTotalOres) return;

        // 🔥 如果已經在訓練，跳過
        if (!training.compareAndSet(false, true)) return;

        lastTotalOres = total;

        // 🔥 複製資料，避免訓練時被修改
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
            training.set(false);
            return;
        }

        // 🔥 丟到背景執行緒
        Thread t = new Thread(() -> {
            try {
                HMM newHmm = new HMM(3, ORE_TO_ID.size());
                newHmm.train(sequences, 20, 1e-4);
                hmm = newHmm;  // 原子替換
                System.out.println("[HMMBiomeTrainer] 訓練完成（背景）");
            } catch (Exception e) {
                System.err.println("[HMMBiomeTrainer] 訓練失敗: " + e.getMessage());
            } finally {
                training.set(false);
            }
        }, "HMM-Trainer");
        t.setDaemon(true);
        t.start();
    }

    // ==================== 預測 ====================

    public static Map<String, Double> predict(UUID uuid) {
        Map<String, Double> raw = new HashMap<>();
        for (String b : BIOMES) raw.put(b, 0.0);

        HMM currentHmm = hmm;
        if (currentHmm != null) {
            List<Integer> seq = playerSequences.get(uuid);
            if (seq != null) {
                int[] arr;
                synchronized (seq) {
                    if (seq.isEmpty()) return smoothedSnapshot();
                    arr = new int[seq.size()];
                    for (int i = 0; i < seq.size(); i++) arr[i] = seq.get(i);
                }

                double[] probs = currentHmm.currentStateProbs(arr);
                int[] mapping = mapStatesToBiomes(currentHmm);
                for (int s = 0; s < 3; s++) {
                    String biome = BIOMES.get(mapping[s]);
                    raw.merge(biome, probs[s], Double::sum);
                }
            }
        }

        for (String k : raw.keySet()) {
            double old = smoothed.getOrDefault(k, 0.0);
            double now = raw.getOrDefault(k, 0.0);
            smoothed.put(k, old * (1 - ALPHA) + now * ALPHA);
        }

        return smoothedSnapshot();
    }

    private static Map<String, Double> smoothedSnapshot() {
        Map<String, Double> snapshot = new HashMap<>();
        for (String b : BIOMES) {
            snapshot.put(b, smoothed.getOrDefault(b, 0.0));
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
                    score += B[s][ore];
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

    public static void clear() {
        playerSequences.clear();
        smoothed.clear();
        hmm = null;
        lastTotalOres = 0;
    }
}