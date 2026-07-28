package com.litestock.litematica;

import com.litestock.LiteStock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过反射读取 fangkuai-material mod 的备货清单 HUD 中显示的物品。
 *
 * <p>fangkuai-material 是一个独立的 Fabric mod，有自己的 HUD 系统。
 * 本类使用反射检测该 mod 是否安装，并读取其 HUD 上实际显示的物品列表，
 * 避免编译时硬依赖。
 */
public class FangkuaiHudReader {
    private static final String MOD_ID = "fangkuai-material";
    private static Boolean modLoaded = null;

    // 反射缓存
    private static Class<?> hudConfigsClass;
    private static Class<?> syncServiceClass;
    private static Class<?> displaySettingsClass;
    private static Object enabledConfig;
    private static Object showCompletedConfig;
    private static Object showAllMaterialsConfig;
    private static Method getBooleanValueMethod;
    private static Method getInstanceMethod;
    private static Method claimedMaterialsMethod;
    private static Method showsMethod;
    private static Method materialIdMethod;
    private static Method materialMissingMethod;
    private static Method materialSyncedCompleteMethod;
    private static Method materialAssignedToCurrentMethod;

    // 配置缓存
    private static Object maxRowsConfig;
    private static Object maxColumnsConfig;
    private static Object multiColumnEnabledConfig;
    private static boolean reflectionInitialized = false;

    public static boolean isModLoaded() {
        if (modLoaded == null) {
            modLoaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
        }
        return modLoaded;
    }

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            hudConfigsClass = Class.forName("fangkuai.material.config.HudConfigs");
            syncServiceClass = Class.forName("fangkuai.material.collaboration.CollaborationSyncService");
            displaySettingsClass = Class.forName("fangkuai.material.config.MaterialDisplaySettings");

            Field enabledField = hudConfigsClass.getField("ENABLED");
            enabledConfig = enabledField.get(null);

            Field showCompletedField = hudConfigsClass.getField("SHOW_COMPLETED");
            showCompletedConfig = showCompletedField.get(null);

            Field showAllField = hudConfigsClass.getField("SHOW_ALL_MATERIALS");
            showAllMaterialsConfig = showAllField.get(null);

            Field maxRowsField = hudConfigsClass.getField("MAX_ROWS");
            maxRowsConfig = maxRowsField.get(null);

            Field maxColumnsField = hudConfigsClass.getField("MAX_COLUMNS");
            maxColumnsConfig = maxColumnsField.get(null);

            Field multiColumnField = hudConfigsClass.getField("MULTI_COLUMN_ENABLED");
            multiColumnEnabledConfig = multiColumnField.get(null);

            getBooleanValueMethod = enabledConfig.getClass().getMethod("getBooleanValue");
            getInstanceMethod = syncServiceClass.getMethod("getInstance");
            claimedMaterialsMethod = syncServiceClass.getMethod("claimedMaterials", boolean.class);
            showsMethod = displaySettingsClass.getMethod("shows", String.class);

            Class<?> claimedMaterialClass = Class.forName(
                    "fangkuai.material.collaboration.CollaborationBoard$ClaimedMaterial");
            materialIdMethod = claimedMaterialClass.getMethod("id");
            materialMissingMethod = claimedMaterialClass.getMethod("missingCount");
            materialSyncedCompleteMethod = claimedMaterialClass.getMethod("syncedComplete");
            materialAssignedToCurrentMethod = claimedMaterialClass.getMethod("assignedToCurrent");

            LiteStock.LOGGER.info("FangkuaiHudReader: 反射初始化成功");
        } catch (Exception e) {
            LiteStock.LOGGER.warn("FangkuaiHudReader: 反射初始化失败 - {}", e.getMessage());
        }
    }

    /**
     * 检测 fangkuai-material 的 HUD 是否开启。
     */
    public static boolean isHudActive() {
        if (!isModLoaded()) return false;
        try {
            initReflection();
            if (enabledConfig == null) return false;
            return (boolean) getBooleanValueMethod.invoke(enabledConfig);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取 fangkuai-material HUD 中显示的缺失物品列表。
     * 完全复刻 HUD 的过滤逻辑：SHOW_COMPLETED、MaterialDisplaySettings.shows、行数限制。
     *
     * @return Item -> 缺失数量的映射，如果 HUD 未开启或 mod 未安装返回空 Map
     */
    public static Map<Item, Integer> getHudDisplayedMissingItems() {
        if (!isModLoaded() || !isHudActive()) return new HashMap<>();
        return extractHudItems(true);
    }

    /**
     * 读取 fangkuai-material HUD 中显示的所有物品列表（包括已完成的）。
     */
    public static Map<Item, Integer> getHudDisplayedAllItems() {
        if (!isModLoaded() || !isHudActive()) return new HashMap<>();
        return extractHudItems(false);
    }

    private static Map<Item, Integer> extractHudItems(boolean missingOnly) {
        Map<Item, Integer> result = new HashMap<>();
        try {
            initReflection();
            if (syncServiceClass == null) return result;

            boolean showAll = (boolean) getBooleanValueMethod.invoke(showAllMaterialsConfig);
            boolean showCompleted = (boolean) getBooleanValueMethod.invoke(showCompletedConfig);

            Object service = getInstanceMethod.invoke(null);
            @SuppressWarnings("unchecked")
            List<Object> materials = (List<Object>) claimedMaterialsMethod.invoke(service, showAll);

            // 复刻 HUD 过滤逻辑
            List<Object> filtered = new ArrayList<>();
            for (Object material : materials) {
                boolean syncedComplete = (boolean) materialSyncedCompleteMethod.invoke(material);
                String materialId = (String) materialIdMethod.invoke(material);

                // 过滤：SHOW_COMPLETED || !syncedComplete
                if (!showCompleted && syncedComplete) continue;
                // 过滤：MaterialDisplaySettings.shows(id)
                if (!(boolean) showsMethod.invoke(null, materialId)) continue;

                filtered.add(material);
            }

            // 应用行数/列数限制（与 HUD 布局一致）
            int maxRows = getConfigInt(maxRowsConfig);
            int maxColumns = multiColumnEnabledConfig != null && getConfigBoolean(multiColumnEnabledConfig)
                    ? Math.max(1, getConfigInt(maxColumnsConfig))
                    : 1;
            int visibleCount = Math.min(filtered.size(), maxRows * maxColumns);

            for (int i = 0; i < visibleCount; i++) {
                Object material = filtered.get(i);
                String itemId = (String) materialIdMethod.invoke(material);
                int missing = (int) materialMissingMethod.invoke(material);

                if (missingOnly && missing <= 0) continue;

                Item item = parseItem(itemId);
                if (item != null) {
                    result.merge(item, Math.max(1, missing), Integer::sum);
                }
            }

            LiteStock.LOGGER.debug("FangkuaiHudReader: HUD显示 {} 种物品（missingOnly={}）",
                    result.size(), missingOnly);
        } catch (Exception e) {
            LiteStock.LOGGER.warn("FangkuaiHudReader: 读取HUD物品失败 - {}", e.getMessage());
        }
        return result;
    }

    private static int getConfigInt(Object config) {
        try {
            Method getInt = config.getClass().getMethod("getIntegerValue");
            return (int) getInt.invoke(config);
        } catch (Exception e) {
            return 8;
        }
    }

    private static boolean getConfigBoolean(Object config) {
        try {
            return (boolean) getBooleanValueMethod.invoke(config);
        } catch (Exception e) {
            return false;
        }
    }

    private static Item parseItem(String itemId) {
        return ItemAlias.resolveItem(itemId);
    }
}
