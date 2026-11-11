package com.minicraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Wardrobe: change your Minecraft skin and Minecraft cape through your Microsoft account. */
public class WardrobeScreen extends MiniPage {
	private static final String API = "https://api.minecraftservices.com/minecraft/profile";
	private static boolean slim = false;
	private static String status = "";
	private static int statusColor = 0xFF9A9A9A;
	private static final List<String[]> capes = new ArrayList<>();   // { id, alias }
	private static int capeIdx = -1;                                   // -1 = none
	private static boolean loaded = false;
	private EditBox name;
	private boolean busy = false;

	public WardrobeScreen(Screen parent) {
		super(parent, "Wardrobe");
	}

	private int colX() { return left + pw - 210; }

	@Override
	protected void build() {
		Minecraft mc = this.minecraft;
		try {
			int sw = Math.min(150, pw - 230), sh = Math.round(sw * 1.25f);
			PlayerSkinWidget skin = new PlayerSkinWidget(sw, sh, mc.getEntityModels(), () -> mc.getSkinManager().getInsecureSkin(mc.getGameProfile()));
			skin.setPosition(left + (pw - 220) / 2 - sw / 2, top + 10);
			addRenderableWidget(skin);
		} catch (Throwable ignored) {
		}
		int x = colX(), y = top + 4, w = 210;
		button(x, y + 12, w, 22, "Model: " + (slim ? "Slim arms" : "Classic arms"), () -> { slim = !slim; rebuildWidgets(); });
		button(x, y + 40, w, 22, "Upload skin file (.png)", this::upload);
		name = new EditBox(this.font, x, y + 82, w - 66, 20, Component.literal("Player name"));
		name.setMaxLength(16);
		name.setHint(Component.literal("Player name"));
		addRenderableWidget(name);
		button(x + w - 60, y + 82, 60, 20, "Copy", this::copyFrom);
		String cape = capeIdx < 0 ? "None" : capes.get(capeIdx)[1];
		button(x, y + 124, w, 22, "Cape: " + (loaded ? cape : "loading..."), this::nextCape);
		button(x, y + 152, w, 22, "Reset to default skin", this::reset);
		if (!loaded) loadProfile();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int x = colX(), y = top + 4;
		g.drawString(this.font, "SKIN", x, y, 0xFF8A8A8A, false);
		g.drawString(this.font, "COPY A PLAYER'S SKIN", x, y + 70, 0xFF8A8A8A, false);
		g.drawString(this.font, "MINECRAFT CAPE", x, y + 112, 0xFF8A8A8A, false);
		int sy = y + 184;
		for (net.minecraft.util.FormattedCharSequence line : this.font.split(Component.literal(status), 210)) {
			g.drawString(this.font, line, x, sy, statusColor, false);
			sy += 10;
		}
		g.drawCenteredString(this.font, "Drag to turn", left + (pw - 220) / 2, top + 10 + Math.round(Math.min(150, pw - 230) * 1.25f) + 6, 0xFF6A6A6A);
	}

	private void say(String s, boolean bad) {
		status = s;
		statusColor = bad ? 0xFFFF8080 : 0xFFE0E0E0;
	}

	private void run(String working, Task t) {
		if (busy) return;
		busy = true;
		say(working, false);
		new Thread(() -> {
			String msg;
			boolean bad = false;
			try { msg = t.go(); } catch (Exception e) { msg = "Failed: " + e.getMessage(); bad = true; }
			final String m = msg;
			final boolean b = bad;
			this.minecraft.execute(() -> { busy = false; say(m, b); rebuildWidgets(); });
		}, "MiniClient-Wardrobe").start();
	}

	private interface Task { String go() throws Exception; }

	private HttpRequest.Builder auth(String url) {
		return MiniHttp.req(url).header("Authorization", "Bearer " + this.minecraft.getUser().getAccessToken());
	}

	private static void check(HttpResponse<String> r) {
		if (r.statusCode() == 401) throw new RuntimeException("sign in again in the launcher");
		if (r.statusCode() / 100 != 2) throw new RuntimeException("Minecraft said " + r.statusCode());
	}

	private static final String DONE = "Saved! Other players see it when you rejoin a server. Restart Minecraft to see it here.";

