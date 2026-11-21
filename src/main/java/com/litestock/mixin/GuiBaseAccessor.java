package com.litestock.mixin;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = GuiBase.class, remap = false)
public interface GuiBaseAccessor {
    @Accessor("widgets")
    List<WidgetBase> litestock_widgets();
}
