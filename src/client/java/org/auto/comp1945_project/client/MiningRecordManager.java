package org.auto.comp1945_project.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MiningRecordManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<RecordEntry>>() {}.getType();

    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("comp1945_project");

    private static final Path GUESS_FILE = CONFIG_DIR.resolve("guess_data.json");
    private static final Path ANS_KEY_FILE = CONFIG_DIR.resolve("ans_key_data.json");

    // 已寫入檔案的記錄
    private static final List<RecordEntry> guessRecords = new ArrayList<>();
    private static final List<RecordEntry> ansKeyRecords = new ArrayList<>();

    // 這一秒內待 flush 的記錄
    private static final List<RecordEntry> pendingAnsRecords = new ArrayList<>();

    // 去重用的 hash
    private static final Set<String> ansKeyHash = new HashSet<>();

    private static int flushTickCounter = 0;

    // ==================== guess_data：破壞方塊時記錄 ====================

    public static void recordBlockBreak(ServerWorld world, BlockPos pos, BlockState state) {
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        long timestamp = System.currentTimeMillis();

        RecordEntry entry = new RecordEntry();
        entry.timestamp = timestamp;
        entry.block = blockId.toString();
        entry.x = pos.getX();
        entry.y = pos.getY();
        entry.z = pos.getZ();

        guessRecords.add(entry);
        save(GUESS_FILE, guessRecords);
    }

    // ==================== ans_key_data：每 tick 記錄 ====================

    /**
     * 每 tick 呼叫一次，記錄某個實體的座標 + 生態域
     */
    public static void tickAnsKey(ServerWorld world, double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        Identifier biomeId = biomeEntry.getKey()
                .map(key -> key.getValue())
                .orElse(Identifier.of("minecraft", "plains"));

        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + biomeId;

        if (ansKeyHash.contains(key)) {
            return; // 已經記錄過，跳過
        }
        ansKeyHash.add(key);

        RecordEntry entry = new RecordEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.x = pos.getX();
        entry.y = pos.getY();
        entry.z = pos.getZ();
        entry.biome = biomeId.toString();

        pendingAnsRecords.add(entry);
    }

    /**
     * 每 tick 呼叫一次，負責每秒 flush
     */
    public static void tickFlush() {
        flushTickCounter++;
        if (flushTickCounter < 20) return;
        flushTickCounter = 0;

        if (pendingAnsRecords.isEmpty()) return;

        ansKeyRecords.addAll(pendingAnsRecords);
        pendingAnsRecords.clear();
        save(ANS_KEY_FILE, ansKeyRecords);
    }

    // ==================== 存檔 / 讀檔 ====================

    private static void save(Path path, List<RecordEntry> records) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(records, writer);
            }
        } catch (IOException e) {
            System.err.println("[MiningRecord] 存檔失敗: " + path + " | " + e.getMessage());
        }
    }

    public static void loadAll() {
        guessRecords.clear();
        ansKeyRecords.clear();
        pendingAnsRecords.clear();
        ansKeyHash.clear();

        guessRecords.addAll(load(GUESS_FILE));
        ansKeyRecords.addAll(load(ANS_KEY_FILE));

        // 把已載入的 ans 記錄也加進 hash，避免重複
        for (RecordEntry e : ansKeyRecords) {
            ansKeyHash.add(e.x + "," + e.y + "," + e.z + "," + e.biome);
        }
    }

    private static List<RecordEntry> load(Path path) {
        if (!Files.exists(path)) return new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            List<RecordEntry> list = GSON.fromJson(reader, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[MiningRecord] 讀檔失敗: " + path + " | " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void clearAll() {
        guessRecords.clear();
        ansKeyRecords.clear();
        pendingAnsRecords.clear();
        ansKeyHash.clear();
        save(GUESS_FILE, guessRecords);
        save(ANS_KEY_FILE, ansKeyRecords);
    }

    // ==================== 資料類別 ====================

    public static class RecordEntry {
        public long timestamp;
        public String block;
        public int x;
        public int y;
        public int z;
        public String biome;
    }
}