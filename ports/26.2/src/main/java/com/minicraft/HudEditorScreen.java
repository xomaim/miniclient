package com.minicraft;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
	private final Screen parent;
	private boolean dragging = false;
	private int offX = 0;
	private int offY = 0;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Move the bar"));
		this.parent = parent;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		Config c = Config.INSTANCE;
		int w = HudRenderer.width(this.minecraft);
		int h = HudRenderer.height(this.minecraft);
		g.fill(c.x - 1, c.y - 1, c.x + w + 1, c.y + h + 1, dragging ? 0x80EBEBEB : 0x60FFFFFF);
		HudRenderer.draw(g, this.minecraft);
		g.centeredText(this.font, "Drag the bar where you want it. Press Esc when done.", this.width / 2, this.height / 2, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x(), mouseY = event.y();
		int button = event.button();
		Config c = Config.INSTANCE;
		int w = HudRenderer.width(this.minecraft);
		int h = HudRenderer.height(this.minecraft);
		if (button == 0 && mouseX >= c.x && mouseX <= c.x + w && mouseY >= c.y && mouseY <= c.y + h) {
			dragging = true;
			offX = (int) mouseX - c.x;
			offY = (int) mouseY - c.y;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
		double mouseX = event.x(), mouseY = event.y();
		if (dragging) {
			Config c = Config.INSTANCE;
			int w = HudRenderer.width(this.minecraft);
			int h = HudRenderer.height(this.minecraft);
			c.x = Math.max(0, Math.min(this.width - w, (int) mouseX - offX));
			c.y = Math.max(0, Math.min(this.height - h, (int) mouseY - offY));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			Config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(parent);
	}
}
