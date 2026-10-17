package com.yutadigital.herobrine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Original transparent eye texture, emissive at full brightness without shaders. */
public final class HerobrineEyesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation("herobrineultimate", "textures/entity/herobrine_eyes.png"));

    public HerobrineEyesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                                 float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                 float netHeadYaw, float headPitch) {
        if (HerobrineClient.transformed(player.getUUID()) && !player.isInvisible()) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(EYES), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }
}
