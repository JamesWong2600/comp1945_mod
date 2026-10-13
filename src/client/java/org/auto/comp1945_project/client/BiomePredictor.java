package org.auto.comp1945_project.client;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BiomePredictor {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MODEL_TYPE = new TypeToken<Model>() {}.getType();

    private static final Path MODEL_FILE =
            FabricLoader.getInstance().getConfigDir()
                    .resolve("comp1945_project")
                    .resolve("model.json");

    private static Model model;

    // ==================== 載入模型 ====================

    public static void load() {
        if (!Files.exists(MODEL_FILE)) {
            System.err.println("[BiomePredictor] model.json 不存在，請先訓練");
            model = null;
            return;
        }
        try (Reader reader = Files.newBufferedReader(MODEL_FILE)) {
            model = GSON.fromJson(reader, MODEL_TYPE);
            System.out.println("[BiomePredictor] 模型已載入");
        } catch (Exception e) {
            System.err.println("[BiomePredictor] 載入模型失敗: " + e.getMessage());
            model = null;
        }
    }

    public static boolean isLoaded() {
        return model != null;
    }

    // ==================== 預測 ====================

    /**
     * 根據挖到的礦物計數，預測生態域機率
     *
     * @param oreCounts key = 礦物 ID（例如 "project:iron_block"），value = 數量
     * @return key = 生態域 ID（例如 "project:red"），value = 機率（0.0 ~ 1.0）
     */
    public static Map<String, Double> predict(Map<String, Integer> oreCounts) {
        Map<String, Double> scores = new HashMap<>();
        if (model == null || model.prior == null) return scores;

        // 1. 初始化為 prior（取 log 避免下溢）
        for (Map.Entry<String, Double> e : model.prior.entrySet()) {
            double p = e.getValue();
            if (p <= 0.0) p = 1e-9;
            scores.put(e.getKey(), Math.log(p));
        }

        // 2. 加上 likelihood
        if (model.likelihood != null) {
            for (Map.Entry<String, Integer> oreEntry : oreCounts.entrySet()) {
                String ore = oreEntry.getKey();
                int count = oreEntry.getValue();
                if (count <= 0) continue;

                Map<String, Double> likelihoods = model.likelihood.get(ore);
                if (likelihoods == null) continue;

                for (Map.Entry<String, Double> biomeEntry : likelihoods.entrySet()) {
                    String biome = biomeEntry.getKey();
                    double p = biomeEntry.getValue();
                    if (p <= 0.0) p = 1e-9;
                    scores.merge(biome, Math.log(p) * count, Double::sum);
                }
            }
        }

        // 3. Softmax 正規化
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

    /**
     * 回傳最可能的生態域
     */
    public static String predictTop(Map<String, Integer> oreCounts) {
        Map<String, Double> probs = predict(oreCounts);
        return probs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // ==================== 模型結構 ====================

    public static class Model {
        /** P(biome)：先驗機率 */
        public Map<String, Double> prior;

        /** P(ore | biome)：每個礦物在各生態域的機率 */
        public Map<String, Map<String, Double>> likelihood;
    }
}