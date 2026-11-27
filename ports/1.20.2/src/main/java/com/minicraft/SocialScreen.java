package com.minicraft;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Mini Client socials. Fill in a link to turn a card on. */
public class SocialScreen extends MiniPage {
	// { name, letter, link } - leave the link empty until the account exists
	public static final String[][] SOCIALS = {
		{ "Discord", "D", "" },
		{ "YouTube", "Y", "" },
		{ "TikTok", "T", "" },
		{ "X", "X", "" },
		{ "Instagram", "I", "" },
		{ "GitHub", "G", "https://github.com/xomaim/miniclient" },
	};

	public SocialScreen(Screen parent) {
		super(parent, "Socials");
	}

	private int cw() { return (pw - 10) / 2; }
	private int cx(int i) { return left + (i % 2) * (cw() + 10); }
	private int cy(int i) { return top + 14 + (i / 2) * 50; }

	@Override
	protected void build() {
		for (int i = 0; i < SOCIALS.length; i++) {
			final String link = SOCIALS[i][2];
			if (link.isEmpty()) continue;
			button(cx(i) + cw() - 58, cy(i) + 11, 48, 18, "Open", () -> Util.getPlatform().openUri(link));
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawString(this.font, "Follow Mini Client for updates and news.", left, top, 0xFF9A9A9A, false);
		for (int i = 0; i < SOCIALS.length; i++) {
			int x = cx(i), y = cy(i), w = cw();
			boolean on = !SOCIALS[i][2].isEmpty();
			card(g, x, y, w, 40, false);
			GlassButton.roundRect(g, x + 8, y + 8, 24, 24, 5, on ? 0xFFFFFFFF : 0xFF26262B);
			g.drawCenteredString(this.font, SOCIALS[i][1], x + 20, y + 16, on ? 0xFF000000 : 0xFF8A8A8A);
			g.drawString(this.font, SOCIALS[i][0], x + 40, y + 10, 0xFFFFFFFF, false);
			g.drawString(this.font, on ? fit(SOCIALS[i][2].replace("https://", ""), w - 110) : "Not added yet", x + 40, y + 22, 0xFF8A8A8A, false);
		}
	}
}
