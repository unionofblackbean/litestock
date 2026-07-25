package com.litestock;

import com.litestock.config.Configs;
import com.litestock.event.InputHandler;
import com.litestock.gui.GuiConfigs;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

public class InitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(LiteStock.MOD_ID, new Configs());

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(LiteStock.MOD_ID, LiteStock.MOD_NAME, GuiConfigs::new)
        );

        InputHandler handler = new InputHandler();
        InputEventHandler.getKeybindManager().registerKeybindProvider(handler);
    }
}
