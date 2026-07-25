package com.litestock.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.litestock.LiteStock;
import net.minecraft.core.BlockPos;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LiteStockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get("config", LiteStock.MOD_ID);
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("litestock.json");

    // Highlight settings
    public int highlightColor = 0xFFFFFF00; // ARGB: semi-transparent yellow
    public boolean highlightEnabled = false;
    public float lineWidth = 2.0f;
    public float expand = 0.002f;

    // Cache settings
    public int cacheExpirySeconds = 300;

    // HUD settings
    public boolean hudEnabled = true;
    public int hudColorBg = 0xA0000000;
    public int hudColorBorder = 0xFF404040;
    public int hudColorText = 0xFFFFFFFF;
    public int hudColorAccent = 0xFFFFFF00;

    // Selected containers
    public List<String> selectedContainers = new ArrayList<>();
    public String currentPresetName = "default";

    public boolean isContainerSelected(BlockPos pos) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return selectedContainers.contains(key);
    }

    public void toggleContainerSelected(BlockPos pos) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        if (selectedContainers.contains(key)) {
            selectedContainers.remove(key);
        } else {
            selectedContainers.add(key);
        }
    }

    public void clearSelectedContainers() {
        selectedContainers.clear();
    }

    public List<BlockPos> getSelectedContainerPositions() {
        List<BlockPos> list = new ArrayList<>();
        for (String s : selectedContainers) {
            String[] parts = s.split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    list.add(new BlockPos(x, y, z));
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    private static LiteStockConfig instance;

    public static LiteStockConfig get() {
        if (instance == null) {
            instance = new LiteStockConfig();
        }
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                instance = GSON.fromJson(json, LiteStockConfig.class);
                if (instance == null) {
                    instance = new LiteStockConfig();
                }
            } else {
                String embeddedJson = loadEmbeddedResource("/default_litestock.json");
                if (embeddedJson != null) {
                    instance = GSON.fromJson(embeddedJson, LiteStockConfig.class);
                    if (instance != null) {
                        save();
                        LiteStock.LOGGER.info("Loaded default container list from embedded resources");
                    } else {
                        instance = new LiteStockConfig();
                    }
                } else {
                    instance = new LiteStockConfig();
                }
            }
        } catch (Exception e) {
            LiteStock.LOGGER.error("Failed to load config", e);
            instance = new LiteStockConfig();
        }
    }

    private static String loadEmbeddedResource(String path) {
        try (InputStream is = LiteStockConfig.class.getResourceAsStream(path)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            LiteStock.LOGGER.warn("Failed to load embedded resource: " + path, e);
            return null;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            String json = GSON.toJson(get());
            Files.writeString(CONFIG_FILE, json);
        } catch (Exception e) {
            LiteStock.LOGGER.error("Failed to save config", e);
        }
    }
}
