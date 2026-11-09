package com.minicraft;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class MiniSettingsScreen extends Screen {
	private static final int[] SIZES = {70, 85, 100, 125, 150};
	private final Screen parent;

	public MiniSettingsScreen(Screen parent) {
		super(Component.literal("Mini Client"));
		this.parent = parent;
	}

	private static Component label(String name, boolean on) {
		return Component.literal(name + ": " + (on ? "ON" : "OFF"));
	}

	private Button toggle(int x, int y, String name, BooleanSupplier get, Consumer<Boolean> set) {
		return Button.builder(label(name, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			Config.save();
			b.setMessage(label(name, get.getAsBoolean()));
		}).bounds(x, y, 150, 20).build();
	}

	@Override
	protected void init() {
		Config c = Config.INSTANCE;
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int y = this.height / 6 + 10;
		int step = 24;

		addRenderableWidget(toggle(left, y, "FPS", () -> c.showFps, v -> c.showFps = v));
		addRenderableWidget(toggle(right, y, "CPS", () -> c.showCps, v -> c.showCps = v));
		y += step;
		addRenderableWidget(toggle(left, y, "Ping", () -> c.showPing, v -> c.showPing = v));
		addRenderableWidget(toggle(right, y, "Coordinates", () -> c.showCoords, v -> c.showCoords = v));
		y += step;
		addRenderableWidget(toggle(left, y, "Clock", () -> c.showTime, v -> c.showTime = v));
		addRenderableWidget(Button.builder(Component.literal("Background: " + (c.bg == 0 ? "Transparent" : "Dark")), b -> {
			c.bg = c.bg == 0 ? 1 : 0;
			Config.save();
			b.setMessage(Component.literal("Background: " + (c.bg == 0 ? "Transparent" : "Dark")));
		}).bounds(right, y, 150, 20).build());
		y += step;
		addRenderableWidget(Button.builder(Component.literal("Size: " + c.scale + "%"), b -> {
			int next = SIZES[0];
			for (int i = 0; i < SIZES.length; i++) {
				if (SIZES[i] == c.scale) {
					next = SIZES[(i + 1) % SIZES.length];
					break;
				}
			}
			c.scale = next;
			Config.save();
			b.setMessage(Component.literal("Size: " + c.scale + "%"));
		}).bounds(left, y, 150, 20).build());
		addRenderableWidget(toggle(right, y, "Menu style", () -> c.titleStyle, v -> c.titleStyle = v));
		y += step + 6;
		addRenderableWidget(Button.builder(Component.literal("Move the bar"), b -> this.minecraft.setScreen(new HudEditorScreen(this)))
			.bounds(left, y, 310, 20).build());
		y += step + 6;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width / 2 - 100, y, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawCenteredString(this.font, "Mini Client", this.width / 2, this.height / 6 - 14, 0xFFFFFFFF);
		g.drawCenteredString(this.font, "Menu style needs a restart to change the title screen", this.width / 2, this.height - 20, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.setScreen(parent);
	}
}
