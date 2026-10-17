package com.yutadigital.herobrine.mixin;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Decorates presentation only: profile identity and signed message contents stay intact. */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void herobrineultimate$displayName(CallbackInfoReturnable<Component> callback) {
        if ((Object) this instanceof ServerPlayer player && HerobrineServer.transformed(player.getUUID()))
            callback.setReturnValue(Component.literal("Herobrine"));
    }
}
