package com.litestock.mixin;

import fi.dy.masa.malilib.MaLiLibConfigs;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiConfigsBase.class, remap = false)
public abstract class ConfigSwitcherMixin {
    @Shadow
    protected WidgetDropDownList<?> modSwitchWidget;

    @Inject(method = "onMouseClicked", at = @At("HEAD"), cancellable = true)
    private void litestock_clickConfigSwitcher(MouseButtonEvent click, boolean doubleClick,
                                                CallbackInfoReturnable<Boolean> result) {
        // A config tab or button behind the dropdown would otherwise receive the click first.
        if (this.modSwitchWidget != null && MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue()
                && this.modSwitchWidget.isMouseOver((int) click.x(), (int) click.y())
                && this.modSwitchWidget.onMouseClicked(click, doubleClick)) {
            result.setReturnValue(true);
        }
    }
}
