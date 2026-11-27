package com.minicraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;

/** Host World: pick settings, pick a world, and it opens to your network as soon as it loads. */
public class HostWorldScreen extends MiniPage {
	public static GameType pendingMode = null;
	public static boolean pendingCheats = false;

	private static final GameType[] MODES = { GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE };
	private static final String[] NAMES = { "Survival", "Creative", "Adventure" };
	private int mode = 0;
	private boolean cheats = false;

	public HostWorldScreen(Screen parent) {
		super(parent, "Host World");
	}

	@Override
	protected void build() {
		int y = top + 40;
		button(left, y, pw, 24, "Friends play in: " + NAMES[mode], () -> {
			mode = (mode + 1) % MODES.length;
			rebuildWidgets();
		});
		button(left, y + 30, pw, 24, "Allow cheats: " + (cheats ? "On" : "Off"), () -> {
			cheats = !cheats;
			rebuildWidgets();
		});
		button(left, y + 74, pw, 30, "Choose a world to host  →", () -> {
			pendingMode = MODES[mode];
			pendingCheats = cheats;
			this.minecraft.setScreen(new SelectWorldScreen(parent));
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawString(this.font, "Open one of your worlds so friends can join you.", left, top, 0xFF9A9A9A, false);
		g.drawString(this.font, "Friends on the same Wi-Fi see it in Multiplayer automatically.", left, top + 12, 0xFF9A9A9A, false);
		int y = top + 150;
		g.drawString(this.font, "Playing over the internet (not on your Wi-Fi) is coming in a later update.", left, y, 0xFF6A6A6A, false);
	}

	/** Called every client tick: once the chosen world has loaded, open it to the network. */
	public static void tick(Minecraft mc) {
		if (pendingMode == null) return;
		IntegratedServer s = mc.getSingleplayerServer();
		if (s == null || mc.level == null || mc.player == null) return;
		GameType mode = pendingMode;
		pendingMode = null;
		if (s.isPublished()) return;
		int port = HttpUtil.getAvailablePort();
		boolean ok = s.publishServer(mode, pendingCheats, port);
		mc.gui.getChat().addMessage(Component.literal(ok
			? "§f§lMini Client §r§7» §fYour world is hosted! Friends on your Wi-Fi can join from Multiplayer. §7(port " + port + ")"
			: "§f§lMini Client §r§7» §cCould not host this world."));
	}
}
