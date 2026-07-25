package com.litestock.mixin;

import com.litestock.scan.ContainerProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleTagQueryPacket", at = @At("HEAD"))
    private void litestock_onHandleTagQuery(ClientboundTagQueryPacket packet, CallbackInfo ci) {
        ContainerProbe.getInstance().onTagQueryResponse(packet);
    }
}
