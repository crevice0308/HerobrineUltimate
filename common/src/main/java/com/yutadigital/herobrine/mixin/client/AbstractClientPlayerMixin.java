package com.yutadigital.herobrine.mixin.client;

import com.yutadigital.herobrine.client.HerobrineClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void herobrineultimate$skin(CallbackInfoReturnable<ResourceLocation> callback) {
        if (HerobrineClient.transformed(((AbstractClientPlayer) (Object) this).getUUID())) {
            callback.setReturnValue(HerobrineClient.SKIN);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void herobrineultimate$model(CallbackInfoReturnable<String> callback) {
        if (HerobrineClient.transformed(((AbstractClientPlayer) (Object) this).getUUID())) {
            callback.setReturnValue("default");
        }
    }
}
