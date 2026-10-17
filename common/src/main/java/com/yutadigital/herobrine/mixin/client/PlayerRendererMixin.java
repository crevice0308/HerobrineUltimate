package com.yutadigital.herobrine.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yutadigital.herobrine.client.HerobrineClient;
import com.yutadigital.herobrine.client.HerobrineEyesLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    protected PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void herobrineultimate$eyes(EntityRendererProvider.Context context, boolean slim, CallbackInfo callback) {
        addLayer(new HerobrineEyesLayer(this));
    }

    @ModifyVariable(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component herobrineultimate$name(Component original, AbstractClientPlayer player,
                                             Component name, PoseStack pose, MultiBufferSource buffers, int light) {
        return HerobrineClient.transformed(player.getUUID()) ? Component.literal("Herobrine") : original;
    }
}
