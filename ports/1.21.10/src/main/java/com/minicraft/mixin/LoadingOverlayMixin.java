package com.minicraft.mixin;

import com.minicraft.Logo;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mojang loading screen: black instead of red, and the Mini Client logo flies down into place. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow private long fadeOutStart;
	@Shadow private long fadeInStart;
	@Shadow @Final private boolean fadeIn;
	@Unique private long miniStart = -1L;

	@Inject(method = "render", at = @At("HEAD"))
	private void mini$black(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		try { Minecraft.getInstance().options.darkMojangStudiosBackground().set(true); } catch (Throwable ignored) {}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void mini$logo(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		long now = Util.getMillis();
		if (miniStart < 0) miniStart = now;
		float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
		float in = fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;
		float alpha = out >= 1f ? 1f - Mth.clamp(out - 1f, 0f, 1f) : (fadeIn ? Mth.clamp(in, 0f, 1f) : 1f);
		if (alpha <= 0.01f) return;
		int w = g.guiWidth(), h = g.guiHeight();
		int cx = (int) (w * 0.5), cy = (int) (h * 0.5);
		double d = Math.min(w * 0.75, h) * 0.25;
		int q = (int) (d * 0.5), r = (int) (d * 4.0 * 0.5);
		int a8 = (int) (alpha * 255) << 24;
		g.fill(cx - r - 2, cy - q - 4, cx + r + 2, cy + q + 4, a8);   // hide the Mojang Studios logo

		float t = Mth.clamp((now - miniStart) / 1100f, 0f, 1f);
		float c1 = 1.70158f, c3 = c1 + 1f;                              // ease-out-back: drops in and settles
		float e = 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
		float size = q * 2.6f;
		float y = Mth.lerp(e, -size, cy);
		Logo.draw(g, cx, y, size, a8 | 0xFFFFFF);
	}
}
