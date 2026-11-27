package com.minicraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;

public class HudRenderer {
	private static int frames = 0;
	private static int fps = 0;
	private static long lastFpsTime = System.currentTimeMillis();
	private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private static final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
	private static boolean wasLeft = false;
	private static boolean wasRight = false;
	private static final int PAD = 3;

	public static void poll(Minecraft mc) {
		long now = System.currentTimeMillis();
		frames++;
		if (now - lastFpsTime >= 1000) {
			fps = frames;
			frames = 0;
			lastFpsTime = now;
		}
		long window = mc.getWindow().getWindow();
		boolean l = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean r = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		if (l && !wasLeft) leftClicks.add(now);
		if (r && !wasRight) rightClicks.add(now);
		wasLeft = l;
		wasRight = r;
		while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000) leftClicks.pollFirst();
		while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();
	}

	public static String text(Minecraft mc) {
		Config c = Config.INSTANCE;
		StringBuilder sb = new StringBuilder();
		if (c.showFps) append(sb, "FPS " + fps);
		if (c.showCps) append(sb, "CPS " + leftClicks.size() + " | " + rightClicks.size());
		if (c.showPing) append(sb, "Ping " + ping(mc) + "ms");
		if (c.showCoords && mc.player != null) {
			append(sb, "XYZ " + (int) Math.floor(mc.player.getX()) + " " + (int) Math.floor(mc.player.getY()) + " " + (int) Math.floor(mc.player.getZ()));
		}
		if (c.showTime) append(sb, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		if (sb.length() == 0) sb.append("Mini Client");
		return sb.toString();
	}

	private static void append(StringBuilder sb, String s) {
		if (sb.length() > 0) sb.append("   ");
		sb.append(s);
	}

	private static int ping(Minecraft mc) {
		try {
			if (mc.getConnection() != null && mc.player != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) return info.getLatency();
			}
		} catch (Exception ignored) {
		}
		return 0;
	}

	public static int width(Minecraft mc) {
		return Math.round((mc.font.width(text(mc)) + PAD * 2) * Config.INSTANCE.scale / 100f);
	}

	public static int height(Minecraft mc) {
		return Math.round((mc.font.lineHeight + PAD * 2) * Config.INSTANCE.scale / 100f);
	}

	public static void draw(GuiGraphics g, Minecraft mc) {
		Config c = Config.INSTANCE;
		String t = text(mc);
		float s = c.scale / 100f;
		int w = mc.font.width(t) + PAD * 2;
		int h = mc.font.lineHeight + PAD * 2;
		g.pose().pushMatrix();
		g.pose().translate((float) c.x, (float) c.y);
		g.pose().scale(s, s);
		if (c.bg == 1) g.fill(0, 0, w, h, 0x90000000);
		g.drawString(mc.font, t, PAD, PAD, 0xFFFFFFFF, true);
		g.pose().popMatrix();
	}
}
