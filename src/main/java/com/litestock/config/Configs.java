package com.litestock.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.litestock.LiteStock;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.util.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;

public class Configs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = LiteStock.MOD_ID + ".json";

    private static final String GENERIC_KEY = LiteStock.MOD_ID + ".config.generic";

    public static class Generic {
        public static final ConfigBoolean AUTO_SCAN_HUD = new ConfigBoolean(
                "autoScanHud", true,
                GENERIC_KEY + ".comment.autoScanHud",
                "HUD自动扫描",
                GENERIC_KEY + ".name.autoScanHud"
        );

        public static final ConfigInteger CACHE_EXPIRY_SECONDS = new ConfigInteger(
                "cacheExpirySeconds", 300, 10, 3600,
                GENERIC_KEY + ".comment.cacheExpirySeconds",
                "缓存过期时间(秒)",
                GENERIC_KEY + ".name.cacheExpirySeconds"
        );

        public static final ConfigColor HIGHLIGHT_COLOR = new ConfigColor(
                "highlightColor", "#FFFF00",
                GENERIC_KEY + ".comment.highlightColor",
                "高亮颜色",
                GENERIC_KEY + ".name.highlightColor"
        );

        public static final ConfigDouble HIGHLIGHT_LINE_WIDTH = new ConfigDouble(
                "highlightLineWidth", 2.5, 0.5, 10.0,
                GENERIC_KEY + ".comment.highlightLineWidth",
                "高亮框线宽",
                GENERIC_KEY + ".name.highlightLineWidth"
        );

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                AUTO_SCAN_HUD,
                CACHE_EXPIRY_SECONDS,
                HIGHLIGHT_COLOR,
                HIGHLIGHT_LINE_WIDTH
        );
    }

    public static void loadFromFile() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile)) {
            com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
            try {
                String content = Files.readString(configFile);
                JsonElement element = parser.parse(content);

                if (element != null && element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();
                    ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                    ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
                }
            } catch (Exception e) {
                LiteStock.LOGGER.error("Failed to load config file '{}'", configFile.toAbsolutePath(), e);
            }
        } else {
            LiteStock.LOGGER.warn("Config file '{}' not found, using defaults", configFile.toAbsolutePath());
        }
    }

    public static void saveToFile() {
        Path dir = FileUtils.getConfigDirectory();

        if (!Files.exists(dir)) {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir)) {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
            Path configFile = dir.resolve(CONFIG_FILE_NAME);
            try {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                Files.writeString(configFile, gson.toJson(root));
            } catch (Exception e) {
                LiteStock.LOGGER.error("Failed to save config file '{}'", configFile.toAbsolutePath(), e);
            }
        }
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }

    @Override
    public void onConfigsChanged() {
        saveToFile();
        loadFromFile();
    }
}
