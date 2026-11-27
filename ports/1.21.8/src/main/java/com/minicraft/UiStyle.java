package com.minicraft;

import net.minecraft.client.gui.GuiGraphics;

/** Shared black-and-white widget drawing for vanilla menus. */
public final class UiStyle {
	public static void box(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hot) {
		int line = !active ? 0x40FFFFFF : hot ? 0xFFFFFFFF : 0x80D0D0D0;
		int fill = !active ? 0x60000000 : hot ? 0x38FFFFFF : 0x14FFFFFF;
		int r = Math.max(2, Math.round(h / 7f));
		GlassButton.roundRect(g, x, y, w, h, r, line);
		GlassButton.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), 0xE00C0C0E);
		GlassButton.roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), fill);
	}

	public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int c) {
		if (w <= 0 || h <= 0) return;
		GlassButton.roundRect(g, x, y, w, h, Math.max(1, Math.min(Math.min(w, h) / 2, Math.round(h / 7f))), c);
	}
}
