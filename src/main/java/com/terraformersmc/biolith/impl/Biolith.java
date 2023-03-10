package com.terraformersmc.biolith.impl;

import com.terraformersmc.biolith.impl.biome.BiomeCoordinator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Biolith implements ModInitializer {
    public static final String MOD_ID = "biolith";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Watch for server events so we can maintain our status data.
        ServerLifecycleEvents.SERVER_STARTING.register(BiomeCoordinator::handleServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(BiomeCoordinator::handleServerStopped);

        LOGGER.info("Biolith is loaded!");
    }
}
