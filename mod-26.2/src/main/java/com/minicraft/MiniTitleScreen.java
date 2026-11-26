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

/** Mini Client title screen, OneClient layout. */
public class MiniTitleScreen extends Screen {
	private static final int PETALS = 46;
	private final float[] px = new float[PETALS];
	private final float[] py = new float[PETALS];
	private final float[] ps = new float[PETALS];
	private final int[] pc = new int[PETALS];

	public MiniTitleScreen() {
		super(Component.literal("Mini Client"));
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

	// Layout copied from the reference screenshot (907 x 563 px), scaled to fit the window.
	private float k;
	private int ox, oy;
	private int X(float px) { return ox + Math.round(px * k); }
	private int Y(float py) { return oy + Math.round(py * k); }
	private int S(float p) { return Math.round(p * k); }
	private int rightX() { return this.width - S(113 + 281); }

	private GlassButton btn(float px, float py, float pw, String label, Runnable r) {
		GlassButton b = new GlassButton(X(px), Y(py), S(pw), S(28), label, r);
		b.textScale = Math.max(0.6f, k * 1.32f);
		return addRenderableWidget(b);
	}

	@Override
	protected void init() {
		Minecraft mc = this.minecraft;
		HostWorldScreen.pendingMode = null;   // backed out of Host World without picking a world
		k = Math.min(this.width / 907f, this.height / 563f);
		ox = 0;
		oy = (this.height - S(563)) / 2;
		btn(113, 280, 281, "\u263A  Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this)));
		btn(113, 318, 281, "\u263B  Multiplayer", () -> mc.setScreen(new JoinMultiplayerScreen(this)));
		btn(113, 356, 281, "\u25CE  Realms", () -> mc.setScreen(new RealmsMainScreen(this)));
		btn(113, 394, 135, "\u2699  Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
		btn(258, 394, 136, "\u2630  Mods", () -> mc.setScreen(new ModBrowserScreen(this)));

		int rx = rightX(), rw = S(281);
		try {
			int sw = S(100), sh = S(124);
			net.minecraft.client.gui.components.PlayerSkinWidget skin = new net.minecraft.client.gui.components.PlayerSkinWidget(
				sw, sh, mc.getEntityModels(), () -> mc.getSkinManager().getInsecureSkin(mc.getGameProfile()));
			skin.setPosition(rx + rw / 2 - sw / 2, Y(134));
			addRenderableWidget(skin);
		} catch (Throwable ignored) {
		}
		GlassButton[] right = {
			new GlassButton(rx, Y(280), rw, S(28), mc.getUser().getName(), () -> mc.setScreen(new MiniSettingsScreen(this))),
			new GlassButton(rx, Y(318), rw, S(28), "\u21E5  Host World", () -> mc.setScreen(new HostWorldScreen(this))),
			new GlassButton(rx, Y(356), rw, S(28), "\u2709  Social", () -> mc.setScreen(new SocialScreen(this))),
			new GlassButton(rx, Y(394), rw, S(28), "\u2666  Wardrobe", () -> mc.setScreen(new WardrobeScreen(this)))
		};
		for (GlassButton b : right) { b.textScale = Math.max(0.6f, k * 1.32f); addRenderableWidget(b); }

	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xFF000000, 0xFF101012);
		long t = System.currentTimeMillis();
		for (int i = 0; i < PETALS; i++) {
			float yy = (py[i] + (t % 10000000L) / 1000f * ps[i] * 0.25f) % 1f;
			float xx = (px[i] + (t % 10000000L) / 1000f * ps[i] * 0.18f + (float) Math.sin(t / 1600.0 + i) * 0.006f) % 1f;
			int x = (int) (xx * this.width), yv = (int) (yy * this.height);
			float tw = 0.75f + 0.25f * (float) Math.sin(t / 900.0 + i * 1.7);      // gentle twinkle
			int a = (int) (((i % 3 == 0) ? 230 : 120) * tw);
			float size = S(i % 4 == 0 ? 14 : 8);
			Glow.draw(g, xx * this.width, yy * this.height, size, a);
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
		int rx = rightX(), rw = S(281);
		try {
			int f = S(16);
			net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, this.minecraft.getSkinManager().getInsecureSkin(this.minecraft.getGameProfile()), rx + S(7), Y(280) + (S(28) - f) / 2, f);
		} catch (Throwable ignored) {
		}
		g.pose().pushMatrix();
		g.pose().translate(rx + rw - S(16), Y(287));
		g.pose().scale(k * 1.3f, k * 1.3f);
		g.drawString(this.font, "v", 0, 0, 0xFFE0E4E8, false);
		g.pose().popMatrix();

		int cx = X(253);
		Logo.draw(g, cx, Y(174), S(54), 0xFFFFFFFF);

		// clock + date, top left
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		String time = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		String date = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.ENGLISH));
		float ts = S(30) / 7f;
		g.pose().pushMatrix();
		g.pose().translate(X(34), Y(30));
		g.pose().scale(ts, ts);
		g.drawString(this.font, time, 0, 0, 0xFFFFFFFF, false);
		g.drawString(this.font, time, 1, 0, 0xFFFFFFFF, false);
		g.pose().popMatrix();
		float ds = Math.max(0.6f, k * 1.25f);
		g.pose().pushMatrix();
		g.pose().translate(X(36), Y(30) + S(36));
		g.pose().scale(ds, ds);
		g.drawString(this.font, date, 0, 0, 0xFFA0A0A0, false);
		g.pose().popMatrix();

		String word = "MINI CLIENT";
		float sc = S(20) / 7f;
		float total = 0;
		for (int i = 0; i < word.length(); i++) total += this.font.width(String.valueOf(word.charAt(i))) + 2;
		g.pose().pushMatrix();
		g.pose().translate(cx - total * sc / 2f, Y(222));
		g.pose().scale(sc, sc);
		int p0 = 0;
		for (int i = 0; i < word.length(); i++) {
			String ch = String.valueOf(word.charAt(i));
			g.drawString(this.font, ch, p0, 0, 0xFFFFFFFF, false);
			g.drawString(this.font, ch, p0 + 1, 0, 0xFFFFFFFF, false);
			p0 += this.font.width(ch) + 2;
		}
		g.pose().popMatrix();
		g.drawString(this.font, "Mini Client 1.21.7", 6, this.height - 12, 0xFF6A6A6A, false);
	}
}
