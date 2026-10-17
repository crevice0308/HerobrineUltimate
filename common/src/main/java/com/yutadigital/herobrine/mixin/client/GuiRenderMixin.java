package com.yutadigital.herobrine.mixin.client;

import com.yutadigital.herobrine.client.HerobrineClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One short, optional transformation flash; it does not alter world lighting. */
@Mixin(Gui.class)
public abstract class GuiRenderMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void herobrineultimate$flash(GuiGraphics graphics, float partialTick, CallbackInfo callback) {
        int alpha = HerobrineClient.flashAlpha();
        if (alpha > 0) {
            var window = Minecraft.getInstance().getWindow();
            graphics.fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                    alpha << 24 | 0xD1F8FF);
        }
    }
}
