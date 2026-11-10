package com.minicraft.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus opened from the title screen get a plain black background instead of the panorama. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true, require = 0)
	private void mini$black(GuiGraphics g, float delta, CallbackInfo ci) {
		if (!com.minicraft.Config.INSTANCE.titleStyle) return;
		ci.cancel();
		g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000, 0xFF101012);
	}

	@Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void mini$noBlur(GuiGraphics g, CallbackInfo ci) {
		if (!com.minicraft.Config.INSTANCE.titleStyle) return;
		if (net.minecraft.client.Minecraft.getInstance().level == null) ci.cancel();
	}
}
