package com.litestock.gui;

import java.util.Collections;
import java.util.List;

import com.litestock.LiteStock;
import com.litestock.config.Configs;
import com.litestock.config.Hotkeys;
import com.litestock.config.LiteStockConfig;
import com.litestock.config.PresetConfig;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiConfigs extends GuiConfigsBase {
    private static ConfigGuiTab tab = ConfigGuiTab.GENERIC;

    public GuiConfigs() {
        super(10, 50, LiteStock.MOD_ID, null, "litestock.gui.title.configs", LiteStock.MOD_VERSION);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();

        int x = 10;
        int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            x += this.createButton(x, y, -1, tab);
        }

        if (tab == ConfigGuiTab.GENERIC) {
            int presetX = 10;
            int presetY = 50;
            this.createPresetButton(presetX, presetY);
        }
    }

    private void createPresetButton(int x, int y) {
        String currentName = LiteStockConfig.get().currentPresetName;
        String label = "预设: " + currentName + " (点击切换)";
        ButtonGeneric button = new ButtonGeneric(x, y, 200, 20, label);
        this.addButton(button, new PresetButtonListener(this));
    }

    private int createButton(int x, int y, int width, ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth() + 2;
    }

    @Override
    protected int getConfigWidth() {
        return 200;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<? extends IConfigBase> configs;
        ConfigGuiTab tab = GuiConfigs.tab;

        if (tab == ConfigGuiTab.GENERIC) {
            configs = Configs.Generic.OPTIONS;
        } else if (tab == ConfigGuiTab.HOTKEYS) {
            configs = Hotkeys.HOTKEY_LIST;
        } else {
            return Collections.emptyList();
        }

        return ConfigOptionWrapper.createFor(configs);
    }

    private static class ButtonListener implements IButtonActionListener {
        private final GuiConfigs parent;
        private final ConfigGuiTab tab;

        public ButtonListener(ConfigGuiTab tab, GuiConfigs parent) {
            this.tab = tab;
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GuiConfigs.tab = this.tab;

            this.parent.reCreateListWidget();
            this.parent.getListWidget().resetScrollbarPosition();
            this.parent.initGui();
        }
    }

    private static class PresetButtonListener implements IButtonActionListener {
        private final GuiConfigs parent;

        public PresetButtonListener(GuiConfigs parent) {
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            PresetConfig presetConfig = PresetConfig.get();
            LiteStockConfig config = LiteStockConfig.get();
            List<String> presets = presetConfig.listPresets();

            if (presets.isEmpty()) {
                return;
            }

            int currentIndex = presets.indexOf(config.currentPresetName);
            int nextIndex = (currentIndex + 1) % presets.size();
            String nextName = presets.get(nextIndex);

            presetConfig.loadPreset(nextName, config);
            button.setDisplayString("预设: " + nextName + " (点击切换)");
        }
    }

    public enum ConfigGuiTab {
        GENERIC ("litestock.gui.button.config_gui.generic"),
        HOTKEYS ("litestock.gui.button.config_gui.hotkeys");

        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}
