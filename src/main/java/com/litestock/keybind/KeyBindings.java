package com.litestock.keybind;

import com.litestock.LiteStock;
import com.litestock.command.LiteStockCommands;
import com.litestock.config.LiteStockConfig;
import com.litestock.scan.SelectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(LiteStock.MOD_ID, "key.categories.litestock")
    );

    public static final KeyMapping SCAN_TOGGLE = new KeyMapping(
            "key.litestock.scan_toggle",
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    public static final KeyMapping SELECT_AREA = new KeyMapping(
            "key.litestock.select_area",
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    public static final KeyMapping CLEAR_SELECTION = new KeyMapping(
            "key.litestock.clear_selection",
            GLFW.GLFW_KEY_L,
            CATEGORY
    );

    public static void init() {
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (SCAN_TOGGLE.consumeClick()) {
            LiteStockCommands.triggerScan();
        }

        if (SELECT_AREA.consumeClick()) {
            handleSelectArea(mc);
        }

        if (CLEAR_SELECTION.consumeClick()) {
            SelectionManager.getInstance().clear();
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 已清除选区").withStyle(ChatFormatting.GOLD));
        }
    }

    private static void handleSelectArea(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 请对准一个方块").withStyle(ChatFormatting.RED));
            return;
        }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();

        if (mc.level == null) return;

        SelectionManager sel = SelectionManager.getInstance();

        if (!sel.hasPos1()) {
            sel.setPos1(pos.immutable());
            mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 选区第一点: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
            ).withStyle(ChatFormatting.GREEN).append(
                Component.literal(" （移动准心到第二点再按 K）").withStyle(ChatFormatting.GRAY)
            ));
        } else {
            sel.setPos2(pos.immutable());
            int count = addSelectionContainers(mc);
            mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 选区完成，已添加 " + count + " 个容器"
            ).withStyle(ChatFormatting.GREEN));
            sel.clear();
        }
    }

    private static int addSelectionContainers(Minecraft mc) {
        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasBoth() || mc.level == null) return 0;

        BlockPos min = sel.getMin();
        BlockPos max = sel.getMax();
        LiteStockConfig config = LiteStockConfig.get();
        int count = 0;

        int minCX = min.getX() >> 4;
        int maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4;
        int maxCZ = max.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                var chunk = mc.level.getChunk(cx, cz);
                for (var be : chunk.getBlockEntities().values()) {
                    BlockPos bePos = be.getBlockPos();
                    if (!sel.contains(bePos)) continue;
                    if (!(be instanceof net.minecraft.world.Container)) continue;
                    if (config.isContainerSelected(bePos)) continue;
                    config.toggleContainerSelected(bePos);
                    count++;
                }
            }
        }

        if (count > 0) {
            LiteStockConfig.save();
        }

        return count;
    }
}
