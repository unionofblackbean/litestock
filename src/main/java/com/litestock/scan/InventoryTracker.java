package com.litestock.scan;

import com.litestock.LiteStock;
import com.litestock.litematica.MaterialListReader;
import com.litestock.render.ChestHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InventoryTracker {
    private static final InventoryTracker INSTANCE = new InventoryTracker();

    private final Map<Item, Integer> collectedCounts = new HashMap<>();
    private boolean tracking = false;

    public static InventoryTracker getInstance() {
        return INSTANCE;
    }

    private InventoryTracker() {}

    public void startTracking() {
        tracking = true;
        updateCollectedCounts();
    }

    public void stopTracking() {
        tracking = false;
        collectedCounts.clear();
    }

    public boolean isTracking() {
        return tracking;
    }

    public void updateCollectedCounts() {
        if (!tracking) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        collectedCounts.clear();

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                collectedCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                if (stack.has(DataComponents.CONTAINER)) {
                    ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                    if (contents != null) {
                        for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                            collectedCounts.merge(inner.getItem(), inner.getCount(), Integer::sum);
                        }
                    }
                }
            }
        }

        MaterialListReader.refreshMissingMaterials();
        Map<Item, Integer> missingMaterials = MaterialListReader.getCurrentMissingMaterials();
        if (!missingMaterials.isEmpty()) {
            updateHighlightsFromMissing(missingMaterials);
        }
    }

    public int getCollectedCount(Item item) {
        return collectedCounts.getOrDefault(item, 0);
    }

    public Map<Item, Integer> getCollectedCounts() {
        return new HashMap<>(collectedCounts);
    }

    public Set<Item> getFulfilledItems(Map<Item, Integer> requirements) {
        Set<Item> fulfilled = new java.util.HashSet<>();
        for (Map.Entry<Item, Integer> req : requirements.entrySet()) {
            if (collectedCounts.getOrDefault(req.getKey(), 0) >= req.getValue()) {
                fulfilled.add(req.getKey());
            }
        }
        return fulfilled;
    }

    private void updateHighlightsFromMissing(Map<Item, Integer> missingMaterials) {
        // 重新获取前N个缺失物品（收集完成后顺位补充）
        Map<Item, Integer> hudDisplayed = MaterialListReader.getCurrentHudDisplayedMaterials();
        Set<Item> hudItemSet = hudDisplayed.keySet();

        if (hudItemSet.isEmpty()) {
            ChestHighlightRenderer.getInstance().setHighlightedChests(new java.util.ArrayList<>());
            return;
        }

        // 用更新后的前N个物品重新匹配所有选中的容器
        com.litestock.config.LiteStockConfig config = com.litestock.config.LiteStockConfig.get();
        java.util.List<net.minecraft.core.BlockPos> selected = config.getSelectedContainerPositions();
        java.util.List<net.minecraft.core.BlockPos> matched =
                ContainerScanner.matchFromCachePublic(selected, hudItemSet);
        ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
    }

    public void updateHighlightsFromCache() {
        MaterialListReader.refreshMissingMaterials();
        Map<Item, Integer> missingMaterials = MaterialListReader.getCurrentMissingMaterials();
        if (!missingMaterials.isEmpty()) {
            updateHighlightsFromMissing(missingMaterials);
        }
    }
}
