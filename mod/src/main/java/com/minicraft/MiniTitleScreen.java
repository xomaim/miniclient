package com.minicraft;

import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
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
			pc[i] = (i % 3 == 0) ? 0xB0FFFFFF : ((i % 3 == 1) ? 0xB0EBEBEB : 0xB0EBEBEB);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private int colX() { return (int) (this.width * 0.28); }

	@Override
	protected void init() {
		Minecraft mc = this.minecraft;
		int cx = colX();
		int bw = 200, y = this.height / 2 - 6, step = 25;
		addRenderableWidget(new GlassButton(cx - bw / 2, y, bw, 20, "\u263A  Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this))));
		addRenderableWidget(new GlassButton(cx - bw / 2, y + step, bw, 20, "\u263B  Multiplayer", () -> mc.setScreen(new JoinMultiplayerScreen(this))));
		addRenderableWidget(new GlassButton(cx - bw / 2, y + step * 2, bw, 20, "\u25CE  Realms", () -> mc.setScreen(new RealmsMainScreen(this))));
		addRenderableWidget(new GlassButton(cx - bw / 2, y + step * 3, bw / 2 - 3, 20, "\u2699  Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
		addRenderableWidget(new GlassButton(cx + 3, y + step * 3, bw / 2 - 3, 20, "\u2630  Mods", () -> mc.setScreen(new MiniSettingsScreen(this))));

		// right column: skin model, account, host world, social, wardrobe
		int rw = 170, rx = this.width - rw - 14;
		try {
			net.minecraft.client.gui.components.PlayerSkinWidget skin = new net.minecraft.client.gui.components.PlayerSkinWidget(
				60, 100, mc.getEntityModels(), () -> mc.getSkinManager().getInsecureSkin(mc.getGameProfile()));
			skin.setPosition(rx + rw / 2 - 30, y - 108);
			addRenderableWidget(skin);
		} catch (Throwable ignored) {
		}
		addRenderableWidget(new GlassButton(rx, y, rw, 20, mc.getUser().getName(), () -> mc.setScreen(new MiniSettingsScreen(this))));
		addRenderableWidget(new GlassButton(rx, y + step, rw, 20, "\u21E5  Host World", () -> mc.setScreen(new SelectWorldScreen(this))));
		addRenderableWidget(new GlassButton(rx, y + step * 2, rw, 20, "\u2709  Social", () -> {
			ServerData hd = new ServerData("Hypixel", "mc.hypixel.net", ServerData.Type.OTHER);
			ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(hd.ip), hd, false, null);
		}));
		addRenderableWidget(new GlassButton(rx, y + step * 3, rw, 20, "\u2666  Wardrobe", () -> mc.setScreen(new MiniSettingsScreen(this))));
		addRenderableWidget(new GlassButton(this.width - 24, 6, 18, 18, "\u2715", () -> mc.stop()));
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xFF0F1924, 0xFF16222E);
		long t = System.currentTimeMillis();
		for (int i = 0; i < PETALS; i++) {
			float yy = (py[i] + (t % 10000000L) / 1000f * ps[i] * 0.25f) % 1f;
			float xx = (px[i] + (t % 10000000L) / 1000f * ps[i] * 0.18f + (float) Math.sin(t / 1600.0 + i) * 0.006f) % 1f;
			int x = (int) (xx * this.width), yv = (int) (yy * this.height);
			int a = (i % 3 == 0) ? 0xC0 : 0x50;
			int s = (i % 4 == 0) ? 2 : 1;
			g.fill(x - s - 1, yv - s - 1, x + s + 2, yv + s + 2, (a / 6) << 24 | 0xFFFFFF);
			g.fill(x - s, yv - s, x + s + 1, yv + s + 1, (a / 3) << 24 | 0xFFFFFF);
			g.fill(x, yv, x + 1, yv + 1, a << 24 | 0xFFFFFF);
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
		int y = this.height / 2 - 6, rw = 170, rx = this.width - rw - 14;
		try {
			net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, this.minecraft.getSkinManager().getInsecureSkin(this.minecraft.getGameProfile()), rx + 6, y + 4, 12);
		} catch (Throwable ignored) {
		}
		g.drawString(this.font, "\u2304", rx + rw - 14, y + 4, 0xFFEBEBEB, false);

		int cx = colX(), top = y - 96;
		star(g, cx, top + 18, 18, 0xFFFFFFFF);
		star(g, cx, top + 18, 7, 0xFF16222E);
		float sc = 2.2f;
		String word = "MINI CLIENT";
		int gap = 1;
		float total = 0;
		for (int i = 0; i < word.length(); i++) total += (this.font.width(String.valueOf(word.charAt(i))) + gap + 1) * sc;
		total -= gap * sc;
		g.pose().pushMatrix();
		g.pose().translate(cx - total / 2f, (float) (top + 50));
		g.pose().scale(sc, sc);
		int px0 = 0;
		for (int i = 0; i < word.length(); i++) {
			String ch = String.valueOf(word.charAt(i));
			g.drawString(this.font, ch, px0, 0, 0xFFFFFFFF, false);
			g.drawString(this.font, ch, px0 + 1, 0, 0xFFFFFFFF, false);  // faux bold
			px0 += this.font.width(ch) + gap + 1;
		}
		g.pose().popMatrix();

		String v = "Mini Client 1.21.7";
		g.drawString(this.font, v, 8, this.height - 14, 0xFF6F7C8A, false);
	}
}
