package com.litestock.litematica;

import com.litestock.LiteStock;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSorter;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class MaterialListReader {
    private static boolean pending = false;

    public static boolean isPending() {
        return pending;
    }

    private static Map<Item, Integer> currentRequirements = new HashMap<>();
    private static Map<Item, Integer> currentMissingMaterials = new HashMap<>();
    private static Map<Item, Integer> currentHudDisplayedMaterials = new HashMap<>();

    public static Map<Item, Integer> getCurrentRequirements() {
        return currentRequirements;
    }

    public static Map<Item, Integer> getCurrentMissingMaterials() {
        return currentMissingMaterials;
    }

    public static Map<Item, Integer> getCurrentHudDisplayedMaterials() {
        return currentHudDisplayedMaterials;
    }

    private static boolean isLitematicaHudActive() {
        try {
            MaterialListBase ml = DataManager.getMaterialList();
            if (ml != null && ml.getHudRenderer().getShouldRenderCustom()) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private static boolean isFangkuaiHudActive() {
        return FangkuaiHudReader.isModLoaded() && FangkuaiHudReader.isHudActive();
    }

    public static void refreshMissingMaterials() {
        boolean litematicaActive = isLitematicaHudActive();
        boolean fangkuaiActive = isFangkuaiHudActive();

        if (fangkuaiActive) {
            Map<Item, Integer> fangkuaiItems = FangkuaiHudReader.getHudDisplayedMissingItems();
            currentHudDisplayedMaterials = fangkuaiItems;
            currentMissingMaterials = fangkuaiItems;
            LiteStock.LOGGER.debug("refreshMissingMaterials: using fangkuai-material HUD, {} items", fangkuaiItems.size());
            return;
        }

        if (litematicaActive) {
            MaterialListBase existing = DataManager.getMaterialList();
            if (existing != null && !existing.getMaterialsAll().isEmpty()) {
                currentHudDisplayedMaterials = extractHudDisplayedMaterials(existing);
                currentMissingMaterials = extractAllMissingMaterials(existing);
                LiteStock.LOGGER.debug("refreshMissingMaterials: using Litematica HUD, {} hud items", currentHudDisplayedMaterials.size());
                return;
            }
        }

        currentHudDisplayedMaterials = Collections.emptyMap();
        currentMissingMaterials = Collections.emptyMap();
        LiteStock.LOGGER.debug("refreshMissingMaterials: no HUD active");
    }

    public static void requestRequiredItems(Consumer<Set<Item>> callback) {
        requestRequiredItemsWithQuantities(items -> callback.accept(items.keySet()));
    }

    public static void requestRequiredItemsWithQuantities(Consumer<Map<Item, Integer>> callback) {
        if (pending) {
            LiteStock.LOGGER.warn("MaterialListReader: already pending, ignoring duplicate request");
            return;
        }

        boolean litematicaActive = isLitematicaHudActive();
        boolean fangkuaiActive = isFangkuaiHudActive();

        if (fangkuaiActive) {
            Map<Item, Integer> fangkuaiItems = FangkuaiHudReader.getHudDisplayedMissingItems();
            currentRequirements = fangkuaiItems;
            currentHudDisplayedMaterials = fangkuaiItems;
            currentMissingMaterials = fangkuaiItems;
            LiteStock.LOGGER.info("requestRequiredItems: using fangkuai-material HUD, {} items", fangkuaiItems.size());
            callback.accept(fangkuaiItems);
            return;
        }

        if (litematicaActive) {
            MaterialListBase existing = DataManager.getMaterialList();
            if (existing != null && !existing.getMaterialsAll().isEmpty()) {
                Map<Item, Integer> hudDisplayed = extractHudDisplayedMaterials(existing);
                Map<Item, Integer> allMissing = extractAllMissingMaterials(existing);
                currentRequirements = hudDisplayed;
                currentHudDisplayedMaterials = hudDisplayed;
                currentMissingMaterials = allMissing;
                LiteStock.LOGGER.info("requestRequiredItems: using Litematica HUD, {} items", hudDisplayed.size());
                callback.accept(hudDisplayed);
                return;
            }
        }

        currentRequirements = Collections.emptyMap();
        currentHudDisplayedMaterials = Collections.emptyMap();
        currentMissingMaterials = Collections.emptyMap();
        LiteStock.LOGGER.info("requestRequiredItems: no HUD active, returning empty");
        callback.accept(Collections.emptyMap());
    }

    private static Set<Item> extractItems(MaterialListBase ml) {
        Set<Item> items = new HashSet<>();
        for (MaterialListEntry entry : ml.getMaterialsAll()) {
            items.add(entry.getStack().getItem());
        }
        return items;
    }

    private static Map<Item, Integer> extractItemsWithQuantities(MaterialListBase ml) {
        Map<Item, Integer> items = new HashMap<>();
        for (MaterialListEntry entry : ml.getMaterialsAll()) {
            Item item = entry.getStack().getItem();
            items.merge(item, entry.getCountTotal(), Integer::sum);
        }
        return items;
    }

    private static Map<Item, Integer> extractHudDisplayedMaterials(MaterialListBase ml) {
        List<MaterialListEntry> list = new ArrayList<>(ml.getMaterialsMissingOnly(false));
        MaterialListSorter sorter = new MaterialListSorter(ml);
        list.sort(sorter);
        int maxLines = Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.getIntegerValue();
        int size = Math.min(list.size(), maxLines);
        Map<Item, Integer> items = new HashMap<>();
        for (int i = 0; i < size; i++) {
            MaterialListEntry entry = list.get(i);
            Item item = entry.getStack().getItem();
            int count = ml.getMultiplier() == 1
                    ? entry.getCountMissing()
                    : ml.getMultiplier() * entry.getCountTotal();
            items.merge(item, count, Integer::sum);
        }
        return items;
    }

    private static Map<Item, Integer> extractAllMissingMaterials(MaterialListBase ml) {
        List<MaterialListEntry> list = ml.getMaterialsMissingOnly(false);
        Map<Item, Integer> items = new HashMap<>();
        for (MaterialListEntry entry : list) {
            Item item = entry.getStack().getItem();
            int count = ml.getMultiplier() == 1
                    ? entry.getCountMissing()
                    : ml.getMultiplier() * entry.getCountTotal();
            items.merge(item, count, Integer::sum);
        }
        return items;
    }

}
