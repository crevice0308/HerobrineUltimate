package com.yutadigital.herobrine.fabric.mixin;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
    private void herobrineultimate$preventFallDamage(float distance, float multiplier, DamageSource source,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayer player && HerobrineServer.noFall(player)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void herobrineultimate$releaseSummon(DamageSource source, CallbackInfo callback) {
        HerobrineServer.entityDied((LivingEntity) (Object) this);
    }
}
