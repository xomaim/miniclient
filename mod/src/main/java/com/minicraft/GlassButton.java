package com.minicraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A dark glass button with a thin outline, like the OneClient menu buttons. */
public class GlassButton extends AbstractWidget {
	private final Runnable action;
	private final String sub;

	public GlassButton(int x, int y, int w, int h, String label, String sub, Runnable action) {
		super(x, y, w, h, Component.literal(label));
		this.sub = sub;
		this.action = action;
	}

	public GlassButton(int x, int y, int w, int h, String label, Runnable action) {
		this(x, y, w, h, label, null, action);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
		Minecraft mc = Minecraft.getInstance();
		boolean hot = this.isHoveredOrFocused();
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();
		int fill = hot ? 0xB0502048 : 0x90140E22;
		int line = hot ? 0xFFFF8FB8 : 0xCCEDE3F5;
		g.fill(x + 1, y, x + w - 1, y + h, fill);
		g.fill(x, y + 1, x + w, y + h - 1, fill);
		g.fill(x + 1, y, x + w - 1, y + 1, line);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, line);
		g.fill(x, y + 1, x + 1, y + h - 1, line);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, line);
		String text = this.getMessage().getString();
		if (sub == null) {
			g.drawCenteredString(mc.font, text, x + w / 2, y + (h - mc.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
		} else {
			g.drawString(mc.font, text, x + 8, y + 6, 0xFFFFFFFF, true);
			g.drawString(mc.font, sub, x + 8, y + 6 + mc.font.lineHeight + 2, 0xFFB9A9C9, false);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		this.defaultButtonNarrationText(out);
	}
}
