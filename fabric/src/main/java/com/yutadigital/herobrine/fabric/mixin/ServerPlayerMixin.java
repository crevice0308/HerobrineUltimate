package com.yutadigital.herobrine.fabric.mixin;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void herobrineultimate$preventDamage(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> callback) {
        if (HerobrineServer.preventDamage((ServerPlayer) (Object) this, source)) {
            callback.setReturnValue(false);
        }
    }
}
