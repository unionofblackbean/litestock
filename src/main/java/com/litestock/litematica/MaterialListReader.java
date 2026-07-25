package com.litestock.litematica;

import com.litestock.LiteStock;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSorter;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the required item list from Litematica's loaded schematic placements.
 *
 * <p>Workflow:
 * <ol>
 *   <li>First checks {@link DataManager#getMaterialList()} – if the user already opened
 *       Litematica's Material List GUI, the data is available immediately.</li>
 *   <li>Otherwise, creates a {@link MaterialListPlacement} for every enabled placement
 *       with {@code reCreate=true}, which schedules an async block-counting task.
 *       The {@link ICompletionListener} fires when all tasks finish.</li>
 * </ol>
 */
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

    public static void refreshMissingMaterials() {
        MaterialListBase existing = DataManager.getMaterialList();
        if (existing != null && !existing.getMaterialsAll().isEmpty()) {
            currentHudDisplayedMaterials = extractHudDisplayedMaterials(existing);
            currentMissingMaterials = extractAllMissingMaterials(existing);
        }
    }

    public static void requestRequiredItems(Consumer<Set<Item>> callback) {
        requestRequiredItemsWithQuantities(items -> callback.accept(items.keySet()));
    }

    public static void requestRequiredItemsWithQuantities(Consumer<Map<Item, Integer>> callback) {
        if (pending) {
            LiteStock.LOGGER.warn("MaterialListReader: already pending, ignoring duplicate request");
            return;
        }

        MaterialListBase existing = DataManager.getMaterialList();
        if (existing != null && !existing.getMaterialsAll().isEmpty()) {
            Map<Item, Integer> result = extractItemsWithQuantities(existing);
            currentRequirements = result;
            currentHudDisplayedMaterials = extractHudDisplayedMaterials(existing);
            currentMissingMaterials = extractAllMissingMaterials(existing);
            callback.accept(result);
            return;
        }

        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager()
                .getAllSchematicsPlacements();

        if (placements.isEmpty()) {
            currentRequirements = Collections.emptyMap();
            callback.accept(Collections.emptyMap());
            return;
        }

        pending = true;
        Map<Item, Integer> allItems = new HashMap<>();
        int[] remaining = {placements.size()};

        if (remaining[0] == 0) {
            pending = false;
            currentRequirements = Collections.emptyMap();
            callback.accept(Collections.emptyMap());
            return;
        }

        for (SchematicPlacement placement : placements) {
            MaterialListPlacement ml = new MaterialListPlacement(placement, true);
            ml.setCompletionListener(new ICompletionListener() {
                @Override
                public void onTaskCompleted() {
                    for (MaterialListEntry entry : ml.getMaterialsAll()) {
                        Item item = entry.getStack().getItem();
                        allItems.merge(item, entry.getCountTotal(), Integer::sum);
                    }
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        pending = false;
                        currentRequirements = allItems;
                        currentHudDisplayedMaterials = extractHudDisplayedFromAllPlacements(placements);
                        currentMissingMaterials = extractAllMissingFromAllPlacements(placements);
                        callback.accept(allItems);
                    }
                }

                @Override
                public void onTaskAborted() {
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        pending = false;
                        currentRequirements = allItems;
                        currentHudDisplayedMaterials = extractHudDisplayedFromAllPlacements(placements);
                        currentMissingMaterials = extractAllMissingFromAllPlacements(placements);
                        callback.accept(allItems);
                    }
                }
            });
        }
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

    private static Map<Item, Integer> extractHudDisplayedFromAllPlacements(List<SchematicPlacement> placements) {
        Map<Item, Integer> items = new HashMap<>();
        for (SchematicPlacement placement : placements) {
            MaterialListPlacement ml = new MaterialListPlacement(placement, false);
            Map<Item, Integer> displayed = extractHudDisplayedMaterials(ml);
            for (Map.Entry<Item, Integer> entry : displayed.entrySet()) {
                items.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return items;
    }

    private static Map<Item, Integer> extractAllMissingFromAllPlacements(List<SchematicPlacement> placements) {
        Map<Item, Integer> items = new HashMap<>();
        for (SchematicPlacement placement : placements) {
            MaterialListPlacement ml = new MaterialListPlacement(placement, false);
            Map<Item, Integer> missing = extractAllMissingMaterials(ml);
            for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
                items.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return items;
    }
}
