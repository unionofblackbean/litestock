package com.litestock.scan;

import net.minecraft.core.BlockPos;

public class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos pos1;
    private BlockPos pos2;

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    private SelectionManager() {}

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public boolean hasPos1() {
        return pos1 != null;
    }

    public boolean hasBoth() {
        return pos1 != null && pos2 != null;
    }

    public void setPos1(BlockPos pos) {
        this.pos1 = pos;
        this.pos2 = null;
    }

    public void setPos2(BlockPos pos) {
        this.pos2 = pos;
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
    }

    public BlockPos getMin() {
        if (pos1 == null) return null;
        BlockPos p2 = pos2 != null ? pos2 : pos1;
        return new BlockPos(
            Math.min(pos1.getX(), p2.getX()),
            Math.min(pos1.getY(), p2.getY()),
            Math.min(pos1.getZ(), p2.getZ())
        );
    }

    public BlockPos getMax() {
        if (pos1 == null) return null;
        BlockPos p2 = pos2 != null ? pos2 : pos1;
        return new BlockPos(
            Math.max(pos1.getX(), p2.getX()),
            Math.max(pos1.getY(), p2.getY()),
            Math.max(pos1.getZ(), p2.getZ())
        );
    }

    public boolean contains(BlockPos pos) {
        BlockPos min = getMin();
        BlockPos max = getMax();
        if (min == null || max == null) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}
