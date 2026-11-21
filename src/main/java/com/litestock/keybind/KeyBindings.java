package com.litestock.keybind;

import com.litestock.LiteStock;
import com.litestock.config.LiteStockConfig;
import com.litestock.scan.SelectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class KeyBindings {
    public static boolean handleSelectArea(Minecraft mc, String keyDisplay) {
        if (mc.level == null || mc.player == null) {
            LiteStock.debugLog("Selection ignored: no active client level or player");
            return false;
        }

        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            LiteStock.debugLog("Selection ignored: target={}", mc.hitResult == null ? "none" : mc.hitResult.getType());
            mc.player.sendSystemMessage(Component.literal("[LiteStock] 请对准一个方块").withStyle(ChatFormatting.RED));
            return false;
        }

        BlockPos pos = hit.getBlockPos();
        SelectionManager sel = SelectionManager.getInstance();

        if (!sel.hasPos1()) {
            sel.setPos1(pos.immutable());
            LiteStock.debugLog("Selection first point set (pos={}, dimension={})", pos, mc.level.dimension().identifier());
            mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 选区第一点: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
            ).withStyle(ChatFormatting.GREEN).append(
                Component.literal(" （移动准心到第二点再按 " + keyDisplay + "）").withStyle(ChatFormatting.GRAY)
            ));
        } else {
            sel.setPos2(pos.immutable());
            int count = addSelectionContainers(mc);
            mc.player.sendSystemMessage(Component.literal(
                "[LiteStock] 选区完成，已添加 " + count + " 个容器"
            ).withStyle(ChatFormatting.GREEN));
            sel.clear();
        }
        return true;
    }

    private static int addSelectionContainers(Minecraft mc) {
        SelectionManager sel = SelectionManager.getInstance();
        if (!sel.hasBoth() || mc.level == null) return 0;

        BlockPos min = sel.getMin();
        BlockPos max = sel.getMax();
        LiteStockConfig config = LiteStockConfig.get();
        int count = 0;
        int unloadedChunks = 0;

        int minCX = min.getX() >> 4;
        int maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4;
        int maxCZ = max.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) {
                    unloadedChunks++;
                    continue;
                }
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

        LiteStock.debugLog("Selection complete (min={}, max={}, addedContainers={}, unloadedChunks={})",
                min, max, count, unloadedChunks);
        return count;
    }
}
