package com.minicraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla sliders (FOV, volume, ...): dark track, white fill, round white handle. */
@Mixin(AbstractSliderButton.class)
public abstract class SliderStyleMixin extends AbstractWidget {
	@Shadow protected double value;
	public SliderStyleMixin(int x, int y, int w, int h, Component c) { super(x, y, w, h, c); }

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void mini$draw(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!com.minicraft.Config.INSTANCE.titleStyle) return;
		ci.cancel();
		Minecraft mc = Minecraft.getInstance();
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		com.minicraft.UiStyle.box(g, x, y, w, h, this.active, this.isHoveredOrFocused());
		int fx = x + 1 + (int) Math.round(this.value * (w - 2));
		com.minicraft.UiStyle.fillRound(g, x + 1, y + 1, Math.max(0, fx - x - 1), h - 2, 0x22FFFFFF);
		int hx = Math.max(x + 2, Math.min(x + w - 6, fx - 2));
		com.minicraft.UiStyle.fillRound(g, hx, y + 3, 4, h - 6, 0xFFFFFFFF);
		this.renderScrollingString(g, mc.font, 4, (this.active ? 0xFFF4F4F4 : 0xFF707070) | (Math.round(this.alpha * 255) << 24));
	}
}
