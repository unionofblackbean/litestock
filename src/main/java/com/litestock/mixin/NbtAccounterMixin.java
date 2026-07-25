package com.litestock.mixin;

import net.minecraft.nbt.NbtAccounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NbtAccounter.class)
public class NbtAccounterMixin {

    @Inject(method = "accountBytes(J)V", at = @At("HEAD"), cancellable = true)
    private void litestock_skipQuotaCheck(long bytes, CallbackInfo ci) {
        ci.cancel();
    }
}
