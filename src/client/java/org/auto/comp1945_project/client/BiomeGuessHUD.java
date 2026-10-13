package org.auto.comp1945_project.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeGuessHUD implements HudRenderCallback {

    // ==================== 給 HUD 和 Web Server 的資料 ====================

    // 🔥 執行緒安全
    public static final Map<String, Double> predictedProbs = new ConcurrentHashMap<>();

    public static volatile String lastBrokenBlock = "none";
    public static volatile String lastCoord = "none";
    public static volatile String lastBiome = "none";
    public static volatile long lastTick = 0;

    private static final String[] BIOMES = {
            "project:red", "project:blue", "project:yellow"
    };

    private static final String[] BIOME_NAMES_ZH = {"紅", "藍", "黃"};
    private static final String[] BIOME_NAMES_EN = {"Red", "Blue", "Yellow"};

    // ==================== 渲染 ====================

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.options.hudHidden) return;

        TextRenderer textRenderer = client.textRenderer;
        boolean useEnglish = client.options.language.startsWith("en");

        // 🔥 只讀，不 predict
        String coordStr = lastCoord;
        String biomeStr = lastBiome;
        long tick = lastTick;

        int leftX = 5;
        int leftY = 5;
        int lineHeight = 10;

        drawContext.drawText(
                textRenderer,
                Text.literal(useEnglish ? "Biome Guess" : "生態域預測")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                leftX, leftY, 0xFFFFFF, true
        );

        int y = leftY + lineHeight + 2;
        for (int i = 0; i < BIOMES.length; i++) {
            String biome = BIOMES[i];
            double prob = predictedProbs.getOrDefault(biome, 0.0);
            int percent = (int) Math.round(prob * 100);
            String name = useEnglish ? BIOME_NAMES_EN[i] : BIOME_NAMES_ZH[i];
            Formatting color = switch (i) {
                case 0 -> Formatting.RED;
                case 1 -> Formatting.BLUE;
                case 2 -> Formatting.YELLOW;
                default -> Formatting.WHITE;
            };
            Text line = Text.literal(name + ": ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(percent + "%").formatted(color));
            drawContext.drawText(textRenderer, line, leftX, y, 0xFFFFFF, true);
            y += lineHeight;
        }

        int rightX = client.getWindow().getScaledWidth() - 5;
        int rightY = 5;
        String[] rightLines;
        if (useEnglish) {
            rightLines = new String[]{
                    "Coord: " + coordStr,
                    "Block: " + lastBrokenBlock,
                    "Tick: " + tick,
                    "Biome: " + biomeStr
            };
        } else {
            rightLines = new String[]{
                    "座標: " + coordStr,
                    "方塊: " + lastBrokenBlock,
                    "Tick: " + tick,
                    "生態域: " + biomeStr
            };
        }

        int ry = rightY;
        for (String line : rightLines) {
            int width = textRenderer.getWidth(line);
            drawContext.drawText(
                    textRenderer,
                    Text.literal(line).formatted(Formatting.AQUA),
                    rightX - width, ry, 0xFFFFFF, true
            );
            ry += lineHeight;
        }
    }

    // ==================== 更新方法 ====================

    public static void updatePredictions(Map<String, Double> probs) {
        if (probs == null) return;
        for (String b : BIOMES) {
            predictedProbs.put(b, probs.getOrDefault(b, 0.0));
        }
    }

    public static void updateLastBrokenBlock(String blockId) {
        lastBrokenBlock = blockId;
    }

    public static void updateStatus(String coord, String biome, long tick) {
        lastCoord = coord;
        lastBiome = biome;
        lastTick = tick;
    }

    // 🔥 Web Server 用這個，回傳快照
    public static Map<String, Double> getPredictionsSnapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (String b : BIOMES) {
            snapshot.put(b, predictedProbs.getOrDefault(b, 0.0));
        }
        return snapshot;
    }
}