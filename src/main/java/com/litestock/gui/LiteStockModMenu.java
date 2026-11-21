package com.litestock.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fi.dy.masa.malilib.gui.GuiBase;

public final class LiteStockModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<GuiBase> getModConfigScreenFactory() {
        return parent -> new GuiConfigs().setParent(parent);
    }
}
