package org.auto.comp1945_project.client;


import java.util.HashMap;
import java.util.Map;

public class BiomeTrainerLive {

    // ore -> biome -> count
    private static final Map<String, Map<String, Integer>> oreBiomeCount = new HashMap<>();

    // biome -> count
    private static final Map<String, Integer> biomeCount = new HashMap<>();

    // ==================== 實時訓練 ====================

    /**
     * 每挖到一個礦物就呼叫
     *
     * @param oreId   礦物 ID（例如 "project:iron_block"）
     * @param biomeId 生態域 ID（例如 "project:red"）
     */
    public static void onBlockBreak(String oreId, String biomeId) {
        oreBiomeCount
                .computeIfAbsent(oreId, k -> new HashMap<>())
                .merge(biomeId, 1, Integer::sum);

        biomeCount.merge(biomeId, 1, Integer::sum);
    }

    // ==================== 實時預測 ====================

    /**
     * 根據玩家當前挖到的礦物計數，預測生態域機率
     */
    public static Map<String, Double> predict(Map<String, Integer> oreCounts) {
        Map<String, Double> scores = new HashMap<>();

        // 1. prior
        int totalBiome = biomeCount.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBiome <= 0) return scores;

        for (Map.Entry<String, Integer> e : biomeCount.entrySet()) {
            double p = (double) e.getValue() / totalBiome;
            if (p <= 0.0) p = 1e-9;
            scores.put(e.getKey(), Math.log(p));
        }

        // 2. likelihood
        for (Map.Entry<String, Integer> oreEntry : oreCounts.entrySet()) {
            String ore = oreEntry.getKey();
            int count = oreEntry.getValue();
            if (count <= 0) continue;

            Map<String, Integer> counts = oreBiomeCount.get(ore);
            if (counts == null) continue;

            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) continue;

            for (Map.Entry<String, Integer> b : counts.entrySet()) {
                String biome = b.getKey();
                double p = (double) b.getValue() / total;
                if (p <= 0.0) p = 1e-9;
                scores.merge(biome, Math.log(p) * count, Double::sum);
            }
        }

        // 3. softmax
        double max = scores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        double sum = 0.0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            double v = Math.exp(e.getValue() - max);
            e.setValue(v);
            sum += v;
        }
        if (sum > 0) {
            for (Map.Entry<String, Double> e : scores.entrySet()) {
                e.setValue(e.getValue() / sum);
            }
        }

        return scores;
    }

    // ==================== 清除 / 匯出 ====================

    public static void clear() {
        oreBiomeCount.clear();
        biomeCount.clear();
    }

    public static BiomePredictor.Model exportModel() {
        BiomePredictor.Model model = new BiomePredictor.Model();
        model.prior = new HashMap<>();
        model.likelihood = new HashMap<>();

        int totalBiome = biomeCount.values().stream().mapToInt(Integer::intValue).sum();
        if (totalBiome > 0) {
            for (Map.Entry<String, Integer> e : biomeCount.entrySet()) {
                model.prior.put(e.getKey(), (double) e.getValue() / totalBiome);
            }
        }

        for (Map.Entry<String, Map<String, Integer>> oreEntry : oreBiomeCount.entrySet()) {
            String ore = oreEntry.getKey();
            Map<String, Integer> counts = oreEntry.getValue();
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) continue;

            Map<String, Double> probs = new HashMap<>();
            for (Map.Entry<String, Integer> b : counts.entrySet()) {
                probs.put(b.getKey(), (double) b.getValue() / total);
            }
            model.likelihood.put(ore, probs);
        }

        return model;
    }
}