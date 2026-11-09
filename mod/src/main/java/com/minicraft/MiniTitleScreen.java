package com.minicraft;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.Random;

/** The Minicraft title screen: dark blossom-night background, falling petals, centered logo and outlined buttons. */
public class MiniTitleScreen extends Screen {
	private static final int PETALS = 46;
	private final float[] px = new float[PETALS];
	private final float[] py = new float[PETALS];
	private final float[] ps = new float[PETALS];
	private final int[] pc = new int[PETALS];

	public MiniTitleScreen() {
		super(Component.literal("Minicraft"));
		Random r = new Random(7);
		for (int i = 0; i < PETALS; i++) {
			px[i] = r.nextFloat();
			py[i] = r.nextFloat();
			ps[i] = 0.012f + r.nextFloat() * 0.03f;
			pc[i] = (i % 3 == 0) ? 0xB0FFFFFF : ((i % 3 == 1) ? 0xB0FFB3D0 : 0xB0FF4F9A);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		Minecraft mc = this.minecraft;
		int cx = this.width / 2;
		int y = this.height / 2 - 6;
		int step = 26;
		addRenderableWidget(new GlassButton(cx - 100, y, 200, 22, "Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this))));
		y += step;
		addRenderableWidget(new GlassButton(cx - 100, y, 200, 22, "Multiplayer", () -> mc.setScreen(new JoinMultiplayerScreen(this))));
		y += step;
		addRenderableWidget(new GlassButton(cx - 100, y, 200, 22, "Realms", () -> mc.setScreen(new RealmsMainScreen(this))));
		y += step;
		addRenderableWidget(new GlassButton(cx - 100, y, 98, 22, "Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
		addRenderableWidget(new GlassButton(cx + 2, y, 98, 22, "Mini Client", () -> mc.setScreen(new MiniSettingsScreen(this))));
		y += step + 8;
		addRenderableWidget(new GlassButton(cx - 100, y, 200, 20, "Quit Game", () -> mc.stop()));

		// quick play: your saved servers first, then popular ones, click to join
		try {
			ServerList servers = new ServerList(mc);
			servers.load();
			java.util.List<ServerData> shown = new java.util.ArrayList<>();
			for (int i = 0; i < servers.size() && shown.size() < 4; i++) shown.add(servers.get(i));
			String[][] defaults = {{"Hypixel", "mc.hypixel.net"}, {"Wynncraft", "play.wynncraft.com"}, {"CubeCraft", "play.cubecraft.net"}, {"2b2t", "2b2t.org"}};
			for (String[] d : defaults) {
				if (shown.size() >= 4) break;
				boolean have = false;
				for (ServerData sd : shown) if (sd.ip.equalsIgnoreCase(d[1])) have = true;
				if (!have) shown.add(new ServerData(d[0], d[1], ServerData.Type.OTHER));
			}
			int sy = this.height / 2 - 6;
			for (ServerData sd : shown) {
				final ServerData target = sd;
				addRenderableWidget(new GlassButton(14, sy, 160, 34, sd.name, sd.ip, () ->
					ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(target.ip), target, false, null)));
				sy += 38;
			}
		} catch (Exception ignored) {
		}

		// right side: your account and the settings
		int rx = this.width - 164;
		addRenderableWidget(new GlassButton(rx, this.height / 2 - 6, 150, 34, mc.getUser().getName(), "Playing as", () -> { }));
		addRenderableWidget(new GlassButton(rx, this.height / 2 + 32, 150, 22, "Mini Client settings", () -> mc.setScreen(new MiniSettingsScreen(this))));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xFF050509, 0xFF120A14);
		g.fillGradient(0, this.height / 2, this.width, this.height, 0x00FF4F9A, 0x38FF4F9A);
		long t = System.currentTimeMillis();
		for (int i = 0; i < PETALS; i++) {
			float yy = (py[i] + (t % 1000000L) / 1000f * ps[i]) % 1f;
			float xx = (px[i] + (float) Math.sin((t / 1000.0) * 0.6 + i) * 0.015f) % 1f;
			int x = (int) (xx * this.width);
			int y = (int) (yy * this.height);
			int s = (i % 4 == 0) ? 3 : 2;
			g.fill(x, y, x + s, y + s, pc[i]);
		}
	}

	private void star(GuiGraphics g, int cx, int cy, int r, int color) {
		double rr = Math.pow(r, 2.0 / 3.0);
		for (int dy = -r; dy <= r; dy++) {
			double inner = rr - Math.pow(Math.abs(dy), 2.0 / 3.0);
			if (inner <= 0) continue;
			int hw = (int) Math.round(Math.pow(inner, 1.5));
			g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int cx = this.width / 2;
		int top = this.height / 2 - 100;
		star(g, cx, top + 22, 20, 0xFFFFFFFF);
		star(g, cx, top + 22, 9, 0xFFFF4F9A);

		float s = 3.0f;
		String a = "MINI";
		String b = "CRAFT";
		int wa = this.font.width(a);
		int wb = this.font.width(b);
		float total = (wa + wb) * s;
		g.pose().pushMatrix();
		g.pose().translate(cx - total / 2f, (float) (top + 52));
		g.pose().scale(s, s);
		g.drawString(this.font, a, 0, 0, 0xFFFFFFFF, true);
		g.drawString(this.font, b, wa, 0, 0xFFFF4F9A, true);
		g.pose().popMatrix();

		g.drawString(this.font, "Quick play", 14, this.height / 2 - 20, 0xFFFF9AC4, true);
		g.drawString(this.font, "Account", this.width - 164, this.height / 2 - 20, 0xFFFF9AC4, true);
		String v = "Minicraft 1.21.7";
		g.drawString(this.font, v, this.width - this.font.width(v) - 8, this.height - 14, 0xFF8D8BA0, false);
	}
}
