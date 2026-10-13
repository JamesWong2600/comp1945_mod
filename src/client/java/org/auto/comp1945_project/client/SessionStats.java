package org.auto.comp1945_project.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionStats {
    private static final Map<UUID, Map<String, Integer>> playerOres = new HashMap<>();

    public static void onBlockBreak(UUID playerUuid, String ore) {
        playerOres
                .computeIfAbsent(playerUuid, k -> new HashMap<>())
                .merge(ore, 1, Integer::sum);
    }

    public static Map<String, Integer> get(UUID playerUuid) {
        return playerOres.getOrDefault(playerUuid, new HashMap<>());
    }

    public static void clear(UUID playerUuid) {
        playerOres.remove(playerUuid);
    }
}