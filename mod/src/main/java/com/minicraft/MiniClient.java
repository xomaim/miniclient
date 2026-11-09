package com.minicraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MiniClient implements ClientModInitializer {
	private int tick = 0;

	@Override
	public void onInitializeClient() {
		Config.load();

		HudRenderCallback.EVENT.register((g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			HudRenderer.poll(mc);
			if (mc.options.hideGui) return;
			if (mc.screen instanceof HudEditorScreen) return;
			HudRenderer.draw(g, mc);
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			tick++;
			if (tick % 20 == 0) {
				GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(), "Minicraft 1.21.7");
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				Screens.getButtons(screen).add(
					Button.builder(Component.literal("Mini Client"), b -> client.setScreen(new MiniSettingsScreen(screen)))
						.bounds(6, 6, 90, 20).build());
			}
			if (screen instanceof TitleScreen && Config.INSTANCE.titleStyle) {
				TitleStyle.apply(client, screen, w, h);
			}
		});
	}
}
