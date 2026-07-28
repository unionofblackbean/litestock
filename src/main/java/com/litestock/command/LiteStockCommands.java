package com.litestock.command;

import com.litestock.config.LiteStockConfig;
import com.litestock.config.PresetConfig;
import com.litestock.litematica.MaterialListReader;
import com.litestock.render.ChestHighlightRenderer;
import com.litestock.scan.ContainerCache;
import com.litestock.scan.ContainerProbe;
import com.litestock.scan.ContainerScanner;
import com.litestock.scan.InventoryTracker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class LiteStockCommands {

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("litestock")
                    .then(ClientCommands.literal("scan")
                            .then(ClientCommands.literal("start")
                                    .executes(ctx -> scanStart()))
                            .then(ClientCommands.literal("stop")
                                    .executes(ctx -> scanStop()))
                            .then(ClientCommands.literal("show")
                                    .executes(ctx -> scanShow()))
                            .then(ClientCommands.literal("clear")
                                    .executes(ctx -> scanClear()))
                            .then(ClientCommands.literal("save_cache")
                                    .then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 3))
                                            .executes(ctx -> saveCache(IntegerArgumentType.getInteger(ctx, "slot")))))
                            .then(ClientCommands.literal("cache")
                                    .then(ClientCommands.argument("slot", IntegerArgumentType.integer(1, 3))
                                            .executes(ctx -> loadCache(IntegerArgumentType.getInteger(ctx, "slot")))))
                            .executes(ctx -> scan()))
                    .then(ClientCommands.literal("clear")
                            .executes(ctx -> clear()))
                    .then(ClientCommands.literal("debug")
                            .then(ClientCommands.literal("items")
                                    .executes(ctx -> debugItems())))
                    .then(ClientCommands.literal("preset")
                            .then(ClientCommands.literal("save")
                                    .then(ClientCommands.argument("name", StringArgumentType.string())
                                            .executes(ctx -> presetSave(StringArgumentType.getString(ctx, "name")))))
                            .then(ClientCommands.literal("load")
                                    .then(ClientCommands.argument("name", StringArgumentType.string())
                                            .executes(ctx -> presetLoad(StringArgumentType.getString(ctx, "name")))))
                            .then(ClientCommands.literal("list")
                                    .executes(ctx -> presetList()))
                            .then(ClientCommands.literal("delete")
                                    .then(ClientCommands.argument("name", StringArgumentType.string())
                                            .executes(ctx -> presetDelete(StringArgumentType.getString(ctx, "name")))))
                    )
            );
        });
    }

    private static int scan() {
        triggerScan();
        return 1;
    }

    private static int scanStart() {
        triggerScanWithProgress();
        return 1;
    }

    private static int scanStop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        ContainerProbe probe = ContainerProbe.getInstance();
        if (!probe.isProbing()) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 当前没有进行中的扫描"
            ).withStyle(ChatFormatting.GRAY));
            return 1;
        }

        probe.stop();
        LiteStockConfig.get().highlightEnabled = false;
        ChestHighlightRenderer.getInstance().clear();
        ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
        InventoryTracker.getInstance().stopTracking();

        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已停止扫描"
        ).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int scanShow() {
        showFromCache();
        return 1;
    }

    private static int scanClear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        ContainerCache cache = ContainerCache.getInstance();
        int cacheCount = cache.size();
        cache.clear();

        LiteStockConfig config = LiteStockConfig.get();
        int selectionCount = config.getSelectedContainerPositions().size();
        config.clearSelectedContainers();
        LiteStockConfig.save();

        config.highlightEnabled = false;
        ChestHighlightRenderer.getInstance().clear();
        ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
        InventoryTracker.getInstance().stopTracking();

        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已清空缓存（" + cacheCount + " 个容器）和选区（" + selectionCount + " 个容器）"
        ).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int saveCache(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        ContainerCache cache = ContainerCache.getInstance();
        int count = cache.size();
        boolean success = cache.saveToSlot(slot);

        if (success) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 已保存 " + count + " 个容器到 " + slot + " 号缓存槽"
            ).withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 保存失败，缓存槽编号必须是 1-3"
            ).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int loadCache(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        ContainerCache cache = ContainerCache.getInstance();
        if (!cache.hasSlot(slot)) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] " + slot + " 号缓存槽为空，请先使用 save_cache 保存"
            ).withStyle(ChatFormatting.RED));
            return 1;
        }

        boolean success = cache.loadFromSlot(slot);
        if (success) {
            int count = cache.getSlotSize(slot);
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 已从 " + slot + " 号缓存槽加载 " + count + " 个容器"
            ).withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 加载失败，缓存槽编号必须是 1-3"
            ).withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    public static void triggerScan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LiteStockConfig config = LiteStockConfig.get();
        ContainerProbe probe = ContainerProbe.getInstance();

        if (probe.isProbing()) {
            probe.stop();
            config.highlightEnabled = false;
            ChestHighlightRenderer.getInstance().clear();
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 已停止扫描").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (config.highlightEnabled) {
            config.highlightEnabled = false;
            ChestHighlightRenderer.getInstance().clear();
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
        }

        if (MaterialListReader.isPending()) {
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取投影物品列表，请稍候...").withStyle(ChatFormatting.YELLOW));
            return;
        }

        mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取材料列表HUD...").withStyle(ChatFormatting.YELLOW));
        MaterialListReader.requestRequiredItemsWithQuantities(items -> {
            if (items.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 未检测到材料列表HUD，请先打开投影或方块清单的材料列表HUD").withStyle(ChatFormatting.RED));
                return;
            }
            mc.player.sendSystemMessage(Component.literal("[LiteStock] HUD显示 " + items.size() + " 种物品，正在扫描箱子...").withStyle(ChatFormatting.YELLOW));

            ContainerScanner.scanAndHighlight(items.keySet(), count -> {
                config.highlightEnabled = true;
                ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
                if (count == 0) {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 未找到包含所需物品的箱子").withStyle(ChatFormatting.GOLD));
                } else {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 已高亮 " + count + " 个箱子").withStyle(ChatFormatting.GREEN));
                }
            });
        });
    }

    public static void triggerScanWithProgress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LiteStockConfig config = LiteStockConfig.get();
        ContainerProbe probe = ContainerProbe.getInstance();

        if (probe.isProbing()) {
            probe.stop();
            config.highlightEnabled = false;
            ChestHighlightRenderer.getInstance().clear();
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 已停止扫描").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (config.highlightEnabled) {
            config.highlightEnabled = false;
            ChestHighlightRenderer.getInstance().clear();
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
        }

        if (MaterialListReader.isPending()) {
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取投影物品列表，请稍候...").withStyle(ChatFormatting.YELLOW));
            return;
        }

        mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取材料列表HUD...").withStyle(ChatFormatting.YELLOW));
        MaterialListReader.requestRequiredItemsWithQuantities(items -> {
            if (items.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 未检测到材料列表HUD，请先打开投影或方块清单的材料列表HUD").withStyle(ChatFormatting.RED));
                return;
            }
            mc.player.sendSystemMessage(Component.literal("[LiteStock] HUD显示 " + items.size() + " 种物品，正在扫描箱子...").withStyle(ChatFormatting.YELLOW));

            ContainerScanner.scanWithProgress(items.keySet(), count -> {
                config.highlightEnabled = true;
                InventoryTracker.getInstance().startTracking();
                ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
                InventoryTracker.getInstance().updateHighlightsFromCache();
                if (count == 0) {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 未找到包含所需物品的箱子").withStyle(ChatFormatting.GOLD));
                } else {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 已高亮 " + count + " 个箱子").withStyle(ChatFormatting.GREEN));
                }
            });
        });
    }

    public static void showFromCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LiteStockConfig config = LiteStockConfig.get();

        if (config.highlightEnabled) {
            config.highlightEnabled = false;
            ChestHighlightRenderer.getInstance().clear();
            ChestHighlightRenderer.getInstance().setScanningProgress(0, 0);
            InventoryTracker.getInstance().stopTracking();
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 已关闭高亮显示").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (MaterialListReader.isPending()) {
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取投影物品列表，请稍候...").withStyle(ChatFormatting.YELLOW));
            return;
        }

        mc.player.sendSystemMessage(Component.literal("[LiteStock] 正在读取材料列表HUD...").withStyle(ChatFormatting.YELLOW));
        MaterialListReader.requestRequiredItemsWithQuantities(items -> {
            if (items.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal("[LiteStock] 未检测到材料列表HUD，请先打开投影或方块清单的材料列表HUD").withStyle(ChatFormatting.RED));
                return;
            }

            ContainerScanner.highlightFromCache(items.keySet(), count -> {
                config.highlightEnabled = true;
                InventoryTracker.getInstance().startTracking();
                InventoryTracker.getInstance().updateHighlightsFromCache();
                if (count == 0) {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 缓存中未找到包含所需物品的箱子").withStyle(ChatFormatting.GOLD));
                } else {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 已从缓存高亮 " + count + " 个箱子").withStyle(ChatFormatting.GREEN));
                }
            });
        });
    }

    private static int clear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        LiteStockConfig.get().highlightEnabled = false;
        ChestHighlightRenderer.getInstance().clear();
        InventoryTracker.getInstance().stopTracking();
        mc.player.sendSystemMessage(Component.literal("[LiteStock] 已清除高亮").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int debugItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        if (MaterialListReader.isPending()) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 正在读取投影物品列表，请稍候..."
            ).withStyle(ChatFormatting.YELLOW));
            return 1;
        }

        MaterialListReader.requestRequiredItems(items -> {
            if (items.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 未检测到材料列表HUD，请先打开投影的材料列表HUD（M键）"
                ).withStyle(ChatFormatting.RED));
                return;
            }
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] HUD显示共 " + items.size() + " 种物品："
            ).withStyle(ChatFormatting.YELLOW));

            List<String> names = items.stream()
                    .map(item -> item.getDefaultInstance().getHoverName().getString())
                    .sorted()
                    .toList();

            for (int i = 0; i < Math.min(names.size(), 20); i++) {
                mc.player.sendSystemMessage(Component.literal(
                        "  " + (i + 1) + ". " + names.get(i)
                ).withStyle(ChatFormatting.GRAY));
            }

            if (names.size() > 20) {
                mc.player.sendSystemMessage(Component.literal(
                        "  ... 还有 " + (names.size() - 20) + " 种物品"
                ).withStyle(ChatFormatting.GRAY));
            }
        });
        return 1;
    }

    private static int presetSave(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        LiteStockConfig config = LiteStockConfig.get();
        List<net.minecraft.core.BlockPos> positions = config.getSelectedContainerPositions();
        if (positions.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 没有选中的容器，请先用 K 键框选容器"
            ).withStyle(ChatFormatting.RED));
            return 1;
        }

        PresetConfig.get().savePreset(name, positions);
        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已保存预设 '" + name + "'（" + positions.size() + " 个容器）到 config/litestock/preset.json"
        ).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int presetLoad(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        PresetConfig preset = PresetConfig.get();
        if (!preset.hasPreset(name)) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 预设 '" + name + "' 不存在"
            ).withStyle(ChatFormatting.RED));
            return 1;
        }

        int count = preset.getPresetPositions(name).size();
        preset.loadPreset(name, LiteStockConfig.get());
        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已加载预设 '" + name + "'（" + count + " 个容器）"
        ).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int presetList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        List<String> names = PresetConfig.get().listPresets();
        if (names.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 没有已保存的预设"
            ).withStyle(ChatFormatting.GRAY));
            return 1;
        }

        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已保存的预设（" + names.size() + " 个）："
        ).withStyle(ChatFormatting.YELLOW));
        for (String name : names) {
            int count = PresetConfig.get().getPresetPositions(name).size();
            mc.player.sendSystemMessage(Component.literal(
                    "  - " + name + "（" + count + " 个容器）"
            ).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int presetDelete(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;

        if (PresetConfig.get().deletePreset(name)) {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 已删除预设 '" + name + "'"
            ).withStyle(ChatFormatting.GREEN));
        } else {
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 预设 '" + name + "' 不存在"
            ).withStyle(ChatFormatting.RED));
        }
        return 1;
    }
}
