package org.auto.comp1945_project.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import java.util.Map;

public class Comp1945_projectClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
       AutoMineController.register();
        BiomeWebServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(BiomeWebServer::stop));
        HudRenderCallback.EVENT.register(new BiomeGuessHUD());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (client.world.getTime() % 20 != 0) return;

            Map<String, Integer> oreCounts = SessionStats.get(client.player.getUuid());
            Map<String, Double> probs = BiomePredictor.predict(oreCounts);
            BiomeGuessHUD.updatePredictions(probs);
        });
    }
}
