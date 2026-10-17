package com.yutadigital.herobrine.mixin.client;

import com.yutadigital.herobrine.client.HerobrineClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void herobrineultimate$name(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
        if (HerobrineClient.transformed(info.getProfile().getId())) callback.setReturnValue(Component.literal("Herobrine"));
    }
}
