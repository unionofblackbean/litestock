package com.litestock.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.litestock.LiteStock;
import net.minecraft.core.BlockPos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Preset config for shareable container position data.
 * Stored separately from the main config so it can be shared independently.
 */
public class PresetConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Paths.get("config", LiteStock.MOD_ID);
    private static final Path PRESET_FILE = CONFIG_DIR.resolve("preset.json");

    private Map<String, List<String>> presets = new HashMap<>();

    public Map<String, List<String>> getPresets() {
        return presets;
    }

    public boolean hasPreset(String name) {
        return presets.containsKey(name);
    }

    public List<BlockPos> getPresetPositions(String name) {
        List<String> coords = presets.get(name);
        if (coords == null) return new ArrayList<>();
        return parsePositions(coords);
    }

    public void savePreset(String name, List<BlockPos> positions) {
        List<String> coords = new ArrayList<>();
        for (BlockPos pos : positions) {
            coords.add(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        presets.put(name, coords);
        save();
    }

    public boolean loadPreset(String name, LiteStockConfig config) {
        List<String> coords = presets.get(name);
        if (coords == null) return false;
        config.selectedContainers = new ArrayList<>(coords);
        config.currentPresetName = name;
        LiteStockConfig.save();
        return true;
    }

    public boolean deletePreset(String name) {
        if (!presets.containsKey(name)) return false;
        presets.remove(name);
        save();
        return true;
    }

    public List<String> listPresets() {
        List<String> list = new ArrayList<>(presets.keySet());
        java.util.Collections.sort(list);
        return list;
    }

    private List<BlockPos> parsePositions(List<String> coords) {
        List<BlockPos> list = new ArrayList<>();
        for (String s : coords) {
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

    private static PresetConfig instance;

    public static PresetConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(PRESET_FILE)) {
                String json = Files.readString(PRESET_FILE);
                Type type = new TypeToken<PresetConfig>() {}.getType();
                instance = GSON.fromJson(json, type);
                if (instance == null) {
                    instance = new PresetConfig();
                }
            } else {
                String embeddedJson = loadEmbeddedResource("/default_preset.json");
                if (embeddedJson != null) {
                    Type type = new TypeToken<PresetConfig>() {}.getType();
                    instance = GSON.fromJson(embeddedJson, type);
                    if (instance != null) {
                        save();
                        LiteStock.LOGGER.info("Loaded default presets from embedded resources");
                    } else {
                        instance = new PresetConfig();
                    }
                } else {
                    instance = new PresetConfig();
                }
            }
        } catch (Exception e) {
            LiteStock.LOGGER.error("Failed to load preset config", e);
            instance = new PresetConfig();
        }
    }

    private static String loadEmbeddedResource(String path) {
        try (InputStream is = PresetConfig.class.getResourceAsStream(path)) {
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
            if (instance == null) {
                instance = new PresetConfig();
            }
            String json = GSON.toJson(instance);
            Files.writeString(PRESET_FILE, json);
        } catch (IOException e) {
            LiteStock.LOGGER.error("Failed to save preset config", e);
        }
    }
}
