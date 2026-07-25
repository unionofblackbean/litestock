package com.litestock.gui;

import com.litestock.config.LiteStockConfig;
import com.litestock.config.PresetArea;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GuiLiteStock extends GuiBase {

    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 22;

    private boolean showPresetMenu = false;

    public GuiLiteStock() {
        this.setTitle("LiteStock - 投影备货助手");
    }

    @Override
    public void initGui() {
        super.initGui();

        int x = 12;
        int y = 30;

        ButtonGeneric listBtn = new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                "选中的容器 (" + LiteStockConfig.get().getSelectedContainerPositions().size() + ")");
        listBtn.setActionListener(new IButtonActionListener() {
            @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                openGui(new GuiContainerListEdit(GuiLiteStock.this));
            }
        });
        this.addButton(listBtn, null);
        y += ROW_SPACING;

        ButtonGeneric clearBtn = new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "清除所有选中容器");
        clearBtn.setActionListener(new IButtonActionListener() {
            @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                LiteStockConfig config = LiteStockConfig.get();
                config.selectedContainers.clear();
                LiteStockConfig.save();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[LiteStock] 已清除所有选中容器").withStyle(ChatFormatting.GOLD));
                }
                clearButtons();
                initGui();
            }
        });
        this.addButton(clearBtn, null);
        y += ROW_SPACING;

        y += 4;
        ButtonGeneric presetBtn = new ButtonGeneric(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "预设区域 ▼");
        presetBtn.setActionListener(new IButtonActionListener() {
            @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                showPresetMenu = !showPresetMenu;
                clearButtons();
                initGui();
            }
        });
        this.addButton(presetBtn, null);
        y += ROW_SPACING;

        if (showPresetMenu) {
            int indentX = x + 16;
            for (PresetArea preset : PresetArea.BUILTIN_PRESETS) {
                ButtonGeneric pBtn = new ButtonGeneric(indentX, y, BUTTON_WIDTH - 16, BUTTON_HEIGHT, preset.name);
                pBtn.setActionListener(new IButtonActionListener() {
                    @Override
                    public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                        addAreaContainers(preset);
                        showPresetMenu = false;
                        clearButtons();
                        initGui();
                    }
                });
                this.addButton(pBtn, null);
                y += ROW_SPACING;
            }
        }
    }

    private void addAreaContainers(PresetArea preset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        LiteStockConfig config = LiteStockConfig.get();
        int count = 0;

        int minCX = (preset.min.getX()) >> 4;
        int maxCX = (preset.max.getX()) >> 4;
        int minCZ = (preset.min.getZ()) >> 4;
        int maxCZ = (preset.max.getZ()) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                var chunk = mc.level.getChunk(cx, cz);
                for (var be : chunk.getBlockEntities().values()) {
                    var pos = be.getBlockPos();
                    if (!preset.contains(pos)) continue;
                    if (!(be instanceof net.minecraft.world.Container)) continue;
                    if (config.isContainerSelected(pos)) continue;
                    config.toggleContainerSelected(pos);
                    count++;
                }
            }
        }

        LiteStockConfig.save();
        mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 已添加 " + count + " 个容器 (" + preset.name + ")"
        ).withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void drawContents(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        this.drawTitle(ctx, mouseX, mouseY, partialTicks);

        int x = 12;
        int y = this.height - 60;
        int lineHeight = this.fontHeight + 2;

        this.drawString(ctx, "使用说明:", x, y, 0xFFFFA0);
        y += lineHeight;
        this.drawString(ctx, "H 键 - 开始/停止扫描", x + 8, y, 0xC0C0C0);
        y += lineHeight;
        this.drawString(ctx, "K 键 - 对准容器添加/移除", x + 8, y, 0xC0C0C0);
        y += lineHeight;
        this.drawString(ctx, "多人模式需先打开过箱子", x + 8, y, 0x808080);
    }
}
