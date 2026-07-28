package com.litestock.scan;

import com.litestock.config.LiteStockConfig;
import com.litestock.render.ChestHighlightRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ContainerScanner {

    private static List<BlockPos> sortByDistance(List<BlockPos> positions, BlockPos origin) {
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return sorted;
    }

    public static void scanAndHighlight(Set<Item> requiredItems, Consumer<Integer> callback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            callback.accept(0);
            return;
        }

        LiteStockConfig config = LiteStockConfig.get();
        List<BlockPos> selected = sortByDistance(config.getSelectedContainerPositions(), mc.player.blockPosition());

        if (selected.isEmpty()) {
            callback.accept(0);
            return;
        }

        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            if (serverLevel == null) {
                callback.accept(0);
                return;
            }
            mc.getSingleplayerServer().execute(() -> {
                List<BlockPos> matched = scanSelectedSingleplayer(serverLevel, selected, requiredItems);
                mc.execute(() -> {
                    ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
                    callback.accept(matched.size());
                });
            });
        } else {
            scanSelectedMultiplayer(selected, requiredItems, callback);
        }
    }

    public static void scanWithProgress(Set<Item> requiredItems, Consumer<Integer> callback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            callback.accept(0);
            return;
        }

        LiteStockConfig config = LiteStockConfig.get();
        List<BlockPos> selected = sortByDistance(config.getSelectedContainerPositions(), mc.player.blockPosition());

        if (selected.isEmpty()) {
            callback.accept(0);
            return;
        }

        // 只扫描过期/缺失缓存的容器
        long maxAgeMs = config.cacheExpirySeconds * 1000L;
        List<BlockPos> needScan = new ArrayList<>();
        for (BlockPos pos : selected) {
            if (ContainerCache.getInstance().isExpired(pos, maxAgeMs)) {
                needScan.add(pos);
            }
        }

        if (needScan.isEmpty()) {
            List<BlockPos> matched = matchFromCache(selected, requiredItems);
            ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
            callback.accept(matched.size());
            return;
        }

        ChestHighlightRenderer renderer = ChestHighlightRenderer.getInstance();
        renderer.setScanningProgress(0, needScan.size());
        renderer.setScanningContainers(new HashSet<>(needScan));

        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            if (serverLevel == null) {
                renderer.setScanningProgress(0, 0);
                renderer.clearScanningContainers();
                callback.accept(0);
                return;
            }
            mc.getSingleplayerServer().execute(() -> {
                int[] scanned = {0};
                for (BlockPos pos : needScan) {
                    BlockEntity be = serverLevel.getBlockEntity(pos);
                    if (be instanceof Container container) {
                        List<ItemStack> stacks = new ArrayList<>();
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            stacks.add(container.getItem(i));
                        }
                        ContainerCache.getInstance().put(pos, stacks);
                    }
                    scanned[0]++;
                    int finalScanned = scanned[0];
                    mc.execute(() -> {
                        renderer.setScanningProgress(finalScanned, needScan.size());
                        renderer.removeScanningContainer(pos);
                    });
                }
                mc.execute(() -> {
                    renderer.setScanningProgress(0, 0);
                    renderer.clearScanningContainers();
                    List<BlockPos> matched = matchFromCache(selected, requiredItems);
                    ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
                    callback.accept(matched.size());
                });
            });
        } else {
            scanSelectedMultiplayerWithProgress(selected, requiredItems, callback);
        }
    }

    public static void highlightFromCache(Set<Item> requiredItems, Consumer<Integer> callback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            callback.accept(0);
            return;
        }

        LiteStockConfig config = LiteStockConfig.get();
        List<BlockPos> selected = config.getSelectedContainerPositions();

        if (selected.isEmpty()) {
            callback.accept(0);
            return;
        }

        List<BlockPos> matched = matchFromCache(selected, requiredItems);
        ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
        callback.accept(matched.size());
    }

    private static void scanSelectedMultiplayer(List<BlockPos> positions,
                                                 Set<Item> required, Consumer<Integer> callback) {
        ContainerCache cache = ContainerCache.getInstance();
        long maxAgeMs = LiteStockConfig.get().cacheExpirySeconds * 1000L;

        List<BlockPos> needProbe = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (cache.isExpired(pos, maxAgeMs)) {
                needProbe.add(pos);
            }
        }

        if (needProbe.isEmpty()) {
            List<BlockPos> matched = matchFromCache(positions, required);
            ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
            callback.accept(matched.size());
            return;
        }

        ContainerProbe.getInstance().startProbe(needProbe, () -> {
            for (BlockPos pos : needProbe) {
                List<ItemStack> items = ContainerProbe.getInstance().getResults().get(pos);
                if (items != null) {
                    cache.put(pos, items);
                }
            }
            List<BlockPos> matched = matchFromCache(positions, required);
            ChestHighlightRenderer.getInstance().setHighlightedChests(matched);
            callback.accept(matched.size());
        });
    }

    private static void scanSelectedMultiplayerWithProgress(List<BlockPos> positions,
                                                            Set<Item> required, Consumer<Integer> callback) {
        ContainerCache cache = ContainerCache.getInstance();
        long maxAgeMs = LiteStockConfig.get().cacheExpirySeconds * 1000L;

        List<BlockPos> needProbe = new ArrayList<>();
        List<BlockPos> cached = new ArrayList<>();

        for (BlockPos pos : positions) {
            if (cache.isExpired(pos, maxAgeMs)) {
                needProbe.add(pos);
            } else {
                cached.add(pos);
            }
        }

        List<BlockPos> matched = matchFromCache(cached, required);
        ChestHighlightRenderer.getInstance().setHighlightedChests(matched);

        if (needProbe.isEmpty()) {
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
            ChestHighlightRenderer.getInstance().clearScanningContainers();
            callback.accept(matched.size());
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ChestHighlightRenderer renderer = ChestHighlightRenderer.getInstance();
        renderer.setScanningProgress(cached.size(), positions.size());
        renderer.setScanningContainers(new HashSet<>(needProbe));

        ContainerProbe.getInstance().startProbe(needProbe, () -> {
            for (BlockPos pos : needProbe) {
                List<ItemStack> items = ContainerProbe.getInstance().getResults().get(pos);
                if (items != null) {
                    cache.put(pos, items);
                }
                mc.execute(() -> renderer.removeScanningContainer(pos));
            }

            int scanned = cached.size() + needProbe.size();
            mc.execute(() -> {
                renderer.setScanningProgress(scanned, positions.size());
                renderer.clearScanningContainers();

                List<BlockPos> allMatched = matchFromCache(positions, required);
                ChestHighlightRenderer.getInstance().setHighlightedChests(allMatched);
                callback.accept(allMatched.size());
            });
        });

        ContainerProbe.getInstance().setProgressCallback(pos -> mc.execute(() -> {
            int current = cached.size() + (needProbe.size() - ContainerProbe.getInstance().getQueueSize());
            renderer.setScanningProgress(current, positions.size());
            renderer.removeScanningContainer(pos);
        }));
    }

    private static List<BlockPos> scanSelectedSingleplayer(ServerLevel level,
                                                            List<BlockPos> positions, Set<Item> required) {
        List<BlockPos> matched = new ArrayList<>();
        ContainerCache cache = ContainerCache.getInstance();

        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                List<ItemStack> stacks = new ArrayList<>();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    stacks.add(container.getItem(i));
                }
                cache.put(pos, stacks);
                if (containsAny(stacks, required)) {
                    matched.add(pos.immutable());
                }
            }
        }

        return matched;
    }

    /**
     * 从缓存匹配包含所需物品的容器位置。供 {@link HudAutoScanner} 在 HUD 激活时立即调用。
     */
    public static List<BlockPos> matchFromCachePublic(List<BlockPos> positions, Set<Item> required) {
        return matchFromCache(positions, required);
    }

    private static List<BlockPos> matchFromCache(List<BlockPos> positions, Set<Item> required) {
        List<BlockPos> matched = new ArrayList<>();
        ContainerCache cache = ContainerCache.getInstance();

        // 扩展 required 集合，包含所有物品别名
        Set<Item> expanded = expandWithAliases(required);

        for (BlockPos pos : positions) {
            Set<Item> items = cache.getItems(pos);
            if (items == null) continue;
            for (Item item : items) {
                if (expanded.contains(item)) {
                    matched.add(pos);
                    break;
                }
            }
        }

        return matched;
    }

    private static boolean containsAny(List<ItemStack> stacks, Set<Item> required) {
        Set<Item> expanded = expandWithAliases(required);

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;

            if (expanded.contains(stack.getItem())) {
                return true;
            }

            if (stack.has(DataComponents.CONTAINER)) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                        if (expanded.contains(inner.getItem())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 将物品集合扩展为包含所有别名物品的集合。
     */
    private static Set<Item> expandWithAliases(Set<Item> items) {
        Set<Item> result = new HashSet<>(items);
        for (Item item : items) {
            result.addAll(com.litestock.litematica.ItemAlias.getMatchingItems(item));
        }
        return result;
    }
}
