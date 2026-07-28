package com.litestock.scan;

import com.litestock.LiteStock;
import com.litestock.config.LiteStockConfig;
import com.litestock.litematica.FangkuaiHudReader;
import com.litestock.litematica.MaterialListReader;
import com.litestock.render.ChestHighlightRenderer;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.Minecraft;

import java.util.Set;

/**
 * 监控备货清单 HUD 状态（支持 Litematica 和 fangkuai-material 两个 mod）。
 *
 * 工作流程（优先从本地缓存读取，避免每次 HUD 激活都触发网络扫描）：
 * 1. HUD 激活时，先尝试从 {@link ContainerCache} 匹配已缓存的容器并立即高亮
 * 2. 后台触发 {@link ContainerProbe} 扫描过期/缺失的容器
 * 3. HUD 关闭时清除高亮
 */
public class HudAutoScanner {
    private static boolean lastHudActive = false;
    private static boolean autoScanEnabled = true;

    public static void setAutoScanEnabled(boolean enabled) {
        autoScanEnabled = enabled;
    }

    public static boolean isAutoScanEnabled() {
        return autoScanEnabled;
    }

    public static void onClientTick() {
        if (!autoScanEnabled) return;

        boolean hudActive = checkHudActive();

        if (hudActive && !lastHudActive) {
            LiteStock.LOGGER.info("Material list HUD activated, highlighting from cache");
            highlightFromCacheThenProbeMissing();
        } else if (!hudActive && lastHudActive) {
            LiteStock.LOGGER.info("Material list HUD deactivated, clearing highlights");
            clearHighlights();
        }

        lastHudActive = hudActive;
    }

    /**
     * 检测任意一个备货清单 HUD 是否开启。
     * 优先检查 Litematica，其次检查 fangkuai-material。
     */
    private static boolean checkHudActive() {
        // 检查 Litematica 的材料列表 HUD
        try {
            MaterialListBase ml = DataManager.getMaterialList();
            if (ml != null && ml.getHudRenderer().getShouldRenderCustom()) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }

        // 检查 fangkuai-material 的 HUD
        if (FangkuaiHudReader.isModLoaded() && FangkuaiHudReader.isHudActive()) {
            return true;
        }

        return false;
    }

    /**
     * 第一步：立即从缓存匹配并高亮（同步、无网络开销）。
     * 第二步：后台扫描过期/缺失的容器，扫描完成后更新高亮。
     */
    private static void highlightFromCacheThenProbeMissing() {
        if (MaterialListReader.isPending()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MaterialListReader.requestRequiredItemsWithQuantities(hudItems -> {
            if (hudItems.isEmpty()) return;

            LiteStockConfig config = LiteStockConfig.get();
            java.util.List<net.minecraft.core.BlockPos> selected = config.getSelectedContainerPositions();

            if (selected.isEmpty()) {
                return;
            }

            java.util.List<net.minecraft.core.BlockPos> matched =
                    ContainerScanner.matchFromCachePublic(selected, hudItems.keySet());
            ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
            config.highlightEnabled = true;
            InventoryTracker.getInstance().startTracking();

            // 检查是否有过期/缺失的缓存，决定是否需要后台扫描
            long maxAgeMs = config.cacheExpirySeconds * 1000L;
            boolean needScan = false;
            for (net.minecraft.core.BlockPos pos : selected) {
                if (ContainerCache.getInstance().isExpired(pos, maxAgeMs)) {
                    needScan = true;
                    break;
                }
            }

            if (needScan) {
                ContainerScanner.scanWithProgress(hudItems.keySet(), count -> {
                    if (LiteStockConfig.get().highlightEnabled) {
                        InventoryTracker.getInstance().updateHighlightsFromCache();
                    }
                });
            }
        });
    }

    private static void clearHighlights() {
        LiteStockConfig.get().highlightEnabled = false;
        ChestHighlightRenderer.getInstance().clear();
        ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
        InventoryTracker.getInstance().stopTracking();
    }
}
