package com.minicraft;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base for the Mini Client pages opened from the title screen: black background, back button, big title. */
public abstract class MiniPage extends Screen {
	protected final Screen parent;
	protected int left, top, pw;

	protected MiniPage(Screen parent, String title) {
		super(Component.literal(title));
		this.parent = parent;
	}

	@Override
	protected final void init() {
		pw = Math.min(this.width - 40, 480);
		left = (this.width - pw) / 2;
		top = 44;
		GlassButton back = new GlassButton(12, 10, 64, 20, "←  Back", this::onClose);
		addRenderableWidget(back);
		build();
	}

	protected abstract void build();

	protected GlassButton button(int x, int y, int w, int h, String label, Runnable r) {
		return addRenderableWidget(new GlassButton(x, y, w, h, label, r));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xFF000000, 0xFF0E0E10);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		big(g, this.title.getString(), left, 16, 1.6f, 0xFFFFFFFF);
	}

	protected void big(GuiGraphics g, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate((float) x, (float) y);
		g.pose().scale(scale, scale);
		g.drawString(this.font, s, 0, 0, color, false);
		g.pose().popMatrix();
	}

	protected void card(GuiGraphics g, int x, int y, int w, int h, boolean hot) {
		UiStyle.box(g, x, y, w, h, true, hot);
	}

	protected String fit(String s, int w) {
		if (s == null) return "";
		if (this.font.width(s) <= w) return s;
		return this.font.plainSubstrByWidth(s, Math.max(0, w - this.font.width("..."))) + "...";
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
