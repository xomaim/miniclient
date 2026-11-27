package com.minicraft;

import net.minecraft.client.gui.GuiGraphics;
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
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		Config c = Config.INSTANCE;
		int w = HudRenderer.width(this.minecraft);
		int h = HudRenderer.height(this.minecraft);
		g.fill(c.x - 1, c.y - 1, c.x + w + 1, c.y + h + 1, dragging ? 0x80EBEBEB : 0x60FFFFFF);
		HudRenderer.draw(g, this.minecraft);
		g.drawCenteredString(this.font, "Drag the bar where you want it. Press Esc when done.", this.width / 2, this.height / 2, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Config c = Config.INSTANCE;
		int w = HudRenderer.width(this.minecraft);
		int h = HudRenderer.height(this.minecraft);
		if (button == 0 && mouseX >= c.x && mouseX <= c.x + w && mouseY >= c.y && mouseY <= c.y + h) {
			dragging = true;
			offX = (int) mouseX - c.x;
			offY = (int) mouseY - c.y;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dragging) {
			Config c = Config.INSTANCE;
			int w = HudRenderer.width(this.minecraft);
			int h = HudRenderer.height(this.minecraft);
			c.x = Math.max(0, Math.min(this.width - w, (int) mouseX - offX));
			c.y = Math.max(0, Math.min(this.height - h, (int) mouseY - offY));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			Config.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.setScreen(parent);
	}
}
