package com.litestock.config;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class PresetArea {
    public final String name;
    public final BlockPos min;
    public final BlockPos max;

    public PresetArea(String name, BlockPos min, BlockPos max) {
        this.name = name;
        this.min = min;
        this.max = max;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public static final List<PresetArea> BUILTIN_PRESETS = new ArrayList<>();

    static {
        BUILTIN_PRESETS.add(new PresetArea("全物品左侧",
                new BlockPos(149, 43, -74),
                new BlockPos(173, 63, -8)));
        BUILTIN_PRESETS.add(new PresetArea("全物品右侧",
                new BlockPos(148, 43, 8),
                new BlockPos(173, 59, 89)));
        BUILTIN_PRESETS.add(new PresetArea("大宗仓库",
                new BlockPos(175, 43, -19),
                new BlockPos(256, 101, 19)));
        BUILTIN_PRESETS.add(new PresetArea("不可堆叠区",
                new BlockPos(130, 43, 8),
                new BlockPos(143, 58, 89)));
    }
}
