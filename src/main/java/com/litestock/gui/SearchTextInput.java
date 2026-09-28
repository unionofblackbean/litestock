package com.litestock.gui;

import com.litestock.LiteStock;
import com.litestock.config.LiteStockConfig;
import com.litestock.render.ChestHighlightRenderer;
import com.litestock.scan.ContainerCache;
import com.litestock.scan.ContainerProbe;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;

public class SearchTextInput extends GuiTextInput {

    public SearchTextInput(int width, String title, String defaultText, net.minecraft.client.gui.screens.Screen parent, IStringConsumer consumer) {
        super(width, "物品查找", defaultText, parent, consumer);
    }

    @Override
    public void initGui() {
        super.initGui();

        int btnW = 80;
        int gap = 4;

        try {
            List<ButtonBase> realButtons = getRealButtonList();
            if (realButtons != null) {
                ButtonBase resetBtn = null;
                for (ButtonBase b : realButtons) {
                    if (getButtonText(b).equals("重置")) {
                        resetBtn = b;
                        break;
                    }
                }
                if (resetBtn != null) {
                    realButtons.remove(resetBtn);
                    for (ButtonBase b : realButtons) {
                        if (b.getX() > resetBtn.getX()) {
                            b.setX(b.getX() - btnW - gap);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LiteStock.LOGGER.error("删重置按钮失败", e);
        }

        int y = this.height / 2 + 22;
        int startX = this.width / 2;

        try {
            List<ButtonBase> realButtons = getRealButtonList();
            if (realButtons != null && !realButtons.isEmpty()) {
                ButtonBase rightmost = realButtons.get(0);
                for (ButtonBase b : realButtons) {
                    if (b.getX() > rightmost.getX()) rightmost = b;
                }
                startX = rightmost.getX() + rightmost.getWidth() + gap;
                y = rightmost.getY();
            }
        } catch (Exception e) {
            LiteStock.LOGGER.error("反射读按钮失败", e);
        }

        ButtonGeneric clearCache = new ButtonGeneric(startX, y, btnW, 20, "重置缓存");
        this.addButton(clearCache, new ClearCacheListener());

        ButtonGeneric startScan = new ButtonGeneric(startX + btnW + gap, y, btnW, 20, "开始扫描");
        this.addButton(startScan, new StartScanListener());
    }

    @SuppressWarnings("unchecked")
    private List<ButtonBase> getRealButtonList() {
        Class<?> cls = this.getClass();
        while (cls != null) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(this);
                        if (val instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof ButtonBase) {
                            return (List<ButtonBase>) list;
                        }
                    } catch (IllegalAccessException ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private String getButtonText(ButtonBase b) {
        try {
            java.lang.reflect.Method m = b.getClass().getMethod("getMessage");
            Object msg = m.invoke(b);
            return msg.toString();
        } catch (Exception e) {
            try {
                java.lang.reflect.Field f = b.getClass().getDeclaredField("displayString");
                f.setAccessible(true);
                Object val = f.get(b);
                return val != null ? val.toString() : "";
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private static class ClearCacheListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            ContainerCache.getInstance().clear();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 缓存已清空").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static class StartScanListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            Minecraft mc = Minecraft.getInstance();
            List<BlockPos> selected = LiteStockConfig.get().getSelectedContainerPositions();
            if (selected.isEmpty()) return;

            mc.setScreen(null);
            mc.player.sendSystemMessage(Component.literal(
                    "[LiteStock] 开始扫描 " + selected.size() + " 个箱子...").withStyle(ChatFormatting.YELLOW));

            ChestHighlightRenderer renderer = ChestHighlightRenderer.getInstance();
            Runnable after = () -> {
                renderer.clearScanningContainers();
                mc.player.sendSystemMessage(Component.literal(
                        "[LiteStock] 扫描完成，缓存已更新").withStyle(ChatFormatting.GREEN));
                if (com.litestock.event.InputHandler.isSearching()) {
                    com.litestock.event.InputHandler.forceRescan();
                }
            };
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
                    mc.execute(after);
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
                    mc.execute(after);
                });
            }
        }
    }
}