package com.yutadigital.herobrine.fabric.mixin;

import com.yutadigital.herobrine.fabric.ClientSmokeBootstrap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only framebuffer callback after the real game/screen has rendered. */
@Mixin(Minecraft.class)
public abstract class ClientRenderTestMixin {
    @Inject(method = "runTick(Z)V", at = @At("TAIL"))
    private void herobrineultimate$testRender(boolean renderLevel, CallbackInfo callback) {
        ClientSmokeBootstrap.renderFinished();
    }
}
