package com.yutadigital.herobrine.mixin.client;

import com.yutadigital.herobrine.client.HerobrineClient;
import com.yutadigital.herobrine.core.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Matches acknowledged server mining speed so the client sends completion at the right time. */
@Mixin(Player.class)
public abstract class PlayerMiningMixin {
    @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", at = @At("RETURN"), cancellable = true)
    private void herobrineultimate$miningPrediction(BlockState block, CallbackInfoReturnable<Float> callback) {
        if ((Object) this == Minecraft.getInstance().player && HerobrineClient.allowed()
                && HerobrineClient.settings().enabled(Ability.MOTION)) {
            callback.setReturnValue((float) Math.min(100000.0,
                    callback.getReturnValueF() * HerobrineClient.settings().multiplier("mine")));
        }
    }
}
