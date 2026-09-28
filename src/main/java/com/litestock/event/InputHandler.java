package com.litestock.event;

import com.litestock.LiteStock;
import com.litestock.command.LiteStockCommands;
import com.litestock.config.Hotkeys;
import com.litestock.config.LiteStockConfig;
import com.litestock.gui.GuiConfigs;
import com.litestock.keybind.KeyBindings;
import com.litestock.render.ChestHighlightRenderer;
import com.litestock.scan.ContainerCache;
import com.litestock.scan.ContainerProbe;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {

    // 动态搜索状态：null=未搜索，非null=正在搜索这个物品集合
    private static Set<Item> searchMatchedItems = null;
    private static int lastSearchX = Integer.MIN_VALUE, lastSearchZ = Integer.MIN_VALUE;
    private static boolean searchReported = false;

    public InputHandler() {
        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new KeyCallbackOpenConfigGui());
        Hotkeys.TOGGLE_SCAN.getKeybind().setCallback(new KeyCallbackToggleScan());
        Hotkeys.ADD_CONTAINER.getKeybind().setCallback(new KeyCallbackAddContainer());
        Hotkeys.CLEAR_SELECTION.getKeybind().setCallback(new KeyCallbackClearSelection());
        Hotkeys.SEARCH_ITEM.getKeybind().setCallback(new KeyCallbackSearchItem());
        Hotkeys.ADD_SEARCH_BLOCK.getKeybind().setCallback(new KeyCallbackAddSearchBlock());
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(LiteStock.MOD_ID, "litestock.hotkeys.category.hotkeys", Hotkeys.HOTKEY_LIST);
    }

    private static boolean inGame(Minecraft mc) {
        return mc.player != null && mc.screen == null;
    }

    /** 每 tick 调用：动态扫描玩家周围匹配的展示物品。 */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (searchMatchedItems == null || mc.player == null || mc.level == null) return;

        // 打开容器界面就退出搜索模式，清除高亮
        if (mc.screen instanceof AbstractContainerScreen) {
            stopSearch();
            return;
        }

        // 玩家移动超过 8 格才重新扫，避免每 tick 都扫
        BlockPos center = mc.player.blockPosition();
        if (center.getX() - lastSearchX > 8 || center.getX() - lastSearchX < -8
                || center.getZ() - lastSearchZ > 8 || center.getZ() - lastSearchZ < -8) {
            lastSearchX = center.getX();
            lastSearchZ = center.getZ();

            // 缓存命中 + 周围扫描 + 本地存储，合并高亮
            List<BlockPos> cacheResults = searchCache(searchMatchedItems);
            List<BlockPos> visible = scanVisibleAround(mc, searchMatchedItems);
            List<BlockPos> localResults = searchLocalPoints(searchMatchedItems);
            Set<BlockPos> merged = new java.util.LinkedHashSet<>();
            merged.addAll(cacheResults);
            merged.addAll(visible);
            merged.addAll(localResults);
            ChestHighlightRenderer.getInstance().setHighlightedChests(new ArrayList<>(merged));
            LiteStockConfig.get().highlightEnabled = true;

            if (!searchReported) {
                searchReported = true;
                int total = merged.size();
                if (total > 0) {
                    mc.player.sendSystemMessage(Component.literal(
                            "[LiteStock] 命中缓存 " + cacheResults.size() + " 个，周围找到 " + visible.size() + " 个，本地存储 " + localResults.size() + " 个，共 " + total + " 个"
                    ).withStyle(ChatFormatting.GREEN));
                } else {
                    mc.player.sendSystemMessage(Component.literal(
                            "[LiteStock] 未找到该物品，建议用「添加方块」热键对准展示框/方块手动存储坐标"
                    ).withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    /** 从 ContainerCache 找包含匹配物品的箱子，跳过过期缓存。 */
    private static List<BlockPos> searchCache(Set<Item> matchedItems) {
        List<BlockPos> result = new ArrayList<>();
        long maxAgeMs = LiteStockConfig.get().cacheExpirySeconds * 1000L;
        for (BlockPos pos : LiteStockConfig.get().getSelectedContainerPositions()) {
            Set<Item> items = ContainerCache.getInstance().getItems(pos);
            if (items == null) continue;
            if (ContainerCache.getInstance().isExpired(pos, maxAgeMs)) continue;
            for (Item item : items) {
                if (matchedItems.contains(item)) {
                    result.add(pos);
                    break;
                }
            }
        }
        return result;
    }

    public static void stopSearch() {
        searchMatchedItems = null;
        ChestHighlightRenderer.getInstance().clear();
    }

    /** 是否正在物品搜索模式（搜索高亮优先级最高，HUD 不应覆盖）。 */
    public static boolean isSearching() {
        return searchMatchedItems != null;
    }


    /** 重置扫描位置，强制下次 tick 重新扫描高亮。 */
    public static void forceRescan() {
        lastSearchX = Integer.MIN_VALUE;
        lastSearchZ = Integer.MIN_VALUE;
    }
    /** 扫玩家周围的展示框 + 孤立展示方块，高亮展示位置本身。 */
    private static List<BlockPos> scanVisibleAround(Minecraft mc, Set<Item> matchedItems) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos center = mc.player.blockPosition();
        int r = 24;
        List<BlockPos> containers = LiteStockConfig.get().getSelectedContainerPositions();

        // 1. 扫物品展示框
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
                net.minecraft.world.item.ItemStack stack = frame.getItem();
                if (!stack.isEmpty() && matchedItems.contains(stack.getItem())) {
                    if (stack.getMaxStackSize() <= 1 || countNearbyContainers(frame.blockPosition(), containers) >= 2) {
                        result.add(frame.blockPosition().immutable());
                    }
                }
            }
        }

        // 2. 扫周围直接放置的方块，过滤连续墙块
        Set<Block> matchedBlocks = new HashSet<>();
        for (Item item : matchedItems) {
            Block block = Block.byItem(item);
            if (block != null && block != Blocks.AIR) {
                matchedBlocks.add(block);
            }
        }

        int foundRaw = 0;
        int filtered = 0;
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    mPos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    Block b = mc.level.getBlockState(mPos).getBlock();
                    if (!matchedBlocks.contains(b)) continue;
                    foundRaw++;
                    int run = countHorizontalRun(mc.level, mPos, b);
                    if (run <= 2 && countNearbyContainers(mPos, containers) >= 2) {
                        result.add(mPos.immutable());
                    } else {
                        filtered++;
                    }
                }
            }
        }
        LiteStock.LOGGER.info("Visible scan: foundRaw={}, filtered={}, result={}", foundRaw, filtered, result.size());

        return result;
    }

    /** 找 pos 附近最近的已选容器（3格内），没有返回 null。 */
    private static BlockPos findNearestContainer(BlockPos pos, List<BlockPos> containers) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos c : containers) {
            double d = c.distSqr(pos);
            if (d <= 9 && d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** 统计 pos 的 3×3×3 范围内有多少个已选容器。 */
    private static int countNearbyContainers(BlockPos pos, List<BlockPos> containers) {
        int count = 0;
        for (BlockPos c : containers) {
            if (Math.abs(c.getX() - pos.getX()) <= 1
                    && Math.abs(c.getY() - pos.getY()) <= 1
                    && Math.abs(c.getZ() - pos.getZ()) <= 1) {
                count++;
            }
        }
        return count;
    }

    /** 统计 pos 这个方块在水平方向（X 和 Z）上连续同方块的长度。 */
    private static int countHorizontalRun(net.minecraft.client.multiplayer.ClientLevel level, BlockPos pos, Block b) {
        int count = 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // X 方向
        for (int dx = 1; dx <= 3; dx++) {
            m.set(pos.getX() + dx, pos.getY(), pos.getZ());
            if (level.getBlockState(m).getBlock() == b) count++;
            else break;
        }
        for (int dx = 1; dx <= 3; dx++) {
            m.set(pos.getX() - dx, pos.getY(), pos.getZ());
            if (level.getBlockState(m).getBlock() == b) count++;
            else break;
        }
        if (count >= 3) return count; // X 方向已经连续 3 个以上，直接算墙

        // Z 方向
        count = 1;
        for (int dz = 1; dz <= 3; dz++) {
            m.set(pos.getX(), pos.getY(), pos.getZ() + dz);
            if (level.getBlockState(m).getBlock() == b) count++;
            else break;
        }
        for (int dz = 1; dz <= 3; dz++) {
            m.set(pos.getX(), pos.getY(), pos.getZ() - dz);
            if (level.getBlockState(m).getBlock() == b) count++;
            else break;
        }
        return count;
    }

    private static class KeyCallbackOpenConfigGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && action == KeyAction.PRESS) {
                mc.setScreen(new GuiConfigs());
                return true;
            }
            return false;
        }
    }

    private static class KeyCallbackToggleScan implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (action == KeyAction.PRESS && inGame(mc)) {
                LiteStockCommands.triggerScan();
                return true;
            }
            return false;
        }
    }

    private static class KeyCallbackAddContainer implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (action == KeyAction.PRESS && inGame(mc)) {
                KeyBindings.handleSelectArea();
                return true;
            }
            return false;
        }
    }

    private static class KeyCallbackClearSelection implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (action == KeyAction.PRESS && inGame(mc)) {
                stopSearch();
                KeyBindings.clearSelection();
                return true;
            }
            return false;
        }
    }

    private static class KeyCallbackSearchItem implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (action == KeyAction.PRESS && mc.player != null) {
                com.litestock.gui.SearchTextInput input = new com.litestock.gui.SearchTextInput(
                        200,
                        "搜索物品（输入中文或英文物品名）",
                        "",
                        mc.screen,
                        new ItemSearchConsumer()
                );
                input.setParent(mc.screen);
                mc.setScreen(input);
                return true;
            }
            return false;
        }
    }

    private static class ItemSearchConsumer implements IStringConsumer {
        @Override
        public void setString(String query) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (query == null || query.isBlank()) return;

            String q = query.toLowerCase(Locale.ROOT).trim();
            String qNorm = normalize(q);

            Set<Item> matchedItems = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                String displayName = new net.minecraft.world.item.ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                String displayNorm = normalize(displayName);
                String regPath = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
                if (displayName.contains(q) || displayNorm.contains(qNorm) || regPath.contains(q)) {
                    matchedItems.add(item);
                }
            }

            if (matchedItems.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 没找到匹配 '" + query + "' 的物品").withStyle(ChatFormatting.RED));
                return;
            }

            List<BlockPos> selected = LiteStockConfig.get().getSelectedContainerPositions();
            if (selected.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 没有已选容器，请先框选或加载预设").withStyle(ChatFormatting.RED));
                return;
            }

            long maxAgeMs = LiteStockConfig.get().cacheExpirySeconds * 1000L;
            int cachedCount = 0;
            for (BlockPos pos : selected) {
                if (ContainerCache.getInstance().getItems(pos) != null
                        && !ContainerCache.getInstance().isExpired(pos, maxAgeMs)) {
                    cachedCount++;
                }
            }

            if (cachedCount == 0) {
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 缓存为空或已过期，正在自动扫描 " + selected.size() + " 个箱子...").withStyle(ChatFormatting.YELLOW));
                scanAllForSearch(mc, selected, () -> mc.execute(() -> startSearchMode(matchedItems, q)));
            } else {
                startSearchMode(matchedItems, q);
            }
        }

        private void startSearchMode(Set<Item> matchedItems, String q) {
            searchMatchedItems = matchedItems;
            lastSearchX = Integer.MIN_VALUE;
            lastSearchZ = Integer.MIN_VALUE;
            searchReported = false;
            Minecraft mc = Minecraft.getInstance();
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 搜索模式：'" + q + "'，走到哪扫到哪，打开箱子自动退出"
            ).withStyle(ChatFormatting.GREEN));
        }

        private void scanAllForSearch(Minecraft mc, List<BlockPos> selected, Runnable after) {
            ChestHighlightRenderer renderer = ChestHighlightRenderer.getInstance();
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                var serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
                mc.getSingleplayerServer().execute(() -> {
                    for (BlockPos pos : selected) {
                        var be = serverLevel.getBlockEntity(pos);
                        if (be instanceof net.minecraft.world.Container container) {
                            java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
                            for (int i = 0; i < container.getContainerSize(); i++) {
                                stacks.add(container.getItem(i));
                            }
                            ContainerCache.getInstance().put(pos, stacks);
                        }
                    }
                    mc.execute(() -> { renderer.clearScanningContainers(); after.run(); });
                });
            } else {
                renderer.setScanningContainers(new HashSet<>(selected));
                ContainerProbe probe = ContainerProbe.getInstance();
                probe.stop();
                probe.resetCooldown();
                probe.setProgressCallback(pos -> mc.execute(() -> renderer.removeScanningContainer(pos)));
                probe.startProbe(selected, () -> {
                    for (BlockPos pos : selected) {
                        java.util.List<net.minecraft.world.item.ItemStack> items = probe.getResults().get(pos);
                        if (items != null) ContainerCache.getInstance().put(pos, items);
                    }
                    mc.execute(() -> { renderer.clearScanningContainers(); after.run(); });
                });
            }
        }
    }


    /** 从本地存储找匹配物品的坐标。 */
    private static List<BlockPos> searchLocalPoints(Set<Item> matchedItems) {
        List<BlockPos> result = new ArrayList<>();
        for (String entry : LiteStockConfig.get().localSearchPoints) {
            String[] parts = entry.split(";");
            if (parts.length != 4) continue;
            try {
                String regPath = parts[0];
                for (Item item : matchedItems) {
                    if (BuiltInRegistries.ITEM.getKey(item).toString().equals(regPath)) {
                        result.add(new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static class KeyCallbackAddSearchBlock implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (action != KeyAction.PRESS) return false;
            if (mc.player == null) return false;
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 添加方块热键触发, hitResult=" + mc.hitResult));

            Item foundItem = null;
            BlockPos foundPos = null;

            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                    && entityHit.getEntity() instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
                net.minecraft.world.item.ItemStack stack = frame.getItem();
                if (!stack.isEmpty()) {
                    foundItem = stack.getItem();
                    foundPos = frame.blockPosition();
                }
            }
            else if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                Block block = mc.level.getBlockState(pos).getBlock();
                Item item = block.asItem();
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    foundItem = item;
                    foundPos = pos;
                }
            }

            if (foundItem == null || foundPos == null) {
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 准星未对准展示框或方块").withStyle(ChatFormatting.RED));
                return true;
            }

            String regPath = BuiltInRegistries.ITEM.getKey(foundItem).toString();
            String entry = regPath + ";" + foundPos.getX() + ";" + foundPos.getY() + ";" + foundPos.getZ();
            if (!LiteStockConfig.get().localSearchPoints.contains(entry)) {
                LiteStockConfig.get().localSearchPoints.add(entry);
                LiteStockConfig.save();
                String name = new net.minecraft.world.item.ItemStack(foundItem).getHoverName().getString();
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 已存储: " + name + " @ " + foundPos.toShortString()).withStyle(ChatFormatting.GREEN));
            } else {
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 已存在该记录").withStyle(ChatFormatting.YELLOW));
            }
            return true;
        }
    }
    private static String normalize(String s) {
        return s.replace('栓', '拴');
    }
}
