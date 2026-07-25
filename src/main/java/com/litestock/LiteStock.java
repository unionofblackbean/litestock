package com.litestock;

import com.litestock.command.LiteStockCommands;
import com.litestock.config.Configs;
import com.litestock.config.LiteStockConfig;
import com.litestock.config.PresetConfig;
import com.litestock.render.ChestHighlightRenderer;
import com.litestock.scan.ContainerProbe;
import com.litestock.scan.HudAutoScanner;
import com.litestock.scan.InventoryTracker;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiteStock implements ClientModInitializer {
    public static final String MOD_ID = "litestock";
    public static final String MOD_NAME = "LiteStock";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LiteStockConfig.load();
        PresetConfig.load();

        LiteStockCommands.init();

        RenderEventHandler.getInstance().registerWorldLastRenderer(ChestHighlightRenderer.getInstance());

        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());

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