	private void loadProfile() {
		new Thread(() -> {
			try {
				HttpResponse<String> r = MiniHttp.send(auth(API).GET().build());
				check(r);
				JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
				List<String[]> list = new ArrayList<>();
				int active = -1;
				if (o.has("capes")) for (JsonElement e : o.getAsJsonArray("capes")) {
					JsonObject c = e.getAsJsonObject();
					list.add(new String[] { c.get("id").getAsString(), c.has("alias") ? c.get("alias").getAsString() : "Cape" });
					if (c.has("state") && "ACTIVE".equals(c.get("state").getAsString())) active = list.size() - 1;
				}
				if (o.has("skins")) for (JsonElement e : o.getAsJsonArray("skins")) {
					JsonObject sk = e.getAsJsonObject();
					if ("ACTIVE".equals(sk.get("state").getAsString()) && sk.has("variant")) slim = "SLIM".equalsIgnoreCase(sk.get("variant").getAsString());
				}
				final int a = active;
				this.minecraft.execute(() -> { capes.clear(); capes.addAll(list); capeIdx = a; loaded = true; rebuildWidgets(); });
			} catch (Exception e) {
				this.minecraft.execute(() -> { loaded = true; say("Could not load your profile: " + e.getMessage(), true); rebuildWidgets(); });
			}
		}, "MiniClient-Profile").start();
	}

	private void upload() {
		if (busy) return;
		new Thread(() -> {
			String file;
			try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer f = st.mallocPointer(1);
				f.put(st.UTF8("*.png")).flip();
				file = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose a Minecraft skin", null, f, "Skin image (*.png)", false);
			}
			if (file == null) return;
			final String path = file;
			this.minecraft.execute(() -> run("Uploading skin...", () -> {
				byte[] png = Files.readAllBytes(Path.of(path));
				String b = "----MiniClient" + System.nanoTime();
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				body.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n").getBytes(StandardCharsets.UTF_8));
				body.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
				body.write(png);
				body.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
				check(MiniHttp.send(auth(API + "/skins").header("Content-Type", "multipart/form-data; boundary=" + b)
					.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build()));
				return DONE;
			}));
		}, "MiniClient-FilePicker").start();
	}

	private void copyFrom() {
		String n = name.getValue().trim();
		if (n.isEmpty()) { say("Type a player name first.", true); return; }
		run("Copying " + n + "'s skin...", () -> {
			JsonObject id;
			try { id = MiniHttp.json("https://api.mojang.com/users/profiles/minecraft/" + MiniHttp.enc(n)).getAsJsonObject(); }
			catch (Exception e) { throw new RuntimeException("no player called " + n); }
			JsonObject prof = MiniHttp.json("https://sessionserver.mojang.com/session/minecraft/profile/" + id.get("id").getAsString()).getAsJsonObject();
			JsonArray props = prof.getAsJsonArray("properties");
			String val = props.get(0).getAsJsonObject().get("value").getAsString();
			JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("textures");
			if (tex == null || !tex.has("SKIN")) throw new RuntimeException(n + " uses a default skin");
			JsonObject skin = tex.getAsJsonObject("SKIN");
			boolean s = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
			slim = s;
			String body = "{\"variant\":\"" + (s ? "slim" : "classic") + "\",\"url\":\"" + skin.get("url").getAsString() + "\"}";
			check(MiniHttp.send(auth(API + "/skins").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()));
			return DONE;
		});
	}

	private void nextCape() {
		if (!loaded || busy) return;
		if (capes.isEmpty()) { say("Your account has no Minecraft capes. Your Mini Client cape is in Settings in the launcher.", false); return; }
		int next = capeIdx + 1 >= capes.size() ? -1 : capeIdx + 1;
		run("Changing cape...", () -> {
			if (next < 0) check(MiniHttp.send(auth(API + "/capes/active").DELETE().build()));
			else check(MiniHttp.send(auth(API + "/capes/active").header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString("{\"capeId\":\"" + capes.get(next)[0] + "\"}")).build()));
			capeIdx = next;
			return next < 0 ? "Cape hidden." : "Cape changed to " + capes.get(next)[1] + ".";
		});
	}

	private void reset() {
		run("Resetting skin...", () -> {
			check(MiniHttp.send(auth(API + "/skins/active").DELETE().build()));
			return "Skin reset to default. Restart Minecraft to see it here.";
		});
	}
}
