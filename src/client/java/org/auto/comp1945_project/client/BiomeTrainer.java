package org.auto.comp1945_project.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BiomeTrainer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<MiningRecordManager.RecordEntry>>() {}.getType();

    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("comp1945_project");

    private static final Path GUESS_FILE = CONFIG_DIR.resolve("guess_data.json");
    private static final Path ANS_KEY_FILE = CONFIG_DIR.resolve("ans_key_data.json");
    private static final Path MODEL_FILE = CONFIG_DIR.resolve("model.json");

    /**
     * 訓練模型：讀 guess_data + ans_key_data，統計 P(ore | biome) 和 P(biome)
     */
    public static BiomePredictor.Model train() {
        List<MiningRecordManager.RecordEntry> guessRecords = load(GUESS_FILE);
        List<MiningRecordManager.RecordEntry> ansRecords = load(ANS_KEY_FILE);

        if (guessRecords.isEmpty() || ansRecords.isEmpty()) {
            System.err.println("[BiomeTrainer] 訓練資料不足");
            return null;
        }

        // 1. 建立座標 -> biome 的對應
        Map<String, String> posToBiome = new HashMap<>();
        for (MiningRecordManager.RecordEntry e : ansRecords) {
            String key = e.x + "," + e.y + "," + e.z;
            posToBiome.put(key, e.biome);
        }

        // 2. 統計 count(ore, biome)
        Map<String, Map<String, Integer>> oreBiomeCount = new HashMap<>();
        Map<String, Integer> biomeCount = new HashMap<>();

        for (MiningRecordManager.RecordEntry e : guessRecords) {
            String key = e.x + "," + e.y + "," + e.z;
            String biome = posToBiome.get(key);
            if (biome == null) continue;

            oreBiomeCount
                    .computeIfAbsent(e.block, k -> new HashMap<>())
                    .merge(biome, 1, Integer::sum);

            biomeCount.merge(biome, 1, Integer::sum);
        }

        // 3. 計算 likelihood P(ore | biome)
        Map<String, Map<String, Double>> likelihood = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> oreEntry : oreBiomeCount.entrySet()) {
            String ore = oreEntry.getKey();
            Map<String, Integer> counts = oreEntry.getValue();
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();

            Map<String, Double> probs = new HashMap<>();
            for (Map.Entry<String, Integer> b : counts.entrySet()) {
                probs.put(b.getKey(), (double) b.getValue() / total);
            }
            likelihood.put(ore, probs);
        }

        // 4. 計算 prior P(biome)
        int totalCount = biomeCount.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> prior = new HashMap<>();
        for (Map.Entry<String, Integer> e : biomeCount.entrySet()) {
            prior.put(e.getKey(), (double) e.getValue() / totalCount);
        }

        // 5. 組合模型
        BiomePredictor.Model model = new BiomePredictor.Model();
        model.prior = prior;
        model.likelihood = likelihood;

        // 6. 存檔
        save(MODEL_FILE, model);

        System.out.println("[BiomeTrainer] 訓練完成，礦物種類: " + likelihood.size()
                + "，生態域: " + prior.size());
        return model;
    }

    // ==================== 存檔 / 讀檔 ====================

    private static List<MiningRecordManager.RecordEntry> load(Path path) {
        if (!Files.exists(path)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            List<MiningRecordManager.RecordEntry> list = GSON.fromJson(reader, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[BiomeTrainer] 讀檔失敗: " + path + " | " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void save(Path path, Object obj) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException e) {
            System.err.println("[BiomeTrainer] 存檔失敗: " + path + " | " + e.getMessage());
        }
    }
}