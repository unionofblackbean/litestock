package com.litestock.mixin;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiConfigsBase.class, remap = false)
public abstract class ConfigSwitcherMixin {
    @Inject(method = "onMouseClicked", at = @At("HEAD"), cancellable = true)
    private void litestock_clickConfigSwitcher(MouseButtonEvent click, boolean doubleClick,
                                                CallbackInfoReturnable<Boolean> result) {
        // Masa Gadget can replace MaLiLib's switcher, so only dispatch to widgets added to this screen.
        for (WidgetBase widget : ((GuiBaseAccessor) this).litestock_widgets()) {
            if (widget instanceof WidgetDropDownList<?> dropdown
                    && dropdown.isMouseOver((int) click.x(), (int) click.y())
                    && dropdown.onMouseClicked(click, doubleClick)) {
                result.setReturnValue(true);
                return;
            }
        }
    }
}
