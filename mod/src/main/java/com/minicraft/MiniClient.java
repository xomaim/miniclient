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
	public static Button capeBtn = null;
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
			if (Config.INSTANCE.titleStyle && mc.screen instanceof TitleScreen) {
				capeBtn = null;
				for (net.minecraft.client.gui.components.AbstractWidget wd : Screens.getButtons(mc.screen)) {
					if (wd instanceof Button && wd.getMessage().getString().toLowerCase().contains("cape")) { capeBtn = (Button) wd; break; }
				}
				mc.setScreen(new MiniTitleScreen());
			}
			if (tick % 20 == 0) {
				GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(), "Minicraft 1.21.7");
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				ScreenEvents.beforeRender(screen).register((sc, g, mx, my, dt) -> {
					g.fillGradient(0, 0, sc.width, sc.height, 0xE0070707, 0xE01C1C1C);
					g.fill(0, sc.height / 2 - 70, 3, sc.height / 2 + 70, 0xFFEBEBEB);
					g.drawCenteredString(client.font, "MINICRAFT", sc.width / 2, 14, 0xFFEBEBEB);
				});
				Screens.getButtons(screen).add(
					Button.builder(Component.literal("Mini Client"), b -> client.setScreen(new MiniSettingsScreen(screen)))
						.bounds(6, 6, 90, 20).build());
			}
		});
	}
}
