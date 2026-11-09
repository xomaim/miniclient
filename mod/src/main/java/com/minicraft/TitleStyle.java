package com.minicraft;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class TitleStyle {
	public static void apply(Minecraft mc, Screen screen, int w, int h) {
		List<AbstractWidget> list = Screens.getButtons(screen);
		for (AbstractWidget wd : new ArrayList<>(list)) {
			if (wd instanceof PlainTextButton) continue;   // keep Mojang's copyright line
			wd.visible = false;
			wd.active = false;
		}

		int cx = w / 2;
		int y = h / 2 - 14;
		int step = 24;
		list.add(Button.builder(Component.literal("Singleplayer"), b -> mc.setScreen(new SelectWorldScreen(screen))).bounds(cx - 100, y, 200, 20).build());
		y += step;
		list.add(Button.builder(Component.literal("Multiplayer"), b -> mc.setScreen(new JoinMultiplayerScreen(screen))).bounds(cx - 100, y, 200, 20).build());
		y += step;
		list.add(Button.builder(Component.literal("Realms"), b -> mc.setScreen(new RealmsMainScreen(screen))).bounds(cx - 100, y, 200, 20).build());
		y += step;
		list.add(Button.builder(Component.literal("Settings"), b -> mc.setScreen(new OptionsScreen(screen, mc.options))).bounds(cx - 100, y, 98, 20).build());
		list.add(Button.builder(Component.literal("Mini Client"), b -> mc.setScreen(new MiniSettingsScreen(screen))).bounds(cx + 2, y, 98, 20).build());
		y += step;
		list.add(Button.builder(Component.literal("Quit Game"), b -> mc.stop()).bounds(cx - 100, y, 200, 20).build());

		// recent servers on the left; they open the Multiplayer screen
		try {
			ServerList servers = new ServerList(mc);
			servers.load();
			int sy = h / 2 - 14;
			for (int i = 0; i < Math.min(3, servers.size()); i++) {
				ServerData sd = servers.get(i);
				list.add(Button.builder(Component.literal(sd.name), b -> mc.setScreen(new JoinMultiplayerScreen(screen))).bounds(10, sy, 130, 20).build());
				sy += step;
			}
		} catch (Exception ignored) {
		}

		ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
			g.drawString(mc.font, "Quick play", 10, h / 2 - 28, 0xFFFFC0D8, true);
			String who = "Playing as " + mc.getUser().getName();
			g.drawString(mc.font, who, w - mc.font.width(who) - 10, h - 20, 0xFFFFFFFF, true);
		});
	}
}
