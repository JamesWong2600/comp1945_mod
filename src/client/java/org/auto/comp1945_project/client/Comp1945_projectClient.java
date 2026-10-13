package org.auto.comp1945_project.client;

import net.fabricmc.api.ClientModInitializer;

public class Comp1945_projectClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
       AutoMineController.register();
    }
}
