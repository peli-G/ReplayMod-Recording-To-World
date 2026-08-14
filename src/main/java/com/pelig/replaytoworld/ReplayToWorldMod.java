package com.pelig.replaytoworld;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplayToWorldMod implements ClientModInitializer {

    public static final String MOD_ID = "replay-to-world";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    public static volatile net.minecraft.core.RegistryAccess cachedRegistryAccess = null;

    @Override
    public void onInitializeClient() {

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            cachedRegistryAccess = handler.registryAccess();
            LOGGER.info("[ReplayToWorld] Cached RegistryAccess on world join (registries: {})",
                    cachedRegistryAccess.registries().count());
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("[ReplayToWorld] World/connection disconnected — cached RegistryAccess from this session is still available for conversion");
        });

        LOGGER.info("Replay-to-World loaded.");
    }
}
