package com.yutadigital.herobrine.fabric.mixin;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", at = @At("RETURN"), cancellable = true)
    private void herobrineultimate$miningSpeed(BlockState block,
                                              CallbackInfoReturnable<Float> callback) {
        if ((Object) this instanceof ServerPlayer player) {
            callback.setReturnValue(HerobrineServer.mining(player, callback.getReturnValueF()));
        }
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void herobrineultimate$targetAttacked(Entity target, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player) HerobrineServer.attacked(player, target);
    }
}
