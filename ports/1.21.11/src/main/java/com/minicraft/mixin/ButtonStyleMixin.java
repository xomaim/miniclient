package com.minicraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla button (Options, Video Settings, ...) drawn in the Mini Client black-and-white style. */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin extends AbstractWidget {
	public ButtonStyleMixin(int x, int y, int w, int h, Component c) { super(x, y, w, h, c); }

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void mini$draw(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!com.minicraft.Config.INSTANCE.titleStyle) return;
		ci.cancel();
		Minecraft mc = Minecraft.getInstance();
		com.minicraft.UiStyle.box(g, getX(), getY(), getWidth(), getHeight(), this.active, this.isHoveredOrFocused());
		int col = this.active ? 0xFFF4F4F4 : 0xFF707070;
		this.renderScrollingString(g, mc.font, 4, col | (Math.round(this.alpha * 255) << 24));
	}
}
