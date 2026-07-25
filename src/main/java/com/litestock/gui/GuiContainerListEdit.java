package com.litestock.gui;

import com.litestock.config.LiteStockConfig;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GuiContainerListEdit extends GuiBase {

    private final GuiBase parent;
    private final List<BlockPos> positions;
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 26;
    private static final int VISIBLE_ROWS = 12;

    public GuiContainerListEdit(GuiBase parent) {
        this.parent = parent;
        this.positions = new ArrayList<>(LiteStockConfig.get().getSelectedContainerPositions());
        this.setTitle("编辑容器列表");
    }

    @Override
    public void initGui() {
        super.initGui();

        int centerX = this.width / 2;
        int listWidth = 380;
        int listX = centerX - listWidth / 2;
        int y = 40;

        int numW = 24;
        int fieldW = 70;
        int btnW = 28;
        int gap = 4;

        int visibleCount = Math.min(positions.size() - scrollOffset, VISIBLE_ROWS);

        for (int i = 0; i < visibleCount; i++) {
            int idx = scrollOffset + i;
            BlockPos pos = positions.get(idx);

            int numX = listX;
            int fx = numX + numW + gap;
            int fy = y + 3;
            int delX = listX + listWidth - btnW;
            int upX = delX - btnW - gap;

            GuiTextFieldGeneric xField = new GuiTextFieldGeneric(fx, fy, fieldW, 18, this.font);
            xField.setValueWrapper(String.valueOf(pos.getX()));
            this.addTextField(xField, new PosTextFieldListener(idx, 0));

            GuiTextFieldGeneric yField = new GuiTextFieldGeneric(fx + fieldW + gap, fy, fieldW, 18, this.font);
            yField.setValueWrapper(String.valueOf(pos.getY()));
            this.addTextField(yField, new PosTextFieldListener(idx, 1));

            GuiTextFieldGeneric zField = new GuiTextFieldGeneric(fx + (fieldW + gap) * 2, fy, fieldW, 18, this.font);
            zField.setValueWrapper(String.valueOf(pos.getZ()));
            this.addTextField(zField, new PosTextFieldListener(idx, 2));

            final int index = idx;
            ButtonGeneric delBtn = new ButtonGeneric(delX, y, btnW, 20, "-");
            delBtn.setActionListener(new IButtonActionListener() {
                @Override
                public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                    positions.remove(index);
                    if (scrollOffset > 0 && scrollOffset >= positions.size()) {
                        scrollOffset = Math.max(0, positions.size() - VISIBLE_ROWS);
                    }
                    saveAndRefresh();
                }
            });
            this.addButton(delBtn, null);

            y += ROW_HEIGHT;
        }

        int addBtnX = listX + listWidth - 80;
        int addBtnY = this.height - 35;
        ButtonGeneric addBtn = new ButtonGeneric(addBtnX, addBtnY, 80, 20, "+ 添加");
        addBtn.setActionListener(new IButtonActionListener() {
            @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                positions.add(new BlockPos(0, 64, 0));
                scrollOffset = Math.max(0, positions.size() - VISIBLE_ROWS);
                saveAndRefresh();
            }
        });
        this.addButton(addBtn, null);

        int backBtnX = listX;
        int backBtnY = this.height - 35;
        ButtonGeneric backBtn = new ButtonGeneric(backBtnX, backBtnY, 80, 20, "返回");
        backBtn.setActionListener(new IButtonActionListener() {
            @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                saveConfig();
                openGui(parent);
            }
        });
        this.addButton(backBtn, null);
    }

    private class PosTextFieldListener implements ITextFieldListener<GuiTextFieldGeneric> {
        private final int index;
        private final int axis;

        PosTextFieldListener(int index, int axis) {
            this.index = index;
            this.axis = axis;
        }

        @Override
        public boolean onTextChange(GuiTextFieldGeneric textField) {
            try {
                int val = Integer.parseInt(textField.getValueWrapper());
                BlockPos old = positions.get(index);
                int x = axis == 0 ? val : old.getX();
                int y = axis == 1 ? val : old.getY();
                int z = axis == 2 ? val : old.getZ();
                positions.set(index, new BlockPos(x, y, z));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
    }

    private void saveAndRefresh() {
        saveConfig();
        clearButtons();
        clearTextFields();
        initGui();
    }

    private void saveConfig() {
        LiteStockConfig config = LiteStockConfig.get();
        config.selectedContainers.clear();
        for (BlockPos pos : positions) {
            String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
            if (!config.selectedContainers.contains(key)) {
                config.selectedContainers.add(key);
            }
        }
        LiteStockConfig.save();
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double amount, double delta) {
        int maxOffset = Math.max(0, positions.size() - VISIBLE_ROWS);
        if (amount < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxOffset);
        } else {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
        clearButtons();
        clearTextFields();
        initGui();
        return true;
    }

    @Override
    public void drawContents(fi.dy.masa.malilib.render.GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        this.drawTitle(ctx, mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;
        int listWidth = 380;
        int listX = centerX - listWidth / 2;

        String title = "容器坐标列表 (" + positions.size() + " 个)";
        this.drawString(ctx, title, listX, 20, 0xFFFFFF);

        String hint = "滚轮滚动  |  X   Y   Z   直接编辑  |  - 删除";
        this.drawString(ctx, hint, listX + listWidth - this.getStringWidth(hint), 20, 0x808080);

        if (positions.isEmpty()) {
            String empty = "列表为空，点击 + 添加 或游戏中用 K 键添加";
            this.drawString(ctx, empty, this.width / 2 - this.getStringWidth(empty) / 2, 120, 0xFFAA00);
        }
    }
}
