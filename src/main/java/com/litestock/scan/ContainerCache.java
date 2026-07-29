package com.litestock.scan;

import com.litestock.LiteStock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches container contents (per BlockPos) so we can look them up later
 * without re-opening the container.  The cache is populated either by
 * {@link ContainerScanner} (integrated server) or by intercepting
 * ScreenHandler syncs (multiplayer, future work).
 */
public class ContainerCache {
    /** Position → set of items stored in that container */
    private final Map<BlockPos, Set<Item>> itemCache = new HashMap<>();
    /** Position → raw item stacks (for detailed lookups) */
    private final Map<BlockPos, List<ItemStack>> stackCache = new HashMap<>();
    /** Position → item count map */
    private final Map<BlockPos, Map<Item, Integer>> itemCountCache = new HashMap<>();
    /** Position → timestamp (ms) of last update */
    private final Map<BlockPos, Long> timestamps = new HashMap<>();

    private static final ContainerCache INSTANCE = new ContainerCache();

    public static ContainerCache getInstance() {
        return INSTANCE;
    }

    private ContainerCache() {}

    public void put(BlockPos pos, List<ItemStack> stacks) {
        Set<Item> items = new java.util.HashSet<>();
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                Item item = s.getItem();
                items.add(item);
                counts.merge(item, s.getCount(), Integer::sum);
                if (s.has(DataComponents.CONTAINER)) {
                    ItemContainerContents contents = s.get(DataComponents.CONTAINER);
                    if (contents != null) {
                        for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                            Item innerItem = inner.getItem();
                            items.add(innerItem);
                            counts.merge(innerItem, inner.getCount(), Integer::sum);
                        }
                    }
                }
            }
        }
        BlockPos key = pos.immutable();
        itemCache.put(key, items);
        stackCache.put(key, new ArrayList<>(stacks));
        itemCountCache.put(key, counts);
        timestamps.put(key, System.currentTimeMillis());
    }

    public Map<Item, Integer> getItemCounts(BlockPos pos) {
        return itemCountCache.get(pos);
    }

    public Set<Item> getItems(BlockPos pos) {
        return itemCache.get(pos);
    }

    public List<ItemStack> getStacks(BlockPos pos) {
        return stackCache.get(pos);
    }

    public boolean hasCache(BlockPos pos) {
        return itemCache.containsKey(pos);
    }

    public boolean isExpired(BlockPos pos, long maxAgeMs) {
        Long ts = timestamps.get(pos);
        if (ts == null) return true;
        return System.currentTimeMillis() - ts > maxAgeMs;
    }

    /** Returns all cached positions within the given radius of center. */
    public List<BlockPos> getCachedPositions(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();
        int r2 = radius * radius;
        for (BlockPos pos : itemCache.keySet()) {
            int dx = pos.getX() - center.getX();
            int dy = pos.getY() - center.getY();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                result.add(pos);
            }
        }
        return result;
    }

    public void clear() {
        itemCache.clear();
        stackCache.clear();
        itemCountCache.clear();
        timestamps.clear();
    }

    /** Number of currently cached containers. */
    public int size() {
        return itemCache.size();
    }

    public void clearExpired(long maxAgeMs) {
        long now = System.currentTimeMillis();
        itemCache.entrySet().removeIf(e -> {
            Long ts = timestamps.get(e.getKey());
            return ts == null || now - ts > maxAgeMs;
        });
        stackCache.entrySet().removeIf(e -> !itemCache.containsKey(e.getKey()));
        itemCountCache.entrySet().removeIf(e -> !itemCache.containsKey(e.getKey()));
        timestamps.entrySet().removeIf(e -> !itemCache.containsKey(e.getKey()));
    }
}
