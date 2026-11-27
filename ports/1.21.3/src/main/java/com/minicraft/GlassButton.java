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
	public float textScale = 1f;

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
		int fill = hot ? 0x30FFFFFF : 0x14FFFFFF;
		int line = hot ? 0xFFFFFFFF : 0x99D0D0D0;
		int r = Math.max(2, Math.round(h / 7f));
		roundRect(g, x, y, w, h, r, line);
		roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), 0xFF0E0E10);
		roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), fill);
		String text = this.getMessage().getString();
		float s = textScale;
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + h / 2f);
		g.pose().scale(s, s);
		int tw = mc.font.width(text);
		g.drawString(mc.font, text, -tw / 2, -mc.font.lineHeight / 2 + 1, 0xFFF4F4F4, false);
		g.pose().popMatrix();
	}

	static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
		g.fill(x + r, y, x + w - r, y + h, c);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x + inset, y + i, x + r, y + i + 1, c);
			g.fill(x + inset, y + h - i - 1, x + r, y + h - i, c);
			g.fill(x + w - r, y + i, x + w - inset, y + i + 1, c);
			g.fill(x + w - r, y + h - i - 1, x + w - inset, y + h - i, c);
		}
		g.fill(x, y + r, x + r, y + h - r, c);
		g.fill(x + w - r, y + r, x + w, y + h - r, c);
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
