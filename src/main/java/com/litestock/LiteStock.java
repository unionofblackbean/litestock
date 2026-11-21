package com.litestock;

import com.litestock.command.LiteStockCommands;
import com.litestock.config.Configs;
import com.litestock.config.LiteStockConfig;
import com.litestock.config.PresetConfig;
import com.litestock.render.WorldRenderHook;
import com.litestock.scan.ContainerProbe;
import com.litestock.scan.HudAutoScanner;
import com.litestock.scan.InventoryTracker;
import com.litestock.scan.SelectionManager;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiteStock implements ClientModInitializer {
    public static final String MOD_ID = "litestock";
    public static final String MOD_NAME = "LiteStock";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void debugLog(String message, Object... args) {
        if (Configs.Generic.DEBUG_LOGGING.getBooleanValue()) {
            LOGGER.info("[LiteStock Debug] " + message, args);
        }
    }

    @Override
    public void onInitializeClient() {
        LiteStockConfig.load();
        PresetConfig.load();

        LiteStockCommands.init();

        RenderEventHandler.getInstance().registerWorldLastRenderer(new WorldRenderHook());

        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());

        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> SelectionManager.getInstance().clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContainerProbe.getInstance().onClientTick();
            InventoryTracker.getInstance().updateCollectedCounts();
            HudAutoScanner.onClientTick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LiteStockConfig.save();
            Configs.saveToFile();
        });

        LOGGER.info("LiteStock initialized - Litematica logistics restocking helper");
    }
}
