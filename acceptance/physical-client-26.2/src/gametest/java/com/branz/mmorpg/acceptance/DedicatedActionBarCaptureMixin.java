package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges the dedicated vanilla action-bar packet into the acceptance message capture stream. */
@Mixin(ClientPacketListener.class)
abstract class DedicatedActionBarCaptureMixin {
    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void branz$captureDedicatedActionBar(
            ClientboundSetActionBarTextPacket packet, CallbackInfo callbackInfo) {
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(packet.text(), true);
    }
}
